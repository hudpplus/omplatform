# ADR-014：CQRS 热数据缓存加速

## 状态

已接受

---

## 背景

### 现状分析

当前订单查询全部走 ES。`order-query-service` 收到买家「我的订单」列表请求后，直接向 ES 发出搜索请求，使用 `orders-search` 别名，通过 `buyer_id` routing 定位到对应 shard，排序后返回订单列表。

```
当前查询流程（改造前）：

买家端 → Gateway → order-query-service
                        │
                        ▼
                    Elasticsearch
                        │
                  1. 解析查询条件
                  2. 扫描 inverted index
                  3. 取回 score + _source
                  4. 排序 + 分页
                  5. 返回结果
                        │
                        ▼
                    响应买家
```

每次列表查询均走完整 ES 查询链路，包括索引扫描、评分计算、source 取回等环节。对于高频的「我的订单」场景（买家查看最近活跃订单），存在显著的优化空间。

### 存在的问题

**问题 1：ES 查询粒度偏重**  
ES 每次查询需要经过「查询解析 → 索引扫描 → 相关性算分 → 取回 _source → 排序分页」完整链路。即使买家只需「最近 5 条订单的状态和金额」，ES 也需要执行全流程。相比 Redis O(1) 读，ES 查询在 CPU 和 IO 上的开销高 1-2 个数量级。

**问题 2：冷热数据同路径**  
已完结（COMPLETED）超过 24h 的订单查询频次极低，但与高频活跃订单共享同一 ES 索引和查询路径。冷数据占用 ES 内存缓存资源，稀释了热数据的缓存效率。

**问题 3：ES 抖动直接传导到前端**  
ES 集群在 merge/forcemerge/checkpoint 等操作时可能出现毫秒级抖动（P99 从 50ms 跳到 200-500ms），直接导致接口延迟劣化。Redis 热缓存层可以缓冲这种抖动。

**问题 4：ES 资源利用率不经济**  
ES 集群的 CPU 和内存资源按峰值容量规划。如果「我的订单」查询中 80% 可由缓存覆盖，等量 ES 资源可服务于更复杂的搜索场景（客服搜索、运营分析、财务对账）。

**问题 5：查询放大效应**  
买家进入「我的订单」页面时，通常先看到列表（最近 N 条），但前端往往同时发出多条查询（分页信息、未读数、角标）。这些查询目前全部落在 ES 上。

### 当前性能指标

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 「我的订单」P99 | ~50ms | ES 查询 + 网络开销 |
| 「我的订单」P50 | ~15ms | 正常负载下 |
| 日查询量 | ~100 万次 | 日活跃买家 20 万，人均 5 次 |
| ES 集群 CPU | 30-40% | 日常负载 |
| 活跃订单占比 | ~80% | 最近 24h 有操作的订单占比 |

---

## 决策

在 ES 前增加 **Redis 热数据缓存层**，仅缓存最近 24h 活跃买家订单的列表摘要。

### 理由

| 维度 | 评估 |
|------|------|
| **效果** | 订单列表查询 P99 从 50ms → < 5ms（降低 90%） |
| **命中率** | 80% 的「我的订单」查询发生在最近 24h 内，热缓存可覆盖 |
| **成本** | 低 — Redis 集群已存在，仅需约 1.2GB 额外内存 |
| **一致性** | 最终一致，最大不一致窗口 30s，仅影响列表展示不涉及资金 |
| **基础设施** | Canal cache-writer Consumer Group 已存在（ADR-013），无需新增组件 |
| **风险** | 低 — Redis 降级后自动回退 ES，不影响正确性 |

---

## 详细设计

### 1. 整体架构

```
改造后查询流程：

买家端 → Gateway → order-query-service
                        │
                    ┌───┴───┐
                    │  拦截器  │  ← @HotCacheable 注解
                    └───┬───┘
                        │
              ┌─────────┴──────────┐
              │  hit?              │
              ┌──┴──┐          ┌──┴──┐
              │ YES │          │ NO  │
              └──┬──┘          └──┬──┘
                 │                │
           ┌─────▼─────┐   ┌─────▼──────┐
           │  Redis     │   │  ES        │
           │  hot cache │   │  (fallback) │
           │  < 2ms     │   │  ~50ms     │
           └─────┬─────┘   └─────┬──────┘
                 │                │
                 │           ┌────▼─────┐
                 │           │ 异步回填  │
                 │           │  Redis    │
                 │           └──────────┘
                 │                │
                 └────────┬───────┘
                          │
                    ┌─────▼──────┐
                    │  响应买家   │
                    └────────────┘


改造后写入刷新路径：

OceanBase binlog
      │
  Canal Server  ←  ADR-013 Canal HA Cluster
      │
  RocketMQ canal-order-binlog Topic (64 partitions)
      │
  ┌────┴────────────────────────────────────┐
  │  canal-es-writer (ORDERLY, 4 instances) │ → ES
  │  canal-cache-writer (CONCURRENTLY, 2)   │ → Redis  ← 本 ADR 增强
  │  canal-biz-event (optional)             │ → 下游业务
  └─────────────────────────────────────────┘
      │
      ▼
  canal-cache-writer Consumer
      │
  ┌────┴────────────────────────────────┐
  │  1. 解析 binlog → 提取 buyer_id    │
  │  2. 构建 OrderSummary              │
  │  3. 判断订单状态 → ADD/UPDATE/DELETE│
  │  4. 批量写入 Redis                 │
  │  5. 续期 TTL = 30s                │
  └─────────────────────────────────────┘
```

