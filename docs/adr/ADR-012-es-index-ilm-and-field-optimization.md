# ADR-012：ES 索引生命周期管理与字段存储优化

## 状态

已接受

## 背景

订单中台查询端（`order-query-service`）基于 Elasticsearch 8.x 构建，采用宽表扁平化文档设计，按月分索引（`orders-{yyyy-MM}`）、6 shards、1 副本、强制 routing。当前索引策略整体合理，但经过上线后的实际运行数据观测，发现以下可优化点。

### 现状指标（基于当前订单量日 100 万）

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 日增量 | ~2 GB | 单文档 ~2KB，日订单 100 万 |
| 月索引大小 | ~60 GB（含 1 副本 = 120GB） | 当前 6 shards，单 shard ~20GB |
| 磁盘占用（12 个月） | ~1.4 TB | 含副本，不含压缩 |
| 月均存储成本增长率 | ~120 GB/月 | 持续线性增长，无自动淘汰机制 |
| 当前索引数 | 6 个（近 6 个月） | 更早的索引未自动清理 |

### 存在的问题

**问题 1：缺少索引生命周期自动化（ILM）**

当前按月手动创建索引，缺少自动化管理策略：

- 索引创建靠定时脚本（XXL-Job），创建逻辑硬编码在代码中
- 索引从 hot → warm → cold → delete 的整个生命周期缺少自动流转
- 历史索引需要人工判断何时清理，存在磁盘撑爆风险
- `refresh_interval`、`number_of_replicas` 等参数在索引变冷后未自动调整

**问题 2：Shard 数量与增长节奏不匹配**

当前 6 shards 的设计基于月索引 ~60GB 的预估，但在实际运行中：

- 大促月份索引可达 120-180GB，单 shard 30GB+，超出推荐上限（Elastic 官方建议单 shard 20-40GB）
- 平峰月份索引 ~30GB，单 shard 仅 5GB，shard 过于碎片化
- 固定 6 shards 无法弹性适配吞吐量变化

**问题 3：字段存储有冗余，可通过 mapping 优化降低存储 20-30%**

当前 mapping 对所有字段启用了默认的 `index: true` 和 `doc_values: true`，但实际查询场景中：

- `buyer_remark` / `seller_remark`：大文本字段，从不用于搜索过滤，`index: true` 浪费倒排索引空间
- `tags`：仅用于过滤，不用于聚合排序，`doc_values: true` 浪费列存空间
- `total_amount` 等金额字段：仅用于聚合（sum/avg），`index: false` 可节省倒排索引
- 部分字段在 ES 中仅做展示用途，不需要索引和聚合

**问题 4：缺少冷热数据分层**

当前所有索引采用相同的副本数和刷新策略，导致：

- 历史月份（> 90 天）的查询频率极低，但占用与 hot 节点相同的资源
- 没有利用 ES 的 cold/frozen 节点特性降低存储成本
- 超过 12 个月的索引没有自动删除机制

**问题 5：缺少 forcemerge 优化**

- 历史索引写入停止后，未执行 forcemerge 合并 segments
- 每个 segment 占用独立的文件句柄和内存
- 大量小 segment 会降低查询性能

### 当前成本分析

| 存储类型 | 当前配置 | 月成本（估算） | 优化后预估 |
|----------|---------|---------------|-----------|
| Hot 阶段（SSD） | 6 索引 × 6 shards × 1 副本 | ~100 GB SSD | ~70 GB（index/doc_values 优化） |
| Warm 阶段（HDD） | 无 | 0 | ~400 GB HDD（forcemerge + 降副本） |
| Cold 阶段（Frozen） | 无 | 0 | ~600 GB Frozen（只读压缩） |
| Delete | 无 | 0 | 12 月前的索引自动删除 |
| **总计** | | **持续增长** | **可控 + 节约 30-40%** |

---

## 决策

采用 **Elasticsearch 原生 ILM（Index Lifecycle Management）** 实现索引全生命周期自动化管理。

### 理由

| 方案 | 评价 |
|------|------|
| **方案 A：ES 原生 ILM（选定）** | 零额外运维、ES 内置能力、与 rollover 天然集成、Policy 可热更新 |
| 方案 B：XXL-Job 脚本管理 | 需要维护脚本逻辑、缺少 rollover 集成、错误处理需自行实现 |
| 方案 C：自研管理平台 | 投入产出比低，ILM 已覆盖 90% 需求 |

### ILM vs 脚本管理 详细对比

| 维度 | ES ILM | XXL-Job 管理脚本 |
|------|--------|-----------------|
| **索引创建** | rollover 自动创建新索引（基于 `max_size`/`max_age`） | 需要脚本计算时间并调用 `PUT` API |
| **阶段流转** | 内置 hot → warm → cold → delete，配置即用 | 需自行实现状态判断和数据迁移 |
| **参数调整** | 各阶段自动调整 `replicas`/`refresh_interval` | 需频繁调用 API |
| **forcemerge** | warm 阶段原生支持 `forcemerge` action | 需单独实现脚本 + 限速 |
| **freeze** | cold 阶段原生支持 `freeze` action | 需手动调用 `POST /_freeze` |
| **错误重试** | 内置重试机制 + `GET _ilm/explain` 排查 | 需自行实现 |
| **运维成本** | 定义一次 Policy，后续自动执行 | 脚本需要持续维护和监控 |

