# OM Platform — 本地开发环境启动指南

## 前置条件

- JDK 25 (Temurin OpenJDK)
- Maven 3.6.3+
- Docker Desktop (WSL2 backend)
- IntelliJ IDEA (推荐)

## 快速启动

### 1. 启动基础设施

```bash
cd deploy
docker-compose up -d
```

启动后验证各个组件：

```bash
# MySQL
docker exec om-mysql mysql -uroot -proot -e "SHOW DATABASES;"

# Redis
docker exec om-redis redis-cli ping

# Nacos
curl http://localhost:8848/nacos/actuator/health

# RocketMQ
docker exec om-rmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876
```

### 2. 构建项目

```bash
mvn clean install -DskipTests
```

### 3. 启动顺序

按以下顺序启动各微服务（等待上一个完全启动后再启动下一个）：

```bash
# 第 1 批：基础设施依赖启动后即可启动
mvn spring-boot:run -pl oms-common       # 端口 -
mvn spring-boot:run -pl oms-api          # 端口 -

# 第 2 批：核心服务
mvn spring-boot:run -pl oms-trade        # 端口 8080
mvn spring-boot:run -pl oms-fulfillment  # 端口 8081
mvn spring-boot:run -pl oms-finance      # 端口 8082

# 第 3 批：营销 + 风控 + 购物车
mvn spring-boot:run -pl oms-marketing    # 端口 8083
mvn spring-boot:run -pl oms-risk-integration  # 端口 8084
mvn spring-boot:run -pl cart-service     # 端口 8085

# 第 4 批：渠道适配 + 网关
mvn spring-boot:run -pl oms-channel-adapter  # 端口 8086
mvn spring-boot:run -pl igw              # 端口 8087
mvn spring-boot:run -pl egw              # 端口 8088
```

## 模块端口分配

| 模块 | 端口 | 说明 |
|------|------|------|
| oms-trade | 8080 | 交易核心 |
| oms-fulfillment | 8081 | 履约 |
| oms-finance | 8082 | 资金 |
| oms-marketing | 8083 | 营销 |
| oms-risk-integration | 8084 | 风控 |
| cart-service | 8085 | 购物车 |
| oms-channel-adapter | 8086 | 渠道适配 |
| igw | 8087 | 内部网关 |
| egw | 8088 | 外部网关 |

## 基础设施端口

| 组件 | 端口 |
|------|------|
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 (API) / 9848 (gRPC) |
| RocketMQ NameServer | 9876 |
| RocketMQ Broker | 10911 |
| RocketMQ Dashboard | 8081 |
| XXL-Job Admin | 8082 |

## 数据库初始化

DDL 脚本路径：`deploy/sql/init-all-databases.sql`

支持的数据库（共 9 个）：
- `oms_common` — 通用基础设施
- `oms_trade` — 交易核心（`order`, `order_items`, `order_operation_log`）
- `oms_cart` — 购物车（`cart_cart`, `cart_item`）
- `oms_marketing` — 营销（促销/优惠券/会员/定价）
- `oms_finance` — 资金（支付/退款/结算）
- `oms_fulfillment` — 履约（库存/履约单）
- `oms_channel_adapter` — 渠道适配
- `oms_risk` — 风控
- `oms_aftersale` — 售后

初始化脚本在 docker-compose 启动时自动执行。

## 验证运行

### Dubbo 调用链验证

```bash
# 1. 查询会员信息
curl http://localhost:8083/v1/...    # oms-marketing REST

# 2. 下单流程（通过 oms-trade）
curl -X POST http://localhost:8087/buyer/orders \
  -H "Content-Type: application/json" \
  -d '{"buyerId":"user001","items":[{"skuId":"SKU001","quantity":2,"unitPrice":99.99}]}'
```

### Nacos 服务发现

启动后访问 http://localhost:8848/nacos → "服务管理" → "服务列表"，
应能看到已注册的微服务实例。

---

## MySQL MGR 高可用集群（ADR-050）

### 架构

```
应用 / ShardingSphere → ProxySQL (6033) → MySQL MGR 3 节点
                                           ├── mgr1 (Primary, 3307)
                                           ├── mgr2 (Secondary, 3308)
                                           └── mgr3 (Secondary, 3309)
```