### 2. 缓存数据结构

#### 2.1 方案对比

| 方案 | 数据结构 | 优势 | 劣势 |
|------|---------|------|------|
| **A: String + JSON** | `Key → JSON Array` | 实现简单，一次读取全量列表；支持 TTL 原子过期；序列化方案成熟（Jackson/Fastjson） | 更新单个订单需整体重写 |
| B: SortedSet | `Key → ZSet`，member=orderId, score=createTime | 天然按时间排序；支持增量增删 | 读取需 ZRANGE 全量取出；TTL 需额外维护；列表为空时 key 残留 |
| C: Hash | `Key → Hash<orderId, OrderSummary>` | 支持增量更新单个订单 | 无法整体排序；TTL 需额外维护；读取需 HGETALL + 应用层排序 |
| D: List | `Key → List<OrderSummary>` | 天然有序 | 更新困难；需 LREM + LPUSH；TTL 需额外维护 |

**选型：方案 A（String + JSON）**

理由：
- 「我的订单」列表通常在 20 条以内（前端分页大小），每次读取全量传输量极小（约 3KB）
- 写入频率远低于读取频率（订单变更 vs 列表查看），整体重写成本可接受
- 排序由 Canal 侧保证（按 gmt_create 降序排列）
- TTL 由 Redis 原生 expire 管理，不需要额外维护
- 空列表场景：缓存 `[]`（空 JSON 数组），TTL 10s，防止穿透

#### 2.2 Key 设计

```
hot:order:list:{buyer_id % 1000}:{buyer_id}

示例：
hot:order:list:123:1008612
          │      │     └── 买家 ID
          │      └──────── buyer_id 哈希桶（1000 个桶，均匀分布）
          └─────────────── 业务前缀
```

前缀 `hot:order:list` 统一管理，便于 Redis 命令行批量扫描和监控。

#### 2.3 Value 设计

```json
[
  {
    "oi": 1008612,              // orderId（缩写，降低序列化体积）
    "st": "PAID",               // orderStatus（缩写）
    "am": 299900,               // payAmount（分，long）
    "ct": "2026-06-11 14:30:00", // gmtCreate（字符串，15 bytes）
    "bt": "ecommerce",          // businessType
    "sn": "ORD202606111234567", // orderNo（客服搜索用，可选字段）
    "ic": 0                     // itemCount（商品数量，展示用）
  },
  ...
]
```

- 字段名使用缩写（2-3 字符），单个摘要约 120-150 bytes
- 金额以「分」为单位（long），避免浮点数
- 时间使用字符串（可读性好，序列化后仅 19 bytes）
- 完整列表 20 条 ≈ 3KB，Redis 单次读取 < 2ms

#### 2.4 OrderSummary 属性选择策略

只缓存列表展示必需的字段，不冗余 ES 宽表的所有字段：

| 字段 | 大小 | 必须? | 说明 |
|------|------|-------|------|
| orderId | 8 bytes | 是 | 主键 |
| orderStatus | 4-15 bytes | 是 | 列表展示核心 |
| payAmount | 8 bytes | 是 | 金额展示 |
| gmtCreate | 19 bytes | 是 | 排序 + 时间展示 |
| businessType | 10 bytes | 是 | 业务线图标/标签 |
| orderNo | 20 bytes | 否 | 客服场景，可选 |
| itemCount | 4 bytes | 否 | 「共 N 件」 |
| sellerName | - | 否 | 买家端不展示卖家（C2C 场景可加） |
| logisticsStatus | - | 否 | 详情页展示，列表页不展示 |

如果字段命中率 < 50% 或仅详情页使用，不加入热缓存摘要。

### 3. 缓存写入/刷新机制

#### 3.1 主路径：Canal cache-writer 消费 binlog

基于 ADR-013 已规划的 `canal-cache-writer` Consumer Group（CONCURRENTLY 模式，2 个实例），增强其功能以支持热数据缓存。

```
Canal binlog 解析 → RocketMQ canal-order-binlog
                           │
                    ┌──────┴──────┐
                    │ cache-writer │ ← 本 ADR 增强
                    │ Consumer     │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │ 解析 binlog event        │
              │ 提取: buyer_id, status,  │
              │       pay_amount, time   │
              └────────────┬────────────┘
                           │
              ┌────────────┴────────────┐
              │ 构建 OrderSummary        │
              │ OrderSummary.builder()  │
              │   .orderId(orderId)      │
              │   .status(newStatus)     │
              │   .payAmount(payAmount)  │
              │   .createTime(gmtCreate) │
              │   .build()              │
              └────────────┬────────────┘
                           │
              ┌────────────┴────────────┐
              │ 判断操作类型              │
              │                         │
              │ INSERT → 追加到列表       │
              │ UPDATE → 替换列表中的条目  │
              │ DELETE → 从列表移除       │
              │                         │
              │ 终态 + 超过24h → 从列表移除│
              └────────────┬────────────┘
                           │
              ┌────────────┴────────────┐
              │ 写入 Redis               │
              │ SET key json_value       │
              │ EXPIRE key 30           │
              └─────────────────────────┘
```

**操作类型判断逻辑（伪代码）：**