---

## 详细设计

### 1. ILM 策略定义

#### 1.1 整体策略

```json
PUT _ilm/policy/orders-ilm-policy
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "30d",
            "max_docs": 50000000
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "90d",
        "actions": {
          "shrink": {
            "number_of_shards": 3,
            "number_of_replicas": 0
          },
          "forcemerge": {
            "max_num_segments": 1
          },
          "allocate": {
            "number_of_replicas": 0,
            "require": {
              "data_type": "warm"
            }
          },
          "readonly": {},
          "set_priority": {
            "priority": 50
          }
        }
      },
      "cold": {
        "min_age": "180d",
        "actions": {
          "freeze": {},
          "searchable_snapshot": {
            "snapshot_repository": "order-backup-repo",
            "force_merge_index": true
          },
          "set_priority": {
            "priority": 0
          }
        }
      },
      "delete": {
        "min_age": "365d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

> **注意**：上述 ILM 策略的 delete 阶段（365d）主要适用于电商/本地生活业务线。B2B 业务线因合同约定的数据保留期限通常为 7 年，需要独立配置 ILM 策略，详见 ADR-031《数据生命周期管理》。

#### 1.2 各阶段说明

| 阶段 | 触发条件 | 核心动作 | 存储介质 | 副本数 | 查询性能 |
|------|---------|---------|---------|--------|---------|
| **Hot** | 当前写入索引 | rollover（50GB / 30d / 5000 万文档任一触发） | SSD（高 IOPS） | 1 | 毫秒级 |
| **Warm** | 索引创建 ≥ 90 天 | shrink → forcemerge → 降副本 → 只读 | HDD / 普通云盘 | 0（无需高可用） | 十毫秒级 |
| **Cold** | 索引创建 ≥ 180 天 | freeze 或 searchable_snapshot | 对象存储（更低成本） | - | 秒级（解冻后） |
| **Delete** | 索引创建 ≥ 365 天 | 物理删除 | - | - | - |

#### 1.3 Rollover 触发条件

采用「任一条件满足即触发」策略：

```
rollover 触发条件（三者任一）:
  ┌──────────────────────────────────────────────────────────────┐
  │  max_size: 50GB   ← 索引大小达到 50GB 触发                    │
  │                    ← 防止大促月份单索引过大，shard 超出推荐上限  │
  │                                                              │
  │  max_age: 30d     ← 索引创建满 30 天触发                      │
  │                    ← 保持按月索引的语义，便于运维理解           │
  │                                                              │
  │  max_docs: 5000万  ← 文档数达到 5000 万触发                   │
  │                    ← 防止单索引文档过多，影响查询性能           │
  └──────────────────────────────────────────────────────────────┘
```

**预期效果**：
- 正常月份：~30 天触发 rollover，与原先按月索引行为一致
- 大促月份：50GB 先触发，约 25-30 天，索引大小稳定在可控范围
- 超高峰值：文档数 5000 万先触发，约 2500 万订单（含子单），约 15-20 天

#### 1.4 Warm 阶段的 Shrink 策略

> **为什么需要 shrink？**
>
> Hot 阶段 6 shards 是为高写入吞吐设计的。进入 Warm 后，索引只有查询需求（且查询频率极低），6 shards 浪费资源。
> shrink 到 3 shards 可使集群 shard 总数减少 50%，降低管理开销。

```json
// Warm 阶段 shrink 配置
{
  "shrink": {
    "number_of_shards": 3,
    "number_of_replicas": 0
  }
}
```

**Shrink 约束检查**：
- 缩容后的 shard 数必须是原 shard 数的因数（6 → 3 ✅，6 → 2 ✅）
- 缩容前索引必须标记为 `readonly`（ILM warm 阶段会自动处理）
- 确保集群有足够磁盘空间完成 shrink（需要一份完整拷贝）

**Shrink 后的 shard 大小预估**：

| 阶段 | Shard 数 | 总数据量 | 单 shard | 说明 |
|------|---------|---------|---------|------|
| Hot | 6 | 60 GB | ~10 GB | 写入吞吐优先 |
| Warm | 3 | 60 GB | ~20 GB | 查询为主，减少 shard 数 |
| Cold | 3 | 60 GB | ~20 GB | frozen 只读，不变 |

#### 1.5 Cold 阶段的选择：Freeze vs Searchable Snapshot

```yaml
两种 Cold 方案对比:

Freeze（当前推荐）:
  - 优点: 无需额外基础设施，ES 原生支持
  - 优点: 解冻后可正常查询，适合偶尔需要查历史数据的场景
  - 缺点: 仍然占用磁盘空间
  - 适用: 订单查询服务偶尔有历史订单查询需求（客服/财务）

