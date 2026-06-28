# ADR-050：MySQL MGR + ProxySQL 高可用架构

## 状态

已接受

---

## 背景

### 现状分析

当前 OM Platform 数据库架构存在以下问题：

**问题 1：单实例无高可用**  
所有数据库（33 个，含 11 个分片库）运行在单个 MySQL 8.0 实例上。实例宕机 = 全站不可用。无自动故障转移、无数据冗余、无读写分离。

**问题 2：ADR-040 的 OceanBase 方案与当前规模不匹配**  
ADR-040 Part B 规划了 OceanBase + Paxos 高可用方案。但项目实际规模（日 100 万订单，峰值 ~333 TPS）远未到 OceanBase 发挥分布式能力的临界点。引入 OB 意味着：
- 最低 3 节点，开发/测试环境资源成本高
- ShardingSphere + OB 双层分片复杂性
- Canal binlog 链路需换成 oblogproxy
- 运维人才稀缺

**问题 3：ShardingSphere 深度集成要求零代码改动**  
ADR-017 已落地完成：自定义分片算法、BusinessContext 路由、11 个分片数据源、232 张分片表。更换数据库层不能改动已测通的 ShardingSphere 配置和代码。

### 决策

采用 **MySQL 8.0 MGR（3 节点单主模式）+ ProxySQL（透明代理）** 作为高可用方案：

```
ShardingSphere（零改动）
        │
        ▼
ProxySQL（读写分离 + 故障转移）
   │         │         │
   ▼         ▼         ▼
MGR-Primary MGR-Sec  MGR-Sec
(写+强一致读)  (读)      (读)
```

**核心理念**：ProxySQL 是透明代理，应用只需改 JDBC URL 端口（`3306` → `6033`），所有读写分离和故障转移由 ProxySQL 自动处理。

### 目标

1. **高可用**：自动故障转移，RTO < 30s，RPO = 0
2. **零代码改动**：ShardingSphere 配置不变，应用代码不变
3. **读写分离**：SELECT 路由到从库，减轻主库读压力
4. **兼容现有链路**：Canal binlog 同步不受影响
5. **开发环境可复现**：Docker Compose 一键启动

---

## 架构

### 整体拓扑

```
                                 ┌──────────────────────┐
                                 │    ShardingSphere     │
                                 │  (11 个逻辑数据源)     │
                                 │  JDBC: localhost:6033 │
                                 └──────────┬───────────┘
                                            │
                                   ProxySQL │ 6033
                                            │
              ┌─────────────────────────────┼─────────────────────────────┐
              │                             │                             │
              ▼                             ▼                             ▼
     ┌────────────────┐          ┌────────────────┐          ┌────────────────┐
     │  mysql-mgr1    │          │  mysql-mgr2    │          │  mysql-mgr3    │
     │  127.0.0.1:3307│          │  127.0.0.1:3308│          │  127.0.0.1:3309│
     │  hostname: mgr1│          │  hostname: mgr2│          │  hostname: mgr3│
     ├────────────────┤          ├────────────────┤          ├────────────────┤
     │  PRIMARY       │◄─────────│  SECONDARY     │◄─────────│  SECONDARY     │
     │  (MGR 单主)     │─────────►│  (自动复制)      │─────────►│  (自动复制)      │
     └────────────────┘          └────────────────┘          └────────────────┘
               │                        │                           │
               └────────────────────────┼───────────────────────────┘
                                        │
                               ProxySQL │ 6032 (管理接口)
                                        │
                               ┌────────▼────────┐
                               │   mysql_monitor  │
                               │    health check  │
                               │    MGR detection │
                               └─────────────────┘

Canal（挂主库 binlog，主切换后自动重连）:
                                        │
                              ┌─────────▼──────────┐
                              │  Canal Server       │
                              │  → Elasticsearch    │
                              │  → Redis            │
                              └────────────────────┘
```

### 数据流

| 操作类型 | SQL 特征 | 路由目标 | 说明 |
|---------|----------|---------|------|
| 写操作 | INSERT / UPDATE / DELETE | MGR-Primary（hostgroup 0） | 所有写必须到主库 |
| 强一致读 | `SELECT ... FOR UPDATE` | MGR-Primary（hostgroup 0） | 事务内读也要到主库 |
| 弱一致读 | `SELECT ...`（无锁） | MGR-Secondary（hostgroup 1） | 路由到任意从库 |
| DDL | CREATE / ALTER / DROP | MGR-Primary（hostgroup 0） | DDL 自动复制到从库 |

### 端口分配