```java
public class HotCacheWriter {

    public void onMessage(CanalBinlogEvent event) {
        String tableName = event.getTable();
        if (!"order".equals(tableName)) return;

        Long buyerId = extractBuyerId(event);
        String orderStatus = extractOrderStatus(event);
        String beforeStatus = extractBeforeStatus(event);

        // 判断是否需要缓存此订单
        if (isTerminalStatus(orderStatus) && isOlderThan24h(event.getGmtCreate())) {
            // 终态且超过 24h → 从热缓存移除
            removeFromHotCache(buyerId, event.getOrderId());
            return;
        }

        // 构建摘要
        OrderSummary summary = OrderSummary.builder()
            .orderId(event.getOrderId())
            .status(orderStatus)
            .payAmount(extractPayAmount(event))
            .createTime(extractCreateTime(event))
            .businessType(extractBusinessType(event))
            .itemCount(extractItemCount(event))
            .build();

        // 更新热缓存
        upsertHotCache(buyerId, summary);
    }

    private boolean isTerminalStatus(String status) {
        return Sets.newHashSet("COMPLETED", "CANCELLED", "CLOSED", "REFUNDED")
            .contains(status);
    }

    private boolean isOlderThan24h(Date orderTime) {
        return System.currentTimeMillis() - orderTime.getTime() > 24 * 3600 * 1000L;
    }
}
```

#### 3.2 辅助路径：领域事件刷新

少数场景下单 binlog 无法覆盖（如运营后台直接修改、数据订正任务），通过领域事件辅助刷新：

```
order-core 或运营后台
      │
      ├── 修改订单数据
      ├── 发送 OrderStatusChangedEvent → RocketMQ order_event Topic
      │
      ▼
cache-writer (enhanced)
      │
      └── 消费领域事件 → 更新 Redis（与 binlog 路径相同的 upsert 逻辑）
```

领域事件刷新为辅助路径，不可靠（可能丢失），仅作为尽力而为的补充。主一致性保障仍依赖 binlog + Canal。

#### 3.3 TTL 管理

```
TTL 策略：
  每次写入/更新 → EXPIRE key 30 （续期 30s）
  不主动删除 key（由 Redis 自动过期）

设计考量：
  - 30s TTL 意味着缓存最大不一致窗口为 30s
  - Canal 正常延迟 < 3s，实际不一致窗口通常 < 3s
  - 如果 Canal 中断超过 30s，缓存自动过期 → 查询降级到 ES
  - 无需额外的「缓存清理」后台任务
```

#### 3.4 批量写入策略

```
避免每条 binlog 变更都触发一次 Redis 写操作：

消费端攒批配置：
  ─ 攒批窗口：500ms
  ─ 最大批大小：100 条
  ─ 任一条件达成立即 flush

Pipeline 写入：
  Redis PIPELINE 或 Lua 脚本批量 SET + EXPIRE
  减少网络 RTT，100 条写入约 5-15ms
```

#### 3.5 排序保证

```
在缓存中始终保持按 gmt_create DESC 排序：

写入时排序：
  Canal cache-writer 在构建 JSON 数组时保持有序
  新订单 INSERT → 追加到数组头部
  已有订单 UPDATE → 在数组中找到并替换（位置不变）
  终态超时移除 → 从数组中删除

读取时不排序：
  读取后直接返回，不额外排序
  由写入端保证顺序正确，减少读取开销
```

### 4. 缓存读取流程

#### 4.1 查询拦截

```
order-query-service 查询方法：

@HotCacheable(key = "'hot:order:list:' + (#buyerId % 1000) + ':' + #buyerId")
public PageResult<OrderSummary> queryMyOrders(Long buyerId, OrderQueryReq req) {
    // 原始 ES 查询逻辑 — 仅当缓存未命中时执行
    return esQueryMyOrders(buyerId, req);
}
```

#### 4.2 双读逻辑

```
HotCacheTemplate 实现：

V get(String key, Class<V> type, long ttlSeconds, Callable<V> dbLoader) {
    // 1. 尝试从 Redis 读取
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        // 命中 → 反序列化返回
        return deserialize(cached, type);
    }

    // 2. 缓存未命中 → 从 ES 加载
    V result = dbLoader.call();

    // 3. 异步回填 Redis（非阻塞）
    if (result != null && !isEmpty(result)) {
        asyncCacheWriter.execute(() -> {
            redisTemplate.opsForValue().set(key, serialize(result), ttlSeconds, TimeUnit.SECONDS);
        });
    } else {
        // 空结果 → 缓存空列表防穿透（短 TTL）
        redisTemplate.opsForValue().set(key, "[]", 10, TimeUnit.SECONDS);
    }

    return result;
}
```

#### 4.3 缓存未命中场景

哪些场景会导致缓存未命中：

| 场景 | 概率 | 影响 |
|------|------|------|
| 买家首次访问（当天无操作） | 低（<5%） | 走 ES 回填 |
| 缓存过期（30s 无访问） | 中等 | 走 ES 回填 |
| Canal 延迟 > 30s | 极低（异常场景） | 走 ES 回填 |
| Redis 宕机 | 极低 | 降级走 ES |
| 买家查询已完成订单 | 低 | 走 ES（热缓存不包含） |

所有未命中场景均自动回退 ES，用户无感知，仅延迟略升。

#### 4.4 空列表缓存

```
当买家确实没有活跃订单时：
  ─ ES 查询返回空列表
  ─ 缓存 "[]"（空 JSON 数组），TTL = 10s
  ─ 10s 内同一买家重复查询 → 直接返回空列表（< 2ms）
  ─ 防止每次空查询都穿透到 ES
```

### 5. 内存预估与容量规划

#### 5.1 日常容量