Searchable Snapshot:
  - 优点: 数据从对象存储读取，几乎不占磁盘
  - 优点: 存储成本极低（S3/OSS 约 0.01 元/GB/月）
  - 缺点: 查询延迟高（首次查询需从对象存储加载）
  - 缺点: 需要 ES Enterprise 许可证 + 配置 snapshot repository
  - 适用: 法规审计类查询（极少访问，但必须保留）
```

**建议**：当前阶段使用 **Freeze**，待 ES 基础设施完善后（配置 snapshot repository）再切换到 Searchable Snapshot。

> **注意**：Freeze 模式在 ES 8.x 中已被标记为 Deprecated（计划在 9.x 移除），未来需要迁移到 Searchable Snapshot。建议在 Phase 2 完成 Searchable Snapshot 的配置和切换。

---

### 2. 索引模板配置

#### 2.1 组件模板（通用配置）

```json
PUT _component_template/orders-settings
{
  "template": {
    "settings": {
      "number_of_shards": 6,
      "number_of_replicas": 1,
      "refresh_interval": "30s",
      "routing": {
        "required": true
      },
      "index.max_result_window": 100000,
      "index.translog.durability": "async",
      "index.translog.sync_interval": "5s",
      "analysis": {
        "analyzer": {
          "ik_smart_analyzer": {
            "type": "custom",
            "tokenizer": "ik_smart"
          }
        }
      }
    }
  }
}
```

```json
PUT _component_template/orders-mappings
{
  "template": {
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "order_id":        { "type": "long" },
        "order_no":        { "type": "keyword" },
        "parent_order_id": { "type": "long" },

        "buyer_id":        { "type": "long" },
        "buyer_name":      { "type": "keyword", "index": false, "copy_to": "search_text" },
        "buyer_phone":     { "type": "keyword", "index": false },

        "seller_id":       { "type": "long" },
        "seller_name":     { "type": "keyword", "index": false, "copy_to": "search_text" },

        "business_type":   { "type": "keyword" },
        "channel":         { "type": "keyword" },
        "source":          { "type": "keyword" },

        "order_status":    { "type": "keyword" },
        "refund_status":   { "type": "keyword" },
        "delivery_status": { "type": "keyword" },

        "total_amount":    { "type": "long", "index": false },
        "discount_amount": { "type": "long", "index": false },
        "freight_amount":  { "type": "long", "index": false },
        "pay_amount":      { "type": "long", "index": false },
        "paid_amount":     { "type": "long", "index": false },

        "gmt_create":      { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
        "gmt_modified":    { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
        "pay_time":        { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
        "ship_time":       { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
        "sign_time":       { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
        "completed_time":  { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },

        "item_list": {
          "type": "nested",
          "properties": {
            "sku_id":       { "type": "long" },
            "product_id":   { "type": "long" },
            "product_name": { "type": "text", "analyzer": "ik_smart", "copy_to": "search_text" },
            "quantity":     { "type": "integer", "index": false },
            "unit_price":   { "type": "long", "index": false },
            "subtotal":     { "type": "long", "index": false }
          }
        },

        "receiver_name":    { "type": "keyword", "index": false, "copy_to": "search_text" },
        "receiver_phone":   { "type": "keyword" },
        "receiver_address": { "type": "text", "analyzer": "ik_smart", "index": false },
        "province":         { "type": "keyword" },
        "city":             { "type": "keyword" },
        "district":         { "type": "keyword" },

        "logistics_no":      { "type": "keyword", "index": false },
        "logistics_company": { "type": "keyword", "index": false },

        "pay_type":       { "type": "keyword", "index": false },
        "pay_channel":    { "type": "keyword" },
        "third_pay_no":   { "type": "keyword" },

        "search_text": {
          "type": "text",
          "analyzer": "ik_smart",
          "fields": {
            "keyword": { "type": "keyword", "index": false }
          }
        },

        "tags":           { "type": "keyword", "doc_values": false },
        "seller_remark":  { "type": "text", "analyzer": "standard", "index": false },
        "buyer_remark":   { "type": "text", "analyzer": "standard", "index": false }
      }
    }
  }
}
```

#### 2.2 索引模板（组合）

```json
PUT _index_template/orders-template
{
  "index_patterns": ["orders-*"],
  "composed_of": ["orders-settings", "orders-mappings"],
  "priority": 100,
  "template": {
    "settings": {
      "index.lifecycle.name": "orders-ilm-policy",
      "index.lifecycle.rollover_alias": "orders-write"
    }
  }
}
```

#### 2.3 Mapping 优化收益盘点

| 字段 | 原始配置 | 优化后 | 节省空间 | 理由 |
|------|---------|--------|---------|------|
| `buyer_name` | keyword + index + doc_values | keyword + index:false | 中等 | 仅展示，不从 ES 搜索买家名 |
| `seller_name` | keyword + index + doc_values | keyword + index:false | 中等 | 同上 |
| `buyer_phone` | keyword + index + doc_values | keyword + index:false | 小 | 客服查单走 `receiver_phone` 足矣 |
| `total_amount` | long + index + doc_values | long + index:false | 小 | 金额按 range 查询极少，主要通过 `pay_amount` 聚合 |
| `discount_amount` | long + index + doc_values | long + index:false | 小 | 同上 |
| `freight_amount` | long + index + doc_values | long + index:false | 小 | 同上 |
| `pay_amount` | long + index + doc_values | long + index:false | 小 | 用 `range` 查金额极少，`terms` 聚合不需要 |
| `paid_amount` | long + index + doc_values | long + index:false | 小 | 同上 |
| `item_list.quantity` | integer + index + doc_values | integer + index:false | 中 | nested 子字段，不单独过滤 |
| `item_list.unit_price` | long + index + doc_values | long + index:false | 中 | 同上 |
| `item_list.subtotal` | long + index + doc_values | long + index:false | 中 | 同上 |
| `receiver_name` | keyword + index + doc_values | keyword + index:false | 小 | 客服通过 `search_text` 搜，不需要独立索引 |
| `receiver_address` | text + index + doc_values | text + index:false | 小 | 不用于搜索过滤 |
| `logistics_no` | keyword + index + doc_values | keyword + index:false | 小 | 极少通过物流单号查订单 |
| `logistics_company` | keyword + index + doc_values | keyword + index:false | 小 | 仅展示 |
| `pay_type` | keyword + index + doc_values | keyword + index:false | 小 | 仅展示 |
| `tags` | keyword + index + doc_values | keyword + doc_values:false | 小 | 仅用于过滤，不聚合 |
| `seller_remark` | text + index + doc_values | text + index:false | 中 | 大文本，不搜索 |
| `buyer_remark` | text + index + doc_values | text + index:false | 中 | 大文本，不搜索 |
| `search_text.keyword` | keyword | keyword + index:false | 小 | 只搜 text 字段即可 |

> **预估收益**：索引大小降低 25-35%。以一月份索引 60GB 为例，优化后约 40-45GB。

#### 2.4 别名设计

```json
// 写入别名：始终指向当前 hot 索引
// 用于 Canal/数据同步端写入
orders-write    → orders-2026-06  (rollover 后自动更新)

// 查询别名：指向所有可查询的索引（hot + warm + cold）
// 用于应用层查询
orders-search   → orders-*        (所有非 delete 阶段的索引)

// 别名特点：
// - 应用层只通过别名访问，不直接操作具体索引
// - rollover 后 orders-write 自动指向新索引（ILM 托管）
// - orders-search 全局别名，搜索时覆盖所有相关索引
```

---

### 3. 写入与查询适配

#### 3.1 数据写入适配

启用 ILM 后，数据写入端需要使用 **rollover alias** 而非具体索引名。

**Canal 同步适配**：

```yaml
# Canal 同步配置（application.yml）
es:
  # 写入时使用别名，ILM 保证别名始终指向当前可写索引
  write-alias: orders-write
```

**批量写入代码适配（ES Java Client）**：

```java
// Before：直接写入具体索引（旧方式）
BulkRequest request = new BulkRequest();
request.add(new IndexRequest("orders-2026-06").id("123").source(orderDoc, XContentType.JSON));
bulkProcessor.add(request);

// After：写入 ILM 托管别名（新方式）
BulkRequest request = new BulkRequest();
request.add(new IndexRequest("orders-write").id("123").source(orderDoc, XContentType.JSON));
// 注意：使用 routing 参数时必须同时指定，确保一致
// request.add(new IndexRequest("orders-write").id("123").routing("buyer_456").source(...));
bulkProcessor.add(request);
```

**关键约束**：

```java
// 使用 ILM rollover 时，必须满足以下约束：
// 1. 每个写入请求必须指定 routing（因为模板中 routing.required = true）
// 2. 索引名必须匹配模板模式 orders-*
// 3. 写入别名只能指向一个索引（rollover 会自动维护指向关系）

// 批量写入示例（Canal 同步端）
BulkProcessor bulkProcessor = BulkProcessor.builder(
    (request, bulkListener) -> esClient.bulkAsync(request, bulkListener),
    () -> BulkProcessor.builder(esClient, new BulkProcessor.Listener() {
        @Override
        public void beforeBulk(long executionId, BulkRequest request) {
            // 可在此处做埋点监控
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            if (response.hasFailures()) {
                log.warn("Bulk写入部分失败: {}", response.buildFailureMessage());
                // 失败文档写入死信队列，后续重试
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
            log.error("Bulk写入完全失败", failure);
            // 记录待重试列表，后续补偿
        }
    })
)
    .setBulkActions(5000)            // 每 5000 条 flush 一次
    .setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB)) // 或 10MB
    .setFlushInterval(TimeValue.timeValueSeconds(5))      // 或 5s
    .setConcurrentRequests(4)        // 并行 4 线程
    .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueSeconds(1), 3))
    .build();
