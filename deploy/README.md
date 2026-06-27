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