```
日活跃买家（DAU）：~20 万
人均活跃订单数：~20 条（平均）

Redis Key 数：20 万（每个买家一个 key）
单个 Key 大小：≈ 3KB（20 条 × 150 bytes）
Value 总大小：20万 × 3KB ≈ 600MB

Redis 内部开销：
  Key 存储：20万 × 100 bytes ≈ 20MB
  dict entry：20万 × 64 bytes ≈ 13MB
  JSON 序列化缓冲：≈ 10MB
  其他 overhead（Redis 内部碎片）：≈ 600MB × 10% = 60MB

总估算：≈ 700MB
```

#### 5.2 大促容量

```
大促日活跃买家（DAU）：~50 万
人均活跃订单数：~30 条（大促期间人均下单更多）

Value 总大小：50万 × 150 bytes × 30 ≈ 2.25GB
总估算：≈ 2.6GB
```

#### 5.3 容量评估

```
Redis Cluster 配置：
  3 节点（每节点 8GB 内存）
  总可用内存：3 × 8GB = 24GB
  当前其他用途：≈ 12GB（订单状态、购物车、分布式锁等）

热缓存占用：
  日常：700MB / 剩余 12GB（6%）
  大促：2.6GB / 剩余 9.4GB（28%）

结论：
  容量充足，无需扩容
  大促期间可临时扩容 Redis 节点或增加分片
```

#### 5.4 内存上限保护

```
为防止热缓存占用过多 Redis 内存，设置逐出保护：

在 Canal cache-writer 侧限速：
  ─ 每秒最多写入 2000 个买家 key
  ─ 单个买家 key 最多 50 条摘要（超出截断，只保留最近 50 条）
  ─ 定期巡检：清理 > 48h 无访问的 key（兜底清理）

Redis 侧配置：
  maxmemory-policy: allkeys-lru
  （如果内存不足，Redis 自动淘汰最久未访问的热缓存 key → 降级为 ES 查询）
```

### 6. 缓存预热

#### 6.1 启动预热

```
order-query-service 启动时自动预热：

预热流程：
  1. 从 ES 查询最近 1h 有操作的所有 buyer_id
     POST orders-search/_search
     {
       "query": { "range": { "gmt_modified": { "gte": "now-1h" }}},
       "fields": ["buyer_id"],
       "collapse": { "field": "buyer_id" },
       "size": 5000
     }

  2. 对每个 buyer_id 查询其最近活跃订单
     POST orders-search/_search?routing=buyer_{id}
     {
       "query": {
         "bool": {
           "filter": [
             { "term": { "buyer_id": id }},
             { "bool": { "should": [
               { "range": { "gmt_modified": { "gte": "now-24h" }}},
               { "terms": { "order_status": ["PENDING_PAY","PAID","SHIPPED","DELIVERED"]}}
             ]}}
           ]
         }
       },
       "sort": [{ "gmt_create": "desc" }],
       "size": 50
     }

  3. 批量写入 Redis
  4. 全量预热完成后，开始接受流量

限速策略：
  每秒预热 1000 个买家
  20 万买家 ≈ 3.5 min
  预热期间不影响线上（Redis key 已存在时跳过）
```

#### 6.2 定时预热

```
除启动预热外，每小时执行增量预热：

XXL-Job @XxlJob("hotCacheWarmUp")
  1. 查询最近 1h 内「首次活跃」的 buyer_id（之前不在热缓存中的买家）
  2. 批量预热这些新买家的订单列表
  3. 跳过已在热缓存中的买家（减少 ES 查询压力）
```

### 7. 一致性说明

```
一致性模型：最终一致

数据流：
  binlog → Canal → RocketMQ → cache-writer → Redis
                                          ↘
  order-core → ES (Canal es-writer)
                                          ↗
  (Redis 和 ES 各自独立消费同一份 binlog)

不一致窗口分析：

  ┌─────────────────────────────────────────────────────┐
  │ 正常情况：                                           │
  │   binlog 产生                                        │
  │      │                                               │
  │      ├──→ Canal 捕获 (延迟 < 100ms)                  │
  │      │     ├──→ es-writer → ES (延迟 < 1s)          │
  │      │     └──→ cache-writer → Redis (延迟 < 500ms) │
  │      │                                               │
  │   结果：Redis 与 ES 几乎实时一致（延迟 < 1s）          │
  │                                                      │
  └─────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────┐
  │ 异常情况 1：Redis 写入成功慢于 ES                     │
  │   Redis TTL=30s，ES 数据已更新但 Redis 尚未刷新      │
  │   用户看到旧数据（最多 30s）                           │
  │   影响：列表展示状态比实际落后，不影响资金安全           │
  │                                                      │
  └─────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────┐
  │ 异常情况 2：Canal 中断后恢复                          │
  │   Canal 中断 > 30s → Redis key 自动过期              │
  │   恢复后 → 用户请求走 ES → 回填 Redis                │
  │   中断期间无数据不一致（因为缓存已过期，走 ES 正确数据）  │
  │                                                      │
  └─────────────────────────────────────────────────────┘

可接受的不一致：
  1. 买家状态已更新但列表展示旧状态（用户刷新后正确）
  2. 已取消的订单在列表中多展示几秒（不影响资金）
  3. 新增订单未出现在列表中短时间（30s 后自动刷新）

不可接受的场景（已从设计上避免）：
  ❌ 缓存展示已失效的支付状态 → TTL 30s + Canal 秒级刷新
  ❌ Redis 宕机导致查询错误 → 自动降级 ES
  ❌ 缓存数据永久不一致 → TTL 过期 + 每日对账兜底
```