```

#### 3.2 查询端适配

查询端无需改动——已经使用 `orders-search` 别名，该别名覆盖所有索引。

```java
// 查询端代码无需修改，继续保持别名访问
SearchRequest searchRequest = new SearchRequest("orders-search");
// ... 现有查询逻辑不变 ...
```

#### 3.3 滚动创建首个 ILM 索引

```bash
# Step 1: 创建 ILM Policy（已完成）
PUT _ilm/policy/orders-ilm-policy
# ...策略体...

# Step 2: 创建首个索引，指定 alias 并设置 is_write_index=true
PUT orders-000001
{
  "aliases": {
    "orders-write": {
      "is_write_index": true
    }
  }
}

# Step 3: 验证 ILM 状态
GET orders-000001/_ilm/explain

# Step 4: 确认写入别名可用
# Canal 同步端将写入目标从 "orders-2026-06" 改为 "orders-write"
```

---

### 4. Shard 数量动态优化

#### 4.1 Shard 密度模型

当前 6 shards 固定，改为基于索引大小的弹性策略：

```yaml
Shard 数量计算公式:
  target_shards = ceil(index_size_gb / target_shards_per_gb)

  目标: 每个 shard 20-40GB（ES 官方推荐 10-50GB）
  
  阈值:
    < 30GB   → 3 shards（平峰月）
    30-80GB  → 6 shards（正常月）
    > 80GB   → 9-12 shards（大促月）

  由于索引大小在创建时不可预知，采用 post-rollover 手动调整策略:
  - 首次创建: 6 shards（保守选择）
  - rollover 后: 根据上一索引实际大小，调整下一索引的 shard 数