| 组件 | 端口 | 说明 |
|------|------|------|
| ProxySQL | 6033 | SQL 接口（应用连接） |
| ProxySQL | 6032 | 管理接口（DBA 维护） |
| mysql-mgr1 | 3307 | MGR 节点 1（初始 Primary） |
| mysql-mgr2 | 3308 | MGR 节点 2（初始 Secondary） |
| mysql-mgr3 | 3309 | MGR 节点 3（初始 Secondary） |
| MGR 内部通信 | 33061 | Group Replication 内部端口 |

---

## 配置细节

### MGR 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `gtid_mode` | ON | 启用 GTID，MGR 必需 |
| `enforce_gtid_consistency` | ON | 强制 GTID 一致性 |
| `binlog_checksum` | NONE | MGR 要求 NONE |
| `group_replication_single_primary_mode` | ON | 单主模式 |
| `group_replication_start_on_boot` | OFF | 启动时不自动启用组复制 |
| `group_replication_bootstrap_group` | OFF | 防止脑裂，仅引导时设为 ON |
| `plugin_load_add` | group_replication.so | 加载组复制插件 |

### ProxySQL 路由规则

ProxySQL 使用 `mysql_group_replication_hostgroups` 自动感知 MGR 主从角色：

1. 监控模块每秒探测 MGR 成员状态（`performance_schema.replication_group_members`）
2. 当 Primary 切换时自动将新主加入 writer_hostgroup，旧主移入 reader_hostgroup
3. SELECT 路由规则按 digest 匹配，`SELECT ... FOR UPDATE` 强制走主库

### 读写分离策略

- 日常所有 SELECT 走从库（hostgroup 1），INSERT/UPDATE/DELETE 走主库（hostgroup 0）
- 强一致性场景（`@Transactional` 内的读请求）通过 `SELECT ... FOR UPDATE` 标记，ProxySQL 自动路由到主库
- 从库延迟超过阈值（默认 10s）→ 从 ProxySQL 自动摘除，避免读脏数据

---

## 开发环境（Docker Compose）

### 启动方式

```bash
# 当前单实例（兼容旧测试）
docker-compose up -d

# MGR 集群（新架构）
docker-compose -f docker-compose-mgr.yml up -d

# 引导 MGR（首次启动后执行一次）
docker exec mgr1 bash /etc/mysql/init-mgr.sh

# 验证集群
docker exec mgr1 mysql -uroot -proot -e \
  "SELECT * FROM performance_schema.replication_group_members;"
```

### 数据导入

首次启动后，从单实例导出全量数据导入 MGR Primary：

```bash
# 从单实例导出
mysqldump -h127.0.0.1 -P3306 -uroot -proot \
  --databases oms_common oms_trade_... \
  > /tmp/mysql_dump.sql

# 导入 MGR Primary（通过 ProxySQL 自动同步到从库）
mysql -h127.0.0.1 -P6033 -uroot -proot < /tmp/mysql_dump.sql
```

---

## 故障切换

### Primary 宕机流程

```
1. MGR 检测到 Primary 失联（group_replication 超时，默认 5s）
2. MGR 自动选新主（基于 lexicographic 排序或 weight 设置）
3. ProxySQL 监控（1s 间隔）感知角色变化
4. ProxySQL 将新主加入 writer_hostgroup 0
5. 旧主恢复后自动加入 reader_hostgroup 1

预期 RTO: < 30s（含检测 5s + 选主 5s + ProxySQL 感知 10s）
预期 RPO: 0（MGR 同步复制，多数派确认才提交）
```

### Secondary 宕机流程

```
1. ProxySQL 标记宕机的从库为 OFFLINE
2. SELECT 流量自动路由到剩余从库和主库
3. 从库恢复后自动重新加入集群和 ProxySQL

影响: 无（读能力降级，写不受影响）
```

### 网络分区（脑裂）防护

- MGR 使用**多数派**机制：3 节点需 2/3 在线才能选主
- 单节点失联 → 自动退出集群，恢复后重新加入
- 双节点失联 → 剩余单节点变为只读，网络恢复后自动重建

---

# Part C — Redis Sentinel 高可用架构

---

## 架构

### 现状

Redis 同为单实例（`deploy/docker-compose.yml` port 6379），与 MySQL 一样无高可用。所有服务（订单缓存、购物车、库存 Lua 引擎、秒杀令牌桶、限流、风控）共用此单点。

### 决策

采用 **Redis Sentinel 3 节点（1 主 2 从 + 3 哨兵）**：