### 8. 降级策略

#### 8.1 Redis 不可用

```
检测：Redis 连接异常 / 超时 / 命令执行失败 → 触发降级

降级行为：
  ─ HotCacheTemplate 捕获 Redis 异常
  ─ 降级日志（WARN 级别，打印 buyer_id 和耗时）
  ─ 自动回退到 ES 查询
  ─ 降级计数器 +1（监控告警用）

恢复：
  ─ Redis 恢复后，下次请求自动恢复正常路径
  ─ 不需要手动切换
```

#### 8.2 Canal 延迟过大

```
检测：canal_delay_ms > 10s（已有 P2 告警 ADR-013）

降级行为（可选，通过 Apollo 配置）：
  ─ hot.cache.degrade-on-high-delay = true
  ─ 延迟 > 10s 时，热缓存主动失效
  ─ 所有请求走 ES，直到延迟恢复正常

理由：
  高延迟场景下，缓存数据可能过期 > 10s
  主动降级到 ES 可减少不一致窗口
```

#### 8.3 应用层熔断

```java
@Component
public class HotCacheTemplate {

    private static final int DEGRADE_THRESHOLD = 50;  // 连续 50 次 Redis 异常触发熔断
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private volatile boolean degraded = false;

    public <T> T get(String key, Class<T> type, long ttlSeconds, Callable<T> dbLoader) {
        if (degraded) {
            return dbLoader.call();  // 熔断中 → 直读 ES
        }

        try {
            String cached = redisTemplate.opsForValue().get(key);
            consecutiveErrors.set(0);  // 成功 → 重置错误计数
            if (cached != null) {
                return deserialize(cached, type);
            }
            // ... 未命中逻辑
        } catch (Exception e) {
            if (consecutiveErrors.incrementAndGet() >= DEGRADE_THRESHOLD) {
                degraded = true;
                log.warn("Hot cache degraded: {} consecutive Redis errors", DEGRADE_THRESHOLD);
                // 30s 后尝试恢复
                scheduler.schedule(() -> degraded = false, 30, TimeUnit.SECONDS);
            }
            return dbLoader.call();  // 降级走 ES
        }
    }
}
```

#### 8.4 大促预判降级策略

```
场景：大促流量远超日常，Redis 内存压力上升

策略：
  ─ 大促前 1 周：扩容 Redis Cluster（增加节点或提升内存配额）
  ─ 大促前 1 天：执行全量预热（确保缓存 100% 就绪）
  ─ 大促期间：监控 hit_rate，如果 < 50%（说明缓存无效），自动降级直读 ES
  ─ 大促结束后：回滚 Redis 配置，恢复正常模式
```

### 9. 监控指标

#### 9.1 Prometheus 指标

```java
// Micrometer 指标定义
@Bean
public MeterRegistry meterRegistry() {
    // 缓存命中率
    Counter hitCounter = Counter.builder("hotcache.hit")
        .description("Hot cache hit count")
        .register(registry);
    Counter missCounter = Counter.builder("hotcache.miss")
        .description("Hot cache miss count")
        .register(registry);

    // 降级计数
    Counter degradeCounter = Counter.builder("hotcache.degrade")
        .description("Hot cache degrade count (fallback to ES)")
        .register(registry);

    // 缓存大小（近似）
    Gauge cacheSize = Gauge.builder("hotcache.size", cacheManager, cm -> cm.estimateSize())
        .description("Estimated hot cache size in bytes")
        .register(registry);

    // 单个请求耗时
    Timer timer = Timer.builder("hotcache.get.duration")
        .description("Hot cache GET latency")
        .publishPercentiles(0.5, 0.99)
        .register(registry);

    return registry;
}
```

#### 9.2 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: hotcache
    rules:
      - alert: HotCacheHitRateLow
        expr: |
          sum(rate(hotcache_hit_total[5m]))
          /
          (sum(rate(hotcache_hit_total[5m])) + sum(rate(hotcache_miss_total[5m])))
          < 0.7
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "热缓存命中率低于 70%"
          description: "最近 5min 命中率 {{ $value | humanizePercentage }}，可能 Canal 延迟或缓存过期过快"

      - alert: HotCacheDegradeRateHigh
        expr: rate(hotcache_degrade_total[5m]) > 10
        for: 1m
        labels: { severity: P2 }
        annotations:
          summary: "热缓存降级频繁"
          description: "每秒 {{ $value }} 次降级到 ES，可能 Redis 异常"

      - alert: HotCacheSizeHigh
        expr: hotcache_size_bytes > 1.5e9
        for: 5m
        labels: { severity: P3 }
        annotations:
          summary: "热缓存内存占用超过 1.5GB"
          description: "当前 {{ $value | humanize1024 }}，大促期间可调整阈值至 3GB"

      - alert: HotCacheWriteLag
        expr: rate(hotcache_write_lag_ms[5m]) > 1000
        for: 5m
        labels: { severity: P3 }
        annotations:
          summary: "热缓存写入延迟偏高"
          description: "平均写入延迟 {{ $value }}ms，检查 Redis 性能"
```

#### 9.3 Grafana 看板

```
看板：热数据缓存监控（Grafana dashboard）

Panel 1 — 命中率（时序图）
  ─ 最近 1h / 6h / 24h 命中率曲线
  ─ 维度：按 order-query-service 实例
  ─ 阈值线：70%（P2 告警）

Panel 2 — 延迟对比（时序图）
  ─ hotcache P99 vs ES P99 对比
  ─ 直观展示缓存带来的延迟降低