```

#### 4.2 Shard 数量调整机制

```java
// XXL-Job 定时检测：每月 1 日检测上一索引大小，动态调整下一索引 shard 数
@XxlJob("adjustOrderShardCount")
public void adjustShardCount() {
    // 1. 获取上一已完成的索引大小
    GetIndexResponse lastIndex = esClient.indices().get(
        req -> req.index("orders-2026-05")  // 示例：上一月份索引
    );

    long storeSizeBytes = lastIndex.get("orders-2026-05").getStoreSize().getBytes();
    double storeSizeGB = storeSizeBytes / (1024.0 * 1024.0 * 1024.0);

    // 2. 根据大小计算 shard 数
    int shardCount;
    if (storeSizeGB < 30) {
        shardCount = 3;
    } else if (storeSizeGB <= 80) {
        shardCount = 6;
    } else {
        shardCount = Math.min(12, (int) Math.ceil(storeSizeGB / 10));
    }

    // 3. 更新组件模板
    esClient.cluster().putComponentTemplate(
        req -> req.name("orders-settings")
            .template(t -> t.settings(s -> s.index(i -> i.numberOfShards(String.valueOf(shardCount)))))
    );

    log.info("根据上索引大小 {}GB，调整 shard 数为: {}", String.format("%.1f", storeSizeGB), shardCount);
}
```

#### 4.3 Shard 数调整最佳实践

```yaml
调整注意事项:
  - Shard 数调整只在索引模板层面生效，已创建的索引不受影响
  - rollover 创建新索引时使用模板中的最新 shard 数
  - 不要频繁调整（每月调整一次足够）
  - 避免 shard 过多导致集群管理开销增大
  
  推荐最大值:
  - 单节点 shard 数 < 1000（含副本）
  - 单索引 shard 数 < 20
  - 单 shard 大小 10-40GB
  
  当前预期:
  - 平峰月（~30GB）: 3 shards × 1 副本 = 6 shards
  - 正常月（~60GB）: 6 shards × 1 副本 = 12 shards
  - 大促月（~120GB）: 9 shards × 1 副本 = 18 shards
```

---

### 5. ES 节点角色与资源配置

#### 5.1 节点角色规划

```yaml
节点角色分配（基于 ILM 分阶段特性）:

Hot 节点（data_hot）:
  用途: 当前写入 + 最近 90 天查询
  规格: 8C 32G, SSD, 500GB × 3 节点
  角色: data_hot, ingest（可选）
  配置:
    node.attr.data_type: hot
    index.search.throttle: -1  # hot 节点不限速

Warm 节点（data_warm）:
  用途: 90-180 天历史数据，低频查询
  规格: 4C 16G, HDD/普通云盘, 2TB × 3 节点
  角色: data_warm
  配置:
    node.attr.data_type: warm

Cold 节点（data_cold）:
  用途: 180-365 天 frozen 数据，极少查询
  规格: 2C 8G, 普通云盘, 3TB × 2 节点
  角色: data_cold
  配置:
    node.attr.data_type: cold

Master 节点:
  用途: 集群管理
  规格: 2C 8G × 3 节点（专用 master，不参与数据）
  角色: master

Coordinating 节点（可选，小规模可不部署）:
  用途: 请求聚合
  规格: 4C 8G × 2 节点
  角色: coordinating
```

#### 5.2 Shard 分配过滤

```json
// Hot 阶段：强制分配到 data_hot 节点
PUT _template/settings-hot
{
  "index_patterns": ["orders-*"],
  "order": 1,
  "settings": {
    "index.routing.allocation.require.data_type": "hot"
  }
}

// 索引进入 warm 阶段后，ILM 自动将 routing 改为 warm 节点
// ILM action:
{
  "allocate": {
    "number_of_replicas": 0,
    "require": {
      "data_type": "warm"
    }
  }
}
```

---

### 6. 监控与告警

#### 6.1 ILM 状态监控

```bash
# 查看 ILM 执行状态
GET orders-*/_ilm/explain