- **ProxySQL** 透明代理：读写分离（SELECT 走从库）+ 自动故障转移
- **应用零改动**：只需把 JDBC URL 端口从 `3306` 改为 `6033`
- **仅用于 MGR 模式**：单实例模式（`docker-compose.yml`）不受影响

### 启动集群

```bash
# 1. 启动 3 节点 MGR + ProxySQL
docker-compose -f docker-compose-mgr.yml up -d

# 2. 引导组复制（首次启动后执行一次）
docker exec mgr1 bash /etc/mysql/init-mgr.sh

# 3. 验证集群状态
docker exec mgr1 mysql -uroot -proot -e \
  "SELECT member_host, member_port, member_state \
   FROM performance_schema.replication_group_members;"

# 4. 验证 ProxySQL 连接
mysql -h127.0.0.1 -P6033 -uroot -proot -e "SHOW DATABASES;"
```

### 验证读写分离

```bash
# 连 ProxySQL 管理接口查看流量分布
mysql -h127.0.0.1 -P6032 -uradmin -pradmin -e \
  "SELECT hostgroup, srv_host, srv_port, status, ConnUsed, QueriesActive \
   FROM stats.stats_mysql_connection_pool;"
```

### 验证故障切换

```bash
# 1. 停止主库
docker stop mgr1

# 2. 等待 30s（MGR 检测 + 选主 + ProxySQL 感知）
sleep 30

# 3. 验证业务不受影响（应用通过 ProxySQL 连到新主）
mysql -h127.0.0.1 -P6033 -uroot -proot -e "SELECT 1;"

# 4. 查看新主是谁
docker exec mgr2 mysql -uroot -proot -e \
  "SELECT member_host, member_role \
   FROM performance_schema.replication_group_members\G"

# 5. 恢复旧主
docker start mgr1
# 旧主恢复后自动加入 MGR 作为 Secondary
```

### 切换回单实例模式

```bash
# 停 MGR 集群
docker-compose -f docker-compose-mgr.yml down -v

# 应用配置改回 localhost:3306
#   application.yml:    localhost:6033 → localhost:3306
#   nacos/oms-trade.yaml: localhost:6033 → localhost:3306

# 重启应用
```

---

## Redis Sentinel 高可用集群（ADR-050 Part C）

### 架构

```
应用 (StringRedisTemplate) → Sentinel → Redis Master (6379)
                                       ├── Replica 1 (6380, 只读)
                                       └── Replica 2 (6381, 只读)
```

- **Sentinel** 3 节点仲裁，自动故障转移
- **应用配置**从 `host:port` 改为 `sentinel.master/nodes`
- **Lua 脚本完全兼容**——所有 key 在同一个 master 操作，无跨 slot 问题

### 启动与验证

```bash
# Redis + Sentinel 已集成在 docker-compose-mgr.yml 中，启动时自动拉起
docker-compose -f docker-compose-mgr.yml up -d

# 验证 Redis Sentinel 集群
docker exec redis-master bash /etc/redis/init-sentinel.sh

# 查看集群状态
docker exec sentinel1 redis-cli -p 26379 SENTINEL master mymaster
docker exec sentinel1 redis-cli -p 26379 SENTINEL REPLICAS mymaster
docker exec sentinel1 redis-cli -p 26379 SENTINEL SENTINELS mymaster
```

### 验证故障切换

```bash
# 1. 停止 master
docker stop redis-master

# 2. 等待 Sentinel 完成故障切换（~15s）
sleep 15

# 3. 查看新 master
docker exec sentinel1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# 4. 验证应用仍可写入（通过 Sentinel 自动发现新 master）
#    应用无需重启，Lettuce 客户端自动感知 Sentinel 通知

# 5. 恢复旧 master
docker start redis-master
# 旧 master 恢复后以 replica 身份重新加入集群
```

### 切换回单实例模式

```bash
# 停 MGR + Sentinel 集群
docker-compose -f docker-compose-mgr.yml down -v

# 所有 11 个服务的 application.yml 改回 host: localhost / port: 6379
# 启动原有的单实例 docker-compose.yml
docker-compose up -d
```

# 应用配置改回 localhost:3306
#   application.yml:    localhost:6033 → localhost:3306
#   nacos/oms-trade.yaml: localhost:6033 → localhost:3306

# 重启应用
```