Panel 3 — 缓存大小（时序图）
  ─ 热缓存总大小（bytes / GB）
  ─ 各 Redis 节点占用分布
  ─ 大促对比基线

Panel 4 — 降级次数（柱状图）
  ─ 按原因分：Redis 不可用 / 延迟过高 / 熔断
  ─ 累计降级次数 / 降级占比

Panel 5 — 热点买家 Top K（表格）
  ─ 缓存最大的 Top 10 buyer_id
  ─ 帮助定位异常买家（机器刷单、爬虫等）
  ─ 访问频率最高的 Top 10 buyer_id

Panel 6 — 写入延迟（时序图）
  ─ Canal cache-writer 消费延迟
  ─ 攒批写入耗时
  ─ Redis PIPELINE 写入耗时
```

### 10. Canal Cache-Writer 代码结构

```java
package com.omplatform.query.infrastructure.cache;

/**
 * Canal cache-writer 扩展 — 热数据缓存写入
 *
 * 在 ADR-013 已有的 cache-writer Consumer 基础上增强
 * 新增 HotCacheHandler 处理热缓存刷新
 */
@Component
public class CanalCacheWriterConsumer implements MessageListener {

    @Autowired
    private HotCacheHandler hotCacheHandler;      // 热缓存写入器
    @Autowired
    private OrderStatusCacheHandler statusHandler; // 订单状态缓存（已有）

    private final BatchBuffer batchBuffer = new BatchBuffer(500, 100); // 攒批 500ms/100条

    @Override
    public void onMessage(List<MessageExt> messages) {
        for (MessageExt msg : messages) {
            CanalBinlogEvent event = CanalBinlogEvent.parseFrom(msg.getBody());
            if (!"order_db".equals(event.getDatabase()) || !"order".equals(event.getTable())) {
                continue;
            }

            // 现有逻辑：刷新订单状态缓存
            statusHandler.handle(event);

            // 新增强：刷新热数据缓存
            hotCacheHandler.handle(event);
        }

        // 强制 flush 缓冲
        batchBuffer.flush();
    }
}
```

```java
@Component
public class HotCacheHandler {

    private static final long ACTIVE_THRESHOLD_MS = 24 * 3600 * 1000L;
    private static final long CACHE_TTL_SECONDS = 30;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void handle(CanalBinlogEvent event) {
        Long buyerId = event.getAfterColumn("buyer_id", Long.class);
        String newStatus = event.getAfterColumn("order_status", String.class);
        String oldStatus = event.getBeforeColumn("order_status", String.class);
        Date gmtCreate = event.getAfterColumn("gmt_create", Date.class);

        if (buyerId == null) return;

        // 终态订单超 24h → 移除
        if (isTerminal(newStatus) && isExpired(gmtCreate)) {
            removeOrder(buyerId, event.getOrderId());
            return;
        }

        // 构建摘要
        OrderSummary summary = OrderSummary.builder()
            .orderId(event.getOrderId())
            .status(newStatus)
            .payAmount(event.getAfterColumn("pay_amount", Long.class))
            .createTime(formatTime(gmtCreate))
            .businessType(event.getAfterColumn("business_type", String.class))
            .itemCount(event.getAfterColumn("item_count", Integer.class))
            .build();

        // 更新热缓存
        upsertOrder(buyerId, summary);
    }

    /**
     * 更新买家热缓存中的订单摘要
     * 如果已存在则替换，不存在则追加到头部（按时间降序）
     */
    private void upsertOrder(Long buyerId, OrderSummary summary) {
        String key = buildKey(buyerId);

        // 读 -> 改 -> 写（Redis + Lua 优化版见 BatchBuffer）
        String json = redisTemplate.opsForValue().get(key);
        List<OrderSummary> list = deserializeList(json);

        // 替换或追加
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getOrderId().equals(summary.getOrderId())) {
                list.set(i, summary);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(0, summary);  // 新订单插入头部
        }

        // 截断（最多保留 50 条）
        if (list.size() > 50) {
            list = list.subList(0, 50);
        }