# 输出示例：

# orders-000001 (hot) → phase: hot, action: rollover, 正常
# orders-000002 (hot) → phase: hot, action: complete, 正常
# orders-2026-03 (warm) → phase: warm, action: forcemerge, 进度 67%
# orders-2026-01 (cold) → phase: cold, action: freeze, 已完成
# orders-2025-12 (delete) → phase: delete, action: delete, 已完成
# orders-2025-11 (delete) → phase: delete, action: delete, pending
```

```bash
# ILM 错误排查
GET _ilm/explain/orders-000001

# 常见错误：
# - "action" : "ROLLOVER", "step" : "ERROR" → 需要检查 rollover 条件
# - "failed_step" : "shrink" → 清理磁盘空间或检查 shrink 约束
# - "phase" : "hot", "action" : "complete" → 一切正常

# 恢复卡住的 ILM 步骤
POST _ilm/retry
```

#### 6.2 Prometheus 监控指标

```yaml
# Prometheus 告警规则（通过 elasticsearch_exporter 采集）

groups:
  - name: es_ilm_alerts
    rules:
      # ILM 错误
      - alert: ESILMStepError
        expr: elasticsearch_ilm_errors_total > 0
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "ES ILM 步骤执行失败: {{ $labels.index }}"

      # Hot 索引磁盘即将满
      - alert: ESHotDiskUsageHigh
        expr: (elasticsearch_filesystem_data_size_bytes - elasticsearch_filesystem_data_free_bytes) / elasticsearch_filesystem_data_size_bytes > 0.85
        for: 10m
        labels:
          severity: P2
        annotations:
          summary: "Hot 节点磁盘使用率 > 85%，可能需要 rollover"

      # Warm 节点磁盘满
      - alert: ESWarmDiskUsageHigh
        expr: (elasticsearch_filesystem_data_size_bytes{node_role="warm"} - elasticsearch_filesystem_data_free_bytes{node_role="warm"}) / elasticsearch_filesystem_data_size_bytes{node_role="warm"} > 0.90
        for: 10m
        labels:
          severity: P2
        annotations:
          summary: "Warm 节点磁盘使用率 > 90%"

      # Forcemerge 卡住（长时间处于 running 状态）
      - alert: ESForcemergeStuck
        expr: elasticsearch_forcemerge_current > 0 and on(instance) (elasticsearch_forcemerge_time_seconds > 3600)
        labels:
          severity: P2
        annotations:
          summary: "Forcemerge 执行超过 1 小时: {{ $labels.index }}"

      # Shard 分布不均
      - alert: ESShardImbalance
        expr: max(elasticsearch_cluster_shards_per_node) - min(elasticsearch_cluster_shards_per_node) > 10
        for: 30m
        labels:
          severity: P3
        annotations:
          summary: "Shard 在各节点分布不均，差异 > 10"
```

#### 6.3 关键 Grafana 看板

```yaml
ILM 阶段看板（新增，补充现有 ES 看板）:

面板 1: 索引分布概览
  - 各阶段（Hot/Warm/Cold/Delete）索引数量
  - Hot 阶段索引大小 + 增长率
  - 各索引的 ILM 状态进度

面板 2: 磁盘使用率趋势
  - Hot 节点磁盘使用率（近 30 天）
  - Warm 节点磁盘使用率（近 30 天）
  - Cold 节点磁盘使用率（近 30 天）
  - 按节点展示，预警扩容时机

面板 3: Rollover 统计
  - rollover 触发次数（近 90 天）
  - rollover 触发原因分布（max_size / max_age / max_docs）
  - 各索引从创建到 rollover 的实际天数
  - 用于调优 rollover 阈值参数

面板 4: 存储成本节省
  - 当前总存储 vs 无 ILM 估算存储
  - forcemerge 节省的空间比例
  - shrink 节省的 shard 数
  - freeze 节省的磁盘空间
```

---

### 7. 数据一致性保障

ILM 管理过程中不能影响数据一致性。以下措施确保迁移过程安全。

#### 7.1 ILM 阶段转换的幂等

```yaml
ILM 各个操作均为幂等操作:
  - rollover: 创建新索引，别名指向切换，旧索引继续保留
  - shrink: 创建新索引（缩减 shard），原索引保持不变直到 shrink 完成
  - forcemerge: 合并 segments，多次执行不会产生副作用
  - freeze: 标记索引为只读，解冻后可再次 freeze
  - delete: 删除前通过 min_age 确保不会误删活跃索引

关键保障:
  - Shrink 期间原索引仍可查询，shrink 完成后自动切换到新索引
  - Forcemerge 期间索引仍然可查询（性能略有下降）
  - 任何阶段执行失败 → ILM 自动重试（可通过 _ilm/retry 手动触发）
```

#### 7.2 写入可用性保障

```yaml
rollover 期间的写入:
  - rollover 创建新索引后，orders-write 别名自动指向新索引
  - 别名切换是原子操作，写入请求不会丢失（ES 会重试失败的请求）
  - 如果 rollover 失败，写入继续指向旧索引，不阻塞写入