```
                       Sentinel-1   Sentinel-2   Sentinel-3
                      (26379)       (26380)       (26381)
                           \            |            /
                            \           |           /
                     ┌──────┴──────────┼──────────┴──────┐
                     │                 │                 │
              ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
              │  Master      │  │  Replica 1  │  │  Replica 2  │
              │  (6379)      │◄─│  (6380)     │◄─│  (6381)     │
              │  写 + 读     │  │  只读       │  │  只读       │
              └─────────────┘  └─────────────┘  └─────────────┘

应用连接:
  spring.redis.sentinel.master=mymaster
  spring.redis.sentinel.nodes=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
```

### 为什么是 Sentinel 而非 Cluster

| 维度 | Sentinel | Cluster |
|------|----------|---------|
| **Lua 多 key 操作** | ✅ 原生支持 | ❌ CROSSSLOT 错误 |
| **写扩展** | 单点写入（~333 TPS 够用） | 多分片（不需要） |
| **资源** | 3 实例 | 最少 6 实例 |
| **客户端兼容** | Spring Data Redis 原生支持 | 需 JedisCluster |

**核心约束**：库存系统的 Lua 脚本在一个事务中操作不同 key（`stock:{sku_id}:available`、`stock:hold:{request_id}`），这些 key 的 hash tag 不同（`{sku_id}` vs `{request_id}`），Cluster 模式下会抛 CROSSSLOT 错误。

## 配置

### Sentinel 参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `sentinel monitor mymaster` | `redis-master 6379 2` | 监控 master，2/3 哨兵确认即判定宕机 |
| `down-after-milliseconds` | 5000ms | 主观下线判定时间 |
| `failover-timeout` | 10000ms | 故障转移超时 |
| `parallel-syncs` | 1 | 新主选出后，同时同步的从库数 |

### 应用配置

所有 11 个服务的 `application.yml` 统一改为 Sentinel 模式：

```yaml
spring:
  data:
    redis:
      timeout: 3000ms
      sentinel:
        master: mymaster
        nodes:
          - 127.0.0.1:26379
          - 127.0.0.1:26380
          - 127.0.0.1:26381
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

## 故障切换

### Master 宕机流程

```
1. Sentinel-1 主观下线（PING 超时 5s）
2. 多数派哨兵（≥2/3）确认 → 客观下线
3. Sentinel 选举新 master（从 replica 中选 replica-priority 最高者）
4. 其余 replica 指向新 master
5. 应用通过 Sentinel 发现新 master 地址

预期 RTO: < 15s
预期 RPO: < 1s（异步复制延迟）
```

### Replica 宕机流程

```
1. Sentinel 标记 replica 为 OFFLINE
2. 读流量由剩余 replica + master 承接
3. replica 恢复后自动同步并重新加入

影响：无（读能力降级）
```

## 开发环境

集成在 `docker-compose-mgr.yml` 中：

```bash
# 启动全部（MySQL MGR + Redis Sentinel）
docker-compose -f docker-compose-mgr.yml up -d

# 验证 Redis Sentinel 集群
docker exec redis-master bash /etc/redis/init-sentinel.sh

# 验证故障切换（停止 master）
docker stop redis-master
sleep 15
# 验证新 master
docker exec sentinel1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

## 与现有 ADR 的关系

| ADR | 关系 | 说明 |
|-----|------|------|
| ADR-014 | 下层增强 | Redis 从单点升级为 Sentinel 高可用，上层缓存逻辑不变 |
| ADR-016 | 补充 | 多 AZ 缓存一致性 → AZ 内用 Sentinel 保证 HA |
| ADR-043 (库存) | 兼容 | Lua 脚本在 Sentinel 下全部工作在同一个 master，无跨 slot 问题 |
| ADR-040 | 补充 Part B | 缓存层 HA 目标以 Sentinel 实现 |

| ADR | 关系 | 说明 |
|-----|------|------|
| ADR-017 | 不影响 | 分片路由在 ShardingSphere 层，MGR 在存储层，完全透明 |
| ADR-040 | 补充 Part B | ADR-040 Part B 的数据库 HA 目标（99.99%），以 MySQL MGR 替代 OceanBase Paxos |
| ADR-013 (Canal) | 兼容 | Canal 挂 MGR-Primary binlog，主切换可自动重连（GTID 模式） |
| ADR-035 (多租户) | 不影响 | 租户隔离在应用层 |
| ADR-030 (幂等) | 不影响 | 幂等逻辑在应用层 |

---

## 回滚方案

```bash
# 停止 MGR 集群
docker-compose -f docker-compose-mgr.yml down

# 应用回到单实例
# 将 application.yml / nacos 的 JDBC 端口从 6033 改回 3306
# 重启应用
```