        // 写回 Redis + 续期 TTL
        redisTemplate.opsForValue().set(key, serialize(list), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 从热缓存移除指定订单
     */
    private void removeOrder(Long buyerId, Long orderId) {
        String key = buildKey(buyerId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return;

        List<OrderSummary> list = deserializeList(json);
        list.removeIf(s -> s.getOrderId().equals(orderId));

        if (list.isEmpty()) {
            redisTemplate.delete(key);
        } else {
            redisTemplate.opsForValue().set(key, serialize(list), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String buildKey(Long buyerId) {
        int bucket = (int)(buyerId % 1000);
        return "hot:order:list:" + bucket + ":" + buyerId;
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) ||
               "CANCELLED".equals(status) ||
               "CLOSED".equals(status) ||
               "REFUNDED".equals(status);
    }

    private boolean isExpired(Date gmtCreate) {
        return System.currentTimeMillis() - gmtCreate.getTime() > ACTIVE_THRESHOLD_MS;
    }
}
```

```java
/**
 * 攒批缓冲区 — 减少 Redis 网络 IO
 *
 * 将多条写入合并为一次 Redis PIPELINE 或 Lua 脚本执行
 */
@Component
public class BatchBuffer {

    private final long maxWaitMs;
    private final int maxBatchSize;
    private final List<Runnable> buffer = new ArrayList<>();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public BatchBuffer(long maxWaitMs, int maxBatchSize) {
        this.maxWaitMs = maxWaitMs;
        this.maxBatchSize = maxBatchSize;
        this.scheduler.scheduleAtFixedRate(this::flush, maxWaitMs, maxWaitMs, TimeUnit.MILLISECONDS);
    }

    public void add(Runnable task) {
        synchronized (buffer) {
            buffer.add(task);
            if (buffer.size() >= maxBatchSize) {
                flush();
            }
        }
    }

    public void flush() {
        List<Runnable> copy;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            copy = new ArrayList<>(buffer);
            buffer.clear();
        }
        // 执行批量 Redis PIPELINE
        redisTemplate.executePipelined((RedisCallback<Void>) connection -> {
            for (Runnable task : copy) {
                task.run();
            }
            return null;
        });
    }
}
```

### 11. 查询拦截代码结构

```java
package com.omplatform.query.infrastructure.cache;

/**
 * 热缓存注解 — 标记需要拦截的查询方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HotCacheable {
    String key();                            // SpEL 表达式生成 key
    long ttlSeconds() default 30;            // 缓存 TTL
    boolean cacheEmpty() default true;       // 是否缓存空结果
}
```

```java
/**
 * 热缓存切面 — 拦截 @HotCacheable 方法
 *
 * 流程：查 Redis → 命中返回 → 未命中执行原方法 → 异步回填
 */
@Aspect
@Component
public class HotCacheAspect {

    private final HotCacheTemplate cacheTemplate;

    @Around("@annotation(hotCacheable)")
    public Object around(ProceedingJoinPoint pjp, HotCacheable hotCacheable) throws Throwable {
        // 1. 解析 SpEL key
        String key = parseKey(hotCacheable.key(), pjp);

        // 2. 通过 HotCacheTemplate 双读
        return cacheTemplate.get(key, List.class, hotCacheable.ttlSeconds(), () -> {
            try {
                return pjp.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

```java
/**
 * 热缓存核心模板
 */
@Component
public class HotCacheTemplate {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // 熔断状态
    private volatile boolean degraded = false;
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, long ttlSeconds, Callable<T> dbLoader) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 熔断状态 → 直读 ES
            if (degraded) {
                meterRegistry.counter("hotcache.degrade", "reason", "circuit_breaker").increment();
                return dbLoader.call();
            }

            // 1. 查 Redis
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                meterRegistry.counter("hotcache.hit").increment();
                T result = objectMapper.readValue(cached, type);
                sample.stop(meterRegistry.timer("hotcache.get.duration"));
                return result;
            }

            // 2. 未命中 → 查 ES
            meterRegistry.counter("hotcache.miss").increment();
            T result = dbLoader.call();

            // 3. 异步回填
            if (result != null) {
                asyncCacheWriter(() -> {
                    String json = objectMapper.writeValueAsString(result);
                    redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
                });
            } else if (hotCacheable.cacheEmpty()) {
                asyncCacheWriter(() -> {
                    redisTemplate.opsForValue().set(key, "[]", 10, TimeUnit.SECONDS);
                });
            }

            consecutiveErrors.set(0);
            sample.stop(meterRegistry.timer("hotcache.get.duration"));
            return result;

        } catch (Exception e) {
            // Redis 异常 → 降级
            meterRegistry.counter("hotcache.degrade", "reason", "redis_error").increment();
            log.warn("HotCache Redis error, fallback to ES. key={}, error={}", key, e.getMessage());

            if (consecutiveErrors.incrementAndGet() >= DEGRADE_THRESHOLD) {
                degraded = true;
                log.error("HotCache circuit broken: {} consecutive errors", DEGRADE_THRESHOLD);
                scheduler.schedule(() -> degraded = false, 30, TimeUnit.SECONDS);
            }

            sample.stop(meterRegistry.timer("hotcache.get.duration"));
            return dbLoader.call();
        }
    }

    private void asyncCacheWriter(Runnable task) {
        CompletableFuture.runAsync(task, cacheWriteExecutor);
    }
}
```

### 12. 数据一致性兜底

#### 12.1 每日全量对账

```
利用已有的 OB ↔ ES 全量对账任务（每天凌晨 2:00），补充 Redis 热缓存校验：

增强对账任务逻辑：
  1. 扫描当天有变更的订单
  2. 对比 OB（权威数据源）与 ES + Redis 三方数据
  3. 不一致 → 重新回填 Redis 热缓存
  4. 输出一致性报告（不一致条数 / 修复条数）

XXL-Job @XxlJob("dataConsistencyCheck")
  ├── Step 1: 扫描 OB 当天变更订单（分批，每批 1000 条）
  ├── Step 2: 对比 ES 文档 → 不一致则修复 ES
  ├── Step 3: 对比 Redis 热缓存 → 不一致则回填
  └── Step 4: 输出报告（一致率 / 修复数 / 耗时）
```

#### 12.2 缓存手动修复

```
运营/运维需要手动修复缓存时：

Apollo 配置开关：
  hot.cache.admin.rebuild-buyer-ids = "1008612,1008613"
  → cache-writer 检测到配置变更
  → 对这些 buyer_id 执行全量重建（从 ES 拉取 → 覆盖写入 Redis）

Apollo 配置：
  hot.cache.admin.rebuild-all = false
  → 改为 true 时，触发全量热缓存重建（先清空再预热）
  → 修改后自动回滚为 false（一次触发）
```

---

## 故障场景与处理

| 场景 | 影响 | 自动处理 | RTO | RPO |
|------|------|---------|-----|-----|
| Redis 单节点宕机 | 该分片热缓存不可用 | 自动降级 ES，其他分片正常 | 0（立即降级） | 0 |
| Redis 集群完全不可用 | 所有热缓存不可用 | 自动降级 ES | 0（立即降级） | 0 |
| Canal cache-writer 宕机 | 热缓存不再刷新 | 缓存 30s 后过期，自动降级 ES | 0（逐步降级） | 0~30s |
| Canal cache-writer 恢复 | 缓存重新开始刷新 | 30s 后用户请求自动重建 | 30s | 0 |
| Canal 整体延迟 > 10s | 缓存数据可能过时 | Apollo 触发降级（可选） | 手动决定 | - |
| ES 不可用（缓存未命中） | 缓存未命中时无法回填 | 缓存 miss → ES timeout → 报错 | 取决于 ES 恢复 | 0 |
| 缓存数据不一致 | 列表展示与真实状态不符 | 30s TTL 自动过期 + 对账修复 | < 30s | 0~30s |

---

## 实施计划

### Phase 1：缓存基础设施（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| `HotCacheTemplate` 核心类开发 | 0.5d | 双读逻辑 + 降级 + 熔断 |
| `@HotCacheable` 注解 + AOP 切面 | 0.5d | 注解式缓存拦截 |
| 单元测试 + 集成测试 | 0.5d | 覆盖命中/未命中/降级/熔断场景 |
| 监控指标埋点 | 0.5d | Micrometer 指标 + Grafana 看板 |

### Phase 2：Canal Cache-Writer 增强（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| `HotCacheHandler` 开发 | 0.5d | binlog 解析 → 缓存写入 |
| 终态判断 + 移除逻辑 | 0.5d | 状态机 + 24h 阈值判断 |
| `BatchBuffer` 攒批写入 | 0.5d | PIPELINE 批量写入 |
| 单元测试 + 集成测试 | 0.5d | 覆盖 INSERT/UPDATE/DELETE 场景 |

### Phase 3：缓存预热 + 对账（1.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 启动预热逻辑 | 0.5d | ES 批量拉取 → 回填 Redis |
| 定时预热 XXL-Job | 0.5d | 每小时增量预热 |
| 一致性对账增强 | 0.5d | 对账任务增加 Redis 校验 |

### Phase 4：灰度上线（1.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| Apollo 开关 `hot.cache.enabled=false` | 0.25d | 兜底关闭能力 |
| 灰度放量（5% → 20% → 100%） | 0.5d | 逐步验证命中率和延迟 |
| 故障演练 | 0.5d | Redis 宕机降级验证 |
| 上线文档 + 监控确认 | 0.25d | 知识沉淀 |

**总计：7 人天**

---

## 上线检查清单

### 前置条件
- [ ] Redis 集群容量评估（日常 + 大促），确认热缓存所需内存在可接受范围
- [ ] Apollo 配置 `hot.cache.enabled=false`（默认关闭，灰度时开启）
- [ ] Apollo 配置 `hot.cache.degrade-on-high-delay=false`（默认关闭）

### 缓存写入验证
- [ ] Canal cache-writer 部署成功，HotCacheHandler 正确解析 binlog
- [ ] 订单 INSERT → Redis key 创建，内容正确
- [ ] 订单 UPDATE → Redis 中对应条目更新
- [ ] 订单终态 + 超 24h → Redis 中移除
- [ ] 订单 DELETE → Redis 中移除
- [ ] TTL 30s 验证：等待 30s 后 key 自动过期

### 缓存查询验证
- [ ] order-query-service 部署成功，@HotCacheable 注解生效
- [ ] 缓存命中 → 返回正确数据（< 5ms）
- [ ] 缓存未命中 → 回退 ES（返回正确数据）
- [ ] 空买家 → 缓存空列表 10s，下次查询命中空缓存

### 降级验证
- [ ] 手动停 Redis → 查询自动降级 ES，接口正常返回
- [ ] 恢复 Redis → 查询自动恢复缓存路径
- [ ] Apollo `hot.cache.enabled=false` → 所有查询走 ES

### 监控验证
- [ ] hit_rate 指标正常上报
- [ ] degrade 降级计数正常上报
- [ ] cache_size 持续增长到稳态后稳定
- [ ] Grafana 看板 6 个 panel 数据正常

### 回滚方案
- [ ] **Plan A（首选）**：Apollo 开关 `hot.cache.enabled=false` → 5s 生效，0 风险
- [ ] **Plan B（代码回滚）**：回退 Canal cache-writer 和 order-query-service 到上一版本

---

## 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §4.6 缓存策略 | 补充「买家的订单列表」缓存策略行 |
| 架构文档 §4.8 ES 索引 | 热缓存未命中时的 fallback 查询路径 |
| ADR-013 Canal 高可用 | canal-cache-writer Consumer Group 作为缓存刷新管道 |
| ADR-012 ES ILM | 热缓存减少 ES 高频查询，延长索引生命周期 |
| optimization-opportunities.md §5 | 本 ADR 是对 P2 #5 的详细展开 |

### 架构文档缓存策略表补充

在架构文档 §4.6.2 各场景缓存策略表中新增一行：

| 数据域 | 缓存内容 | TTL | 层级 | 策略 | 一致性要求 |
|--------|---------|-----|------|------|-----------|
| **买家订单列表** | 最近 24h 活跃订单摘要 SortedSet | 30s | L2 | Canal cache-writer 写入 + Cache-Aside 读取 | 最终一致（允许 3-30s 延迟） |