节点故障期间的 ILM:
  - ILM 在节点故障时会自动暂停
  - 节点恢复后，ILM 从中断处继续执行
  - 如果某阶段长时间卡住，人工介入执行 _ilm/retry
```

---

### 8. 回滚与应急预案

#### 8.1 ILM 回滚步骤

如果在 ILM 配置变更后出现问题，可以回滚到无 ILM 状态：

```yaml
回滚方案 A: 删除 ILM Policy（索引不受影响）
  1. 移除索引模板中的 lifecycle 配置：
     PUT _index_template/orders-template
     { ... 移除 "index.lifecycle.name" ... }
  2. 删除 Policy（已应用的索引不受影响）：
     DELETE _ilm/policy/orders-ilm-policy
  3. 各索引保持当前阶段，不再继续流转
  4. 恢复手动管理方式（XXL-Job 管理索引创建 + 清理）

回滚方案 B: 修改 Policy 为更保守的值
  1. 修改 Policy，延长各阶段 min_age：
     PUT _ilm/policy/orders-ilm-policy
     { "hot": { "min_age": "0ms" },
       "warm": { "min_age": "180d" },  // 原 90d → 改为 180d
       "cold": { "min_age": "365d" },  // 原 180d → 改为 365d
       "delete": { "min_age": "730d" }  // 原 365d → 改为 730d
     }
  2. 已进入 warm/cold 的索引不受影响
  3. 未进入的索引将按新时间进入

关键原则: ILM Policy 是软配置，修改/删除不会导致数据丢失。
最坏情况：ILM 卡住 → 手动 retry → 仍然不行 → 手动迁移索引
```

#### 8.2 Forcemerge 期间的性能影响

```yaml
Forcemerge 是高 IO 操作，在 warm 阶段执行时需要注意:
  - forcemerge 限速: 通过 max_num_segments: 1 一批合并
  - 影响范围: 只影响正在 forcemerge 的索引，不影响 hot 索引的写入
  - 时间窗口: 60GB 索引 forcemerge 约需 15-30 分钟
  - 高峰期避开: ILM warm 触发时间固定为创建后 90 天，不可控
  
  建议:
  如果 forcemerge 影响集群性能，在 ILM warm phase 添加延迟:
  {
    "warm": {
      "min_age": "90d",
      "actions": {
        "forcemerge": { "max_num_segments": 1 },
        "allocate": { "require": { "data_type": "warm" }}
      }
    }
  }
  
  配合慢查询监控观察 forcemerge 期间的集群响应变化。
  如果影响大，可改为凌晨执行（但 ES ILM 不支持指定时间窗口）。
  替代方案: 移除 ILM 中的 forcemerge action，改用 XXL-Job 在凌晨运行。
```

#### 8.3 ILM 卡住后的手动恢复

```bash
# Step 1: 检查 ILM 状态
GET orders-000001/_ilm/explain
# 如果返回 "step" : "ERROR"，查看 "failed_step" 和 "reason"

# Step 2: 解决根本问题
# 常见问题及解决：
# 磁盘满 → 清理磁盘或扩容
# shrink 卡住 → 检查 shrink 约束（shard 因数、readonly）
# forcemerge 中断 → 索引状态异常，手动 forcemerge

# Step 3: 手动 retry
POST orders-000001/_ilm/retry

# Step 4: 如果 retry 无效，手动执行剩余操作
# 例：手动 shrink（从 6 shards shrink 到 3 shards）
POST orders-000001/_shrink/orders-000001-shrink
{
  "settings": {
    "index.number_of_shards": 3,
    "index.number_of_replicas": 0,
    "index.routing.allocation.require.data_type": "warm"
  }
}

# Step 5: 手动删除原索引，替换为缩容后的索引
POST /_aliases
{
  "actions": [
    { "remove_index": { "index": "orders-000001" }},
    { "add": { "index": "orders-000001-shrink", "alias": "orders-search" }}
  ]
}
```

---

### 9. 实施步骤

#### Phase 1：Mapping 优化（1-2 天，与 ILM 无关）

```
任务 1: 创建优化后的 mapping 模板（索引 settings + mappings）
  操作: PUT _component_template/orders-mappings（含 index:false 优化）
        PUT _component_template/orders-settings
        PUT _index_template/orders-template
  注意: 只影响新建索引，不影响已有索引

任务 2: 新建索引使用新模板
  操作: 通过 rollover 或手动创建新索引，验证字段优化生效
  验证: GET orders-new/_mapping → 确认 index:false 字段不产生倒排索引
        _cat/indices/orders-new → 对比大小 vs 旧索引

任务 3: 评估优化效果（新索引运行 1 周后）
  操作: 对比新旧索引的存储大小
  预期: 新索引存储降低 25-35%
```

#### Phase 2：ILM Policy 创建与验证（2 天）

```
任务 4: 创建 ILM Policy
  操作: PUT _ilm/policy/orders-ilm-policy
  注意: 初始可将 min_age 大幅延长（如 warm: 365d），防止误触发

