# OmPlatform — 订单中台

**订单管理平台**：基于 Spring Cloud Alibaba + Dubbo + RocketMQ 的微服务订单中台，覆盖订单全生命周期管理。

## 架构

```
igw (Spring Cloud Gateway)      egw (Spring Cloud Gateway)
         │                              │
         └─────────── 微服务 ───────────┘
         │       │       │       │
    oms-trade  oms-fulfillment  oms-finance  oms-marketing
    oms-saga   oms-channel-adapter  oms-risk-integration
         │
    cart-service  seckill-service  wms-service
```

| 模块 | 职责 |
|------|------|
| **igw** | 内部 API 网关（BFF） |
| **egw** | 外部 API 网关（开放平台） |
| **oms-trade** | 交易核心（订单创建、取消、查询） |
| **oms-saga** | Saga 编排引擎 + TCC 事务支持 |
| **oms-fulfillment** | 履约中心（发货、库存扣减） |
| **oms-finance** | 财务中心（支付、对账、退款） |
| **oms-marketing** | 营销中心（优惠券、促销） |
| **oms-channel-adapter** | 渠道适配器（第三方平台订单接入） |
| **oms-risk-integration** | 风控集成 |
| **cart-service** | 购物车服务（Redis + DB 双写，Transactional Outbox） |
| **seckill-service** | 秒杀服务 |
| **wms-service** | 仓储服务 |

## 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | Spring Boot 3.4 + Spring Cloud 2024.0 |
| RPC | Apache Dubbo 3.3 |
| ORM | MyBatis-Plus 3.5.9 |
| 消息 | RocketMQ |
| 注册/配置 | Nacos |
| 分库分表 | ShardingSphere 5.4 |
| 网关 | Spring Cloud Gateway |
| 调度 | XXL-Job |
| 链 tracing | Micrometer + Zipkin |
| Java | 25 |

## 快速开始

```bash
# 启动基础设施（MySQL + Redis + Nacos + RocketMQ + XXL-Job）
cd deploy
docker compose up -d

# 初始化数据库
docker compose exec mysql bash -c "mysql -uroot -proot < /docker-entrypoint-initdb.d/01-init.sql"

# 启动服务
cd ..
mvn install -DskipTests
mvn spring-boot:run -pl oms-trade
mvn spring-boot:run -pl cart-service
```

## 模块依赖

```
oms-common ─── oms-api ─── 各微服务
                │
           oms-saga ──── oms-trade
                │
           oms-trade ─── oms-fulfillment
                       ── oms-finance
                       ── oms-marketing
                       ── oms-channel-adapter
```

## License

MIT