任务 5: 创建首个 ILM 托管索引
  操作: 创建 orders-000001，指定 orders-write 别名
        验证写入别名可用

任务 6: 验证阶段流转
  操作: 手动触发索引进入 warm（缩短 min_age=1d 测试）
        观察 forcemerge + shrink 是否正常执行
  注意: 测试完成后恢复原 min_age 值
  验证: warm 阶段执行后 → 索引只读 + 副本归零 + segments 合并

任务 7: 全量迁移写入别名
  操作: Canal 同步端将写入目标从 "orders-{yyyy-MM}" 改为 "orders-write"
  灰度: 先在 PRE 环境验证 → 上线 PROD

任务 8: 配置 Forcemerge + Shrink + Freeze 阶段
  操作: 恢复 ILM Policy 的生产配置
        warm: 90d, cold: 180d, delete: 365d
  验证: 等待索引自然进入各阶段，监控 ILM 执行状态
```

#### Phase 3：监控与持续优化（持续）

```
任务 9: 配置 ILM 监控告警
  操作: 导入 6.3 节中的 Prometheus 告警规则
        添加 Grafana dashboard

任务 10: 定期评估阈值
  操作: 每月检查 rollover 阈值是否合理
        根据实际索引增长率调整 max_size / max_age
  评估指标: 单索引大小、shard 密度、磁盘使用率趋势

任务 11: 切换到 Searchable Snapshot（可选，ES Enterprise 许可证）
  操作: 配置 snapshot repository（OSS/S3）
        修改 ILM cold 阶段为 searchable_snapshot
        验证数据可恢复性
        迁移已有 cold 索引
```

---

### 10. 上线检查清单

```markdown
## ES ILM + 字段优化 上线 Checklist

### 前置检查
- [ ] ES 版本 ≥ 8.0（ILM 是白金版特性，需要白金版 License）
- [ ] 集群有 Hot/Warm/Cold 节点角色分配
- [ ] ILM Policy 已在 PRE 环境验证通过
- [ ] 优化后的 Mapping 模板已在 PRE 环境应用验证
- [ ] 写入别名 orders-write 只指向一个索引
- [ ] 查询别名 orders-search 覆盖所有索引

### 写入端适配
- [ ] Canal 同步配置已从具体索引名改为 orders-write 别名
- [ ] bulk 写入代码已确认使用 routing 参数
- [ ] 批量写入错误重试机制正常
- [ ] 写入端灰度验证通过（先切 10% 流量）

### 查询端适配
- [ ] 查询端继续使用 orders-search 别名（无需改动）
- [ ] 验证旧索引仍能被 orders-search 覆盖
- [ ] 验证 `index:false` 字段不能被搜索（确认无查询使用这些字段做 filter）

### 监控检查
- [ ] ILM 状态看板已配置（Grafana）
- [ ] Prometheus 告警规则已导入
- [ ] ILM 错误告警已配置通知
- [ ] 磁盘使用率告警已配置

### 回滚准备
- [ ] ILM Policy 修改前的配置已备份
- [ ] 旧 Mapping 模板已备份
- [ ] 确认删除 ILM Policy 不会影响已有索引
```

### 11. 实施预期收益

| 维度 | 优化前 | 优化后 | 收益 |
|------|--------|--------|------|
| **存储成本** | 月增 ~120GB（含副本） | 月增 ~80GB（mapping 优化）→ ~40GB（warm 降副本）→ ~0（cold 后删除） | **3-6 个月后存储降低 50%+** |
| **查询性能** | 所有索引同样配置，无冷热分层 | hot 节点专注近期数据，SSD 性能充分发挥 | **近期查询 P99 降低 10-15%** |
| **运维成本** | 手动创建索引、手动清理 | 全自动生命周期管理 | **运维工作量降低 90%** |
| **磁盘安全** | 历史索引不清理，存在撑爆风险 | 自动清理 > 365 天索引 | **消除磁盘满风险** |
| **集群管理** | 所有节点同角色，无分层 | Hot/Warm/Cold 分层，资源利用率高 | **集群可扩展性提升** |

---

## 与架构文档的关联

本 ADR 对应 `docs/optimization-opportunities.md` 中的 **P1 #3 ES 索引 ILM + 字段优化**。

涉及架构指标：
- **存储成本降低 20-30%** → 实际可达 50%+（含 warm 降副本 + cold freeze）
- **查询性能提升 10-15%** → hot 节点专注于近期数据
- **运维自动化** → 索引管理从手动变为全自动

相关实现文件：
- `deploy/scripts/es/setup-ilm.sh` — ILM Policy + 索引模板部署脚本（实施时创建）
- `deploy/scripts/es/adjust-shard-count.sh` — Shard 数量动态调整脚本（实施时创建）
- `order-query-service` — 查询端代码（无需改动，别名机制保证兼容）
- `canal-sync` — 数据同步服务（写入别名适配，少量改动）

依赖 ADR：
- **ADR-013** — Canal 高可用确保 ES 写入端稳定性，ILM 不依赖 Canal 但需要 Canal 写入别名适配
