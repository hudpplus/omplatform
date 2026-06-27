# ADR-017：多业务线数据物理隔离

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台采用 **一表多业务线** 架构——所有业务线的订单数据共享同一张 `order` 表，通过 `business_type` 字段（`ecommerce` / `locallife` / `b2b`）区分业务归属。

```
当前架构（改造前）：

        ┌──────────────────────────────────────────────┐
        │              order 表（共享）                   │
        │                                                │
        │  order_id │ buyer_id │ business_type │ ...     │
        │  ─────────┼──────────┼───────────────┼────     │
        │  10001    │ 1001     │ ecommerce     │ ...     │
        │  10002    │ 2001     │ locallife      │ ...     │
        │  10003    │ 3001     │ b2b           │ ...     │
        │  ...      │ ...      │ ...           │ ...     │
        └──────────────────────────────────────────────┘
                    │                 │              │
                    ▼                 ▼              ▼
              电商业务线          本地生活         B2B业务线
             (高并发秒杀)         (核销场景)       (多级审批)

        ┌──────────────────────────────────────────────┐
        │         orders-search ES 索引（共享）            │
        │  所有业务线写到同一个索引，business_type 过滤     │
        └──────────────────────────────────────────────┘
```

这种「逻辑隔离、物理共享」的模式在初期业务线少时高效简洁，但随着业务线扩展和差异化加深，暴露出以下问题。

### 存在的问题

**问题 1：业务线间相互干扰**  
电商大促秒杀时，订单写入 TPS 瞬间飙升到 5000+，占用 DB 连接池和 IO 资源，导致本地生活订单查询超时、B2B 审批提交缓慢。线下没有物理隔离时，一个业务线的流量高峰会「挤占」其他业务线的资源份额。

**问题 2：差异化 Schema 难以满足**  
各业务线的订单结构差异越来越大：
- 电商需要 `秒杀活动ID`、`预售阶段`、`优惠券分摊金额`
- 本地生活需要 `核销码`、`门店ID`、`服务时间`
- B2B 需要 `审批流ID`、`合同号`、`分期付款计划`

当前共享表用大量 `nullable` 字段 + `ext_info` JSON 存差异字段，导致：字段膨胀（order 表已 60+ 列）、JSON 字段查询不便（无法建索引）、类型安全缺失。

**问题 3：数据生命周期策略无法区分**  
| 业务线 | 归档策略 | 保留周期 | 当前情况 |
|--------|---------|---------|---------|
| 电商 | 终态 7 天后自动完成 | 3 年 | ❌ 无法按 business_type 设置不同分区生命周期 |
| 本地生活 | 核销即终态 | 1 年 | ❌ 大量已核销订单占用空间 |
| B2B | 审批完成 + 分期付清 | 7 年 | ❌ 长保留周期导致分区无法按时清理 |

共享表的分区策略只能按统一规则（如按月分区），无法为不同业务线定制。

**问题 4：ES 索引相互影响**  
共享 ES 索引中，电商的秒杀流量会占用 ES 的写入 buffer 和 segment 资源，导致本地生活查询延迟升高。ES 的 ILM 和 forcemerge 策略也必须统一，无法按业务线差异化。

**问题 5：扩展性瓶颈**  
当业务线从 3 个扩展到 5 个、8 个时，共享表会成为中央瓶颈：
- DDL 变更需要评估所有业务线的影响
- 索引数量膨胀（每个业务线都需要不同的查询组合）
- 无法独立扩缩容

### 业务线特征对比

| 维度 | 电商 (ecommerce) | 本地生活 (locallife) | B2B |
|------|-----------------|---------------------|-----|
| **日订单量** | ~80 万 | ~15 万 | ~5 万 |
| **峰值 TPS** | 5000+（秒杀） | 500 | 50 |
| **订单结构** | 60 列共享 + 20 列独立 | 60 列共享 + 15 列独立 | 60 列共享 + 30 列独立 |
| **终态时间** | 7 天自动完成 | 核销后即时完成 | 审批 + 分期，最长 90 天 |
| **数据保留** | 3 年 | 1 年 | 7 年 |
| **查询模式** | 买家高频查列表 | 核销员查今日单 | 财务/审批查进度 |
| **一致性要求** | 最终一致 | 最终一致 | 强一致（审批流） |
| **扩展优先级** | 高（大促保障） | 中 | 低（合规优先） |

---

## 决策

实施**多业务线数据物理隔离**，核心策略：

1. **分表存储**：各业务线使用独立的物理表 `order_ecommerce` / `order_locallife` / `order_b2b`，共享相同的基础字段集，各自扩展差异化字段
2. **DAO 层动态路由**：应用层根据 `business_type` 自动路由到对应的物理表/数据源
3. **ES 索引独立**：各业务线使用独立的 ES 索引，互不干扰
4. **渐进式迁移**：采用「双写 + 数据对账 + 灰度切换」策略上线，不停机

### 理由

| 维度 | 评估 |
|------|------|
| **效果** | 消除业务线间相互干扰，查询 P99 稳定度提升 90%（某业务线高峰不影响其他） |
| **成本** | 中~高 — 表结构调整 + DAO 路由改造 + ES 索引拆分 + 数据迁移 |
| **影响面** | 大 — 涉及 order-core / order-query / Canal / ES 等多个组件 |
| **风险** | 可管理 — 双写 + 对账 + 灰度切换控制迁移风险 |
| **收益周期** | 长期 — 基础设施投入，后续新业务线接入成本大幅降低 |

---

## 详细设计

### 1. 整体架构

```
改造后架构：

                        ┌──────────────────────┐
                        │   业务线路由层         │
                        │  BusinessRouter       │
                        │  (business_type → 表) │
                        └────┬──────┬──────┬───┘
                             │      │      │
              ┌──────────────┼──────┼──────┼──────────────┐
              │              │      │      │              │
              ▼              ▼      ▼      ▼              │
     ┌────────────┐ ┌──────────┐ ┌──────────┐             │
     │order_      │ │order_    │ │order_    │             │
     │ecommerce   │ │locallife │ │b2b       │             │
     │            │ │          │ │          │             │
     │基础字段(50)│ │基础字段同│ │基础字段同│             │
     │电商扩展(20)│ │本地扩(15)│ │B2B扩(30) │             │
     │分库: 8库   │ │分库: 2库 │ │分库: 1库 │             │
     │分表: 64表  │ │分表: 16表│ │分表: 4表 │             │
     └────────────┘ └──────────┘ └──────────┘             │
              │              │      │                      │
              └──────────────┼──────┼──────────────────────┘
                             │      │
                             ▼      ▼
                    ┌───────────────────┐
                    │   ES 独立索引       │
                    │                    │
                    │ orders-ecommerce-* │
                    │ orders-locallife-* │
                    │ orders-b2b-*       │
                    └───────────────────┘
```

**核心组件：**

| 组件 | 职责 |
|------|------|
| **BusinessRouter** | DAO 层路由：根据 `business_type` + 分片键决定目标表和数据库 |
| **OrderShardingAlgorithm** | 分库分表算法：按业务线和 buyer_id 联合路由 |
| **CanalMultiRouter** | Canal 多路分发：根据 `business_type` 路由 binlog 到不同 ES 索引和 Redis key 前缀 |
| **MigrationCoordinator** | 迁移协调器：控制双写/对账/切换全流程 |

### 2. 表结构设计

#### 2.1 基础字段集（所有业务线共享）

```sql
-- 基础订单表定义（各业务线独立建表，结构相同部分）
-- 以下为各业务线表共享的基础字段（约 50 列）

CREATE TABLE `order_ecommerce` (    -- 同理 order_locallife, order_b2b
    -- 核心标识
    `id`            bigint(20)    NOT NULL AUTO_INCREMENT  COMMENT '自增主键',
    `order_id`      bigint(20)    NOT NULL                  COMMENT '订单ID（全局唯一）',
    `order_no`      varchar(32)   NOT NULL                  COMMENT '订单号（可读）',
    `buyer_id`      bigint(20)    NOT NULL                  COMMENT '买家ID',
    `seller_id`     bigint(20)    DEFAULT NULL              COMMENT '卖家ID',
    `business_type` varchar(16)   NOT NULL                  COMMENT '业务线标识(冗余，路由用)',

    -- 金额
    `total_amount`  bigint(20)    NOT NULL                  COMMENT '总金额(分)',
    `pay_amount`    bigint(20)    DEFAULT NULL              COMMENT '实付金额(分)',
    `discount_amount` bigint(20)  DEFAULT NULL              COMMENT '优惠金额(分)',
    `freight_amount` bigint(20)   DEFAULT NULL              COMMENT '运费(分)',

    -- 状态
    `order_status`  varchar(32)   NOT NULL                  COMMENT '订单状态',
    `refund_status` varchar(32)   DEFAULT NULL              COMMENT '退款状态',
    `pre_sale_status` varchar(16) DEFAULT NULL              COMMENT '预售状态(电商)',
    `approval_status` varchar(16) DEFAULT NULL              COMMENT '审批状态(B2B)',

    -- 时间
    `gmt_create`    datetime(3)   NOT NULL                  COMMENT '创建时间',
    `gmt_modified`  datetime(3)   NOT NULL                  COMMENT '修改时间',
    `gmt_pay`       datetime(3)   DEFAULT NULL              COMMENT '支付时间',
    `gmt_completed` datetime(3)   DEFAULT NULL              COMMENT '完成时间',

    -- 其他
    `client_ip`     varchar(16)   DEFAULT NULL              COMMENT '客户端IP',
    `source`        varchar(32)   DEFAULT NULL              COMMENT '订单来源（APP/小程序/Web）',
    `remark`        varchar(512)  DEFAULT NULL              COMMENT '买家备注',

    -- 公共扩展（仅限所有业务线确实共用的字段）
    `ext_info`      json          DEFAULT NULL              COMMENT '公共扩展信息',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_buyer_id` (`buyer_id`, `gmt_create`),
    KEY `idx_seller_id` (`seller_id`, `gmt_create`),
    KEY `idx_order_status` (`order_status`, `gmt_create`),
    KEY `idx_gmt_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表-电商';
```

> 基础字段集控制在 50 列以内。`ext_info` JSON 仅放所有业务线确实共享的公共扩展。各业务线独有字段不进基础表。

#### 2.2 业务线扩展字段

```sql
-- ==================== 电商扩展表 ====================
CREATE TABLE `order_ecommerce_ext` (
    `order_id`         bigint(20)    NOT NULL        COMMENT '订单ID',
    -- 秒杀相关
    `seckill_activity_id` bigint(20)  DEFAULT NULL   COMMENT '秒杀活动ID',
    `seckill_pipeline`    varchar(16) DEFAULT NULL   COMMENT '秒杀批次',
    -- 预售相关
    `pre_sale_id`         bigint(20)  DEFAULT NULL   COMMENT '预售活动ID',
    `pre_sale_stage`      varchar(16) DEFAULT NULL   COMMENT '预售阶段(定金/尾款)',
    `pre_sale_deposit`    bigint(20)  DEFAULT NULL   COMMENT '定金金额(分)',
    -- 优惠分摊
    `coupon_id`           bigint(20)  DEFAULT NULL   COMMENT '优惠券ID',
    `coupon_split_amount` bigint(20)  DEFAULT NULL   COMMENT '优惠券分摊金额(分)',
    `promotion_id`        varchar(64) DEFAULT NULL   COMMENT '营销活动ID',
    -- 物流
    `delivery_type`       varchar(16) DEFAULT NULL   COMMENT '配送方式',
    `expected_delivery_time` datetime DEFAULT NULL   COMMENT '预计送达时间',
    `sign_time`           datetime    DEFAULT NULL   COMMENT '签收时间',

    PRIMARY KEY (`order_id`),
    KEY `idx_seckill_activity` (`seckill_activity_id`, `seckill_pipeline`),
    KEY `idx_pre_sale` (`pre_sale_id`, `pre_sale_stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单扩展-电商';

-- ==================== 本地生活扩展表 ====================
CREATE TABLE `order_locallife_ext` (
    `order_id`         bigint(20)   NOT NULL       COMMENT '订单ID',
    -- 核销相关
    `verification_code`  varchar(32) NOT NULL       COMMENT '核销码',
    `verification_status` varchar(16) DEFAULT NULL  COMMENT '核销状态(待核销/已核销/已退款)',
    `verification_time`  datetime    DEFAULT NULL   COMMENT '核销时间',
    `verifier_id`        bigint(20)  DEFAULT NULL   COMMENT '核销员ID',
    `store_id`           bigint(20)  NOT NULL       COMMENT '门店ID',
    `store_name`         varchar(64) DEFAULT NULL   COMMENT '门店名称',
    `service_time`       datetime    DEFAULT NULL   COMMENT '服务时间',
    `service_duration`   int(11)     DEFAULT NULL   COMMENT '服务时长(分钟)',
    `service_address`    varchar(256) DEFAULT NULL  COMMENT '服务地址',
    `technician_id`      bigint(20)  DEFAULT NULL   COMMENT '技师ID',

    PRIMARY KEY (`order_id`),
    KEY `idx_verification_code` (`verification_code`),
    KEY `idx_store_id` (`store_id`, `service_time`),
    KEY `idx_verifier_id` (`verifier_id`, `verification_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单扩展-本地生活';

-- ==================== B2B 扩展表 ====================
CREATE TABLE `order_b2b_ext` (
    `order_id`          bigint(20)   NOT NULL      COMMENT '订单ID',
    -- 审批相关
    `approval_flow_id`    varchar(64)  NOT NULL     COMMENT '审批流ID',
    `approval_status`     varchar(16)  DEFAULT NULL COMMENT '审批状态(待审批/通过/驳回)',
    `approval_node`       varchar(32)  DEFAULT NULL COMMENT '当前审批节点',
    `approval_detail`     json         DEFAULT NULL COMMENT '审批详情(审批人/时间/意见)',
    `contract_no`         varchar(64)  DEFAULT NULL COMMENT '合同号',
    `contract_file_url`   varchar(512) DEFAULT NULL COMMENT '合同文件URL',
    -- 分期相关
    `installment_plan_id` bigint(20)   DEFAULT NULL COMMENT '分期计划ID',
    `installment_count`   int(11)      DEFAULT NULL COMMENT '分期总期数',
    `installment_detail`  json         DEFAULT NULL COMMENT '分期详情(每期金额/时间)',
    -- B2B 特有
    `company_id`          bigint(20)   NOT NULL     COMMENT '企业ID',
    `invoice_type`        varchar(16)  DEFAULT NULL COMMENT '发票类型',
    `invoice_title`       varchar(128) DEFAULT NULL COMMENT '发票抬头',
    `tax_id`              varchar(32)  DEFAULT NULL COMMENT '税号',
    `purchase_order_no`   varchar(64)  DEFAULT NULL COMMENT '采购单号',

    PRIMARY KEY (`order_id`),
    KEY `idx_approval_flow` (`approval_flow_id`),
    KEY `idx_company_id` (`company_id`, `gmt_create`),
    KEY `idx_contract_no` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单扩展-B2B';
```

#### 2.3 表设计原则

```
基础表 vs 扩展表的设计权衡：

方案 A — 单宽表（各有独立列，列数不同）
  ┌──────────────────┐
  │ order_ecommerce   │ 50 基础 + 20 电商特有列 = 70 列
  │ order_locallife   │ 50 基础 + 15 本地特有列 = 65 列
  │ order_b2b         │ 50 基础 + 30 B2B 特有列 = 80 列
  └──────────────────┘
  优点：一次查询全量数据，无需 JOIN
  缺点：表宽，部分列大量 NULL；Schema 变更时各表单独 ALTER

方案 B — 基础表 + 扩展表（1:1 扩展）
  ┌─────────────┐    ┌──────────────────┐
  │ order_ecommerce │──→│ order_ecommerce_ext│
  │ order_locallife │──→│ order_locallife_ext│
  │ order_b2b       │──→│ order_b2b_ext      │
  └─────────────┘    └──────────────────┘
  优点：基础表紧凑；扩展表可独立查询；Schema 变更互不影响
  缺点：需要 JOIN 才能获取全量数据

选型：方案 B（基础表 + 扩展表）
  理由：
  ── 列表查询（我的订单）只需要基础字段，不需要 JOIN 扩展表
  ── 详情页需要扩展字段时 JOIN，频率较低（列表页的 1/10）
  ── 扩展表 Schema 变更不会影响基础表的稳定性和查询性能
  ── 扩展表可根据业务线特点独立优化（如 B2B 扩展表单独放到慢 IO 节点）
```

#### 2.4 分库分表策略

根据业务线特征采用不同的分片策略：

```
电商（高并发）：
  ── 分库：8 个数据库（同 OceanBase 实例或不同实例）
  ── 分表：64 张表（每库 8 表）
  ── 分片键：buyer_id
  ── 路由：buyer_id % 64 → 确定分表；buyer_id / 64 % 8 → 确定分库
  ── 容量：单表 ~500 万行（日 80 万 × 30 天），足够

本地生活（中等并发）：
  ── 分库：2 个数据库
  ── 分表：16 张表（每库 8 表）
  ── 分片键：buyer_id
  ── 路由：buyer_id % 16 → 分表；buyer_id / 16 % 2 → 分库
  ── 容量：单表 ~300 万行（日 15 万 × 60 天），足够

B2B（低并发、长周期）：
  ── 分库：1 个数据库（不拆分）
  ── 分表：4 张表
  ── 分片键：company_id
  ── 路由：company_id % 4 → 分表
  ── 容量：单表 ~500 万行（日 5 万 × 3 年），需要定期归档
```

### 3. DAO 层动态路由

#### 3.1 BusinessRouter 设计

```java
/**
 * 业务线路由器 — 根据 business_type 路由到对应的数据源和表
 *
 * 核心接口：对 order-core 和 order-query 透明
 * 上层代码只需传入 business_type，无需感知具体分表逻辑
 */
@Component
public class BusinessRouter {

    /**
     * 路由策略定义
     */
    private static final Map<String, RouteRule> ROUTE_RULES = new HashMap<>();

    static {
        // 电商：8 库 × 8 表 = 64 分区
        ROUTE_RULES.put("ecommerce", RouteRule.builder()
            .dbCount(8).tableCount(8)
            .dbIndexExtractor((shardKey) -> (shardKey / 8) % 8)
            .tableIndexExtractor((shardKey) -> shardKey % 8)
            .build());

        // 本地生活：2 库 × 8 表 = 16 分区
        ROUTE_RULES.put("locallife", RouteRule.builder()
            .dbCount(2).tableCount(8)
            .dbIndexExtractor((shardKey) -> (shardKey / 8) % 2)
            .tableIndexExtractor((shardKey) -> shardKey % 8)
            .build());

        // B2B：1 库 × 4 表 = 4 分区
        ROUTE_RULES.put("b2b", RouteRule.builder()
            .dbCount(1).tableCount(4)
            .dbIndexExtractor((shardKey) -> 0)
            .tableIndexExtractor((shardKey) -> shardKey % 4)
            .build());
    }

    /**
     * 获取目标表名
     *
     * @param businessType 业务线
     * @param shardKey     分片键（buyer_id / company_id）
     * @return 完整表名
     */
    public String resolveTable(String businessType, Long shardKey) {
        RouteRule rule = ROUTE_RULES.get(businessType);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown business type: " + businessType);
        }

        int shardValue = Math.abs(shardKey.hashCode());
        String baseTable = "order_" + businessType;
        int tableIndex = rule.getTableIndex(shardValue);
        return baseTable + "_" + tableIndex;
    }

    /**
     * 获取目标数据源
     */
    public String resolveDataSource(String businessType, Long shardKey) {
        RouteRule rule = ROUTE_RULES.get(businessType);
        int shardValue = Math.abs(shardKey.hashCode());
        int dbIndex = rule.getDbIndex(shardValue);
        return businessType + "_ds_" + dbIndex; // 对应 Spring DataSource bean name
    }
}
```

#### 3.2 MyBatis/MyBatis-Plus 拦截器集成

```java
/**
 * MyBatis 拦截器 — 自动注入 business_type 和分表逻辑
 *
 * 对业务代码透明：DAO 层仍然写 order，拦截器替换为真实表名
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Component
public class TableRoutingInterceptor implements Interceptor {

    @Autowired
    private BusinessRouter businessRouter;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 从 ThreadLocal 获取当前请求的 business_type 和分片键
        String businessType = BusinessContext.getBusinessType();
        Long shardKey = BusinessContext.getShardKey();

        if (StringUtils.isEmpty(businessType) || shardKey == null) {
            return invocation.proceed(); // 无上下文 → 走默认逻辑
        }

        // 替换 SQL 中的表名占位符
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String sql = boundSql.getSql();

        String targetTable = businessRouter.resolveTable(businessType, shardKey);
        String dataSource = businessRouter.resolveDataSource(businessType, shardKey);

        // 替换表名
        String newSql = sql.replace("order_ecommerce", targetTable);
        // 通过反射修改 BoundSql 的 SQL
        Field field = BoundSql.class.getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, newSql);

        return invocation.proceed();
    }
}
```

#### 3.3 数据源配置

```yaml
# 多数据源配置（ShardingSphere / Dynamic-Datasource）
spring:
  datasource:
    dynamic:
      primary: ecommerce_ds_0
      datasource:
        # 电商 8 库
        ecommerce_ds_0:  # ... 电商库 0
        ecommerce_ds_1:  # ... 电商库 1
        # ... 省略 ecommerce_ds_2~7
        # 本地生活 2 库
        locallife_ds_0:  # ... 本地生活库 0
        locallife_ds_1:  # ... 本地生活库 1
        # B2B 1 库
        b2b_ds_0:        # ... B2B 库 0
```

#### 3.4 业务层调用示例

```java
// order-core 创建订单
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    @Transactional
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        // 1. 设置业务线上下文
        BusinessContext.set(request.getBusinessType(), request.getBuyerId());

        try {
            // 2. 构建订单
            OrderDO order = buildOrder(request);

            // 3. 插入 — TableRoutingInterceptor 自动替换表名和数据源
            orderMapper.insert(order);

            // 4. 插入扩展信息
            if (request.getBusinessType().equals("ecommerce")) {
                orderEcommerceExtMapper.insert(buildEcommerceExt(request));
            } else if (request.getBusinessType().equals("locallife")) {
                orderLocallifeExtMapper.insert(buildLocallifeExt(request));
            }
            // ...

            return CreateOrderResult.success(order.getOrderId());
        } finally {
            BusinessContext.clear();
        }
    }
}
```

### 4. ES 索引拆分

#### 4.1 索引结构

```
索引命名规则：
  orders-{business_type}-{yyyy-MM}

示例：
  orders-ecommerce-2026-06    # 电商 2026年6月 索引
  orders-locallife-2026-06    # 本地生活 2026年6月 索引
  orders-b2b-2026-06          # B2B 2026年6月 索引
```

各业务线 ES 索引采用不同的 mapping、shard 数量和 ILM 策略：

```yaml
# 电商 ES 索引模板（高频写入，适当分片）
PUT _template/orders-ecommerce
{
  "index_patterns": ["orders-ecommerce-*"],
  "settings": {
    "number_of_shards": 12,
    "number_of_replicas": 1,
    "refresh_interval": "30s",
    "routing": { "required": true },
    "index": {
      "sort.field": ["gmt_create"],
      "sort.order": ["desc"]
    }
  },
  "mappings": {
    "properties": {
      "order_id":      { "type": "long" },
      "buyer_id":      { "type": "long" },
      "seller_id":     { "type": "long" },
      "order_status":  { "type": "keyword" },
      "pay_amount":    { "type": "long" },
      "gmt_create":    { "type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
      "gmt_modified":  { "type": "date" },
      "business_type": { "type": "keyword" },
      # 电商特有字段
      "seckill_activity_id": { "type": "long" },
      "pre_sale_stage":      { "type": "keyword" },
      "delivery_type":       { "type": "keyword" }
    }
  }
}

# 本地生活索引（中等写入，较少分片）
PUT _template/orders-locallife
{
  "index_patterns": ["orders-locallife-*"],
  "settings": {
    "number_of_shards": 6,
    "number_of_replicas": 1,
    "refresh_interval": "60s"
  },
  "mappings": {
    "properties": {
      ...基础字段...
      "verification_code": { "type": "keyword" },
      "store_id":          { "type": "long" },
      "service_time":      { "type": "date" }
    }
  }
}

# B2B 索引（低频写入，长保留，less shards）
PUT _template/orders-b2b
{
  "index_patterns": ["orders-b2b-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "120s"
  },
  "mappings": {
    "properties": {
      ...基础字段...
      "approval_status":   { "type": "keyword" },
      "company_id":        { "type": "long" },
      "contract_no":       { "type": "keyword" }
    }
  }
}
```

#### 4.2 ES 路由策略

```java
/**
 * ES 查询路由 — 根据 business_type 路由到对应索引
 */
@Component
public class EsIndexRouter {

    // 索引别名映射
    private static final Map<String, String> ALIAS_MAP = Map.of(
        "ecommerce", "orders-ecommerce-search",
        "locallife", "orders-locallife-search",
        "b2b",       "orders-b2b-search"
    );

    /**
     * 获取业务线对应的查询别名
     */
    public String getSearchAlias(String businessType) {
        return ALIAS_MAP.get(businessType);
    }

    /**
     * ES 查询示例 — MyBatis-Plus / 原生 ES client 调用
     */
    public PageResult<OrderDocument> searchOrders(String businessType, OrderQuery query) {
        String indexAlias = getSearchAlias(businessType);

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withIndices(indexAlias)
            .withQuery(buildQuery(query))
            .withPageable(PageRequest.of(query.getPage(), query.getSize()))
            .build();

        SearchHits<OrderDocument> hits = elasticsearchRestTemplate.search(searchQuery, OrderDocument.class);
        return convertToPageResult(hits);
    }
}
```

#### 4.3 Canal binlog 多路分发

Canal 消费端根据 `business_type` 分发到不同 ES 索引：

```java
/**
 * Canal ES 写入器 — 按业务线分发到不同 ES 索引
 *
 * 在 ADR-013 的 canal-es-writer Consumer Group 基础上改造
 */
@Component
public class CanalEsMultiRouter {

    private static final Map<String, String> INDEX_MAP = Map.of(
        "ecommerce", "orders-ecommerce",
        "locallife", "orders-locallife",
        "b2b",       "orders-b2b"
    );

    public void onMessage(CanalBinlogEvent event) {
        String businessType = event.getAfterColumn("business_type", String.class);
        if (businessType == null) return;

        String indexPrefix = INDEX_MAP.get(businessType);
        if (indexPrefix == null) return;  // 未知业务线

        // 按月分索引
        String dateSuffix = formatMonth(event.getAfterColumn("gmt_create", Date.class));
        String indexName = indexPrefix + "-" + dateSuffix;

        // 写入对应索引
        esWriter.index(indexName, event);
    }
}
```

### 5. 数据生命周期管理

#### 5.1 分区策略差异化

```sql
-- 电商：按月分区，保留 36 个月
CREATE TABLE `order_ecommerce` (
    ...
) ENGINE=InnoDB
PARTITION BY RANGE (TO_DAYS(gmt_create)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    ...
    PARTITION p202812 VALUES LESS THAN (TO_DAYS('2029-01-01'))  -- 3年
);

-- 本地生活：按月分区，保留 12 个月
CREATE TABLE `order_locallife` (
    ...
) ENGINE=InnoDB
PARTITION BY RANGE (TO_DAYS(gmt_create)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    ...
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01'))  -- 1年
);

-- B2B：按季度分区，保留 28 个季度（7 年）
CREATE TABLE `order_b2b` (
    ...
) ENGINE=InnoDB
PARTITION BY RANGE (TO_DAYS(gmt_create)) (
    PARTITION p2026Q1 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p2026Q2 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    ...
    PARTITION p2032Q4 VALUES LESS THAN (TO_DAYS('2033-01-01'))  -- 7年
);
```

#### 5.2 ES ILM 差异化

```yaml
# 电商 ILM（热 → 温 → 删除，3 年周期）
PUT _ilm/policy/ecommerce_ilm
{
  "policy": {
    "phases": {
      "hot":  { "min_age": "0d",   "actions": { "rollover": { "max_size": "50GB", "max_age": "30d" }}},
      "warm": { "min_age": "90d",  "actions": { "allocate": { "number_of_replicas": 0 }, "forcemerge": { "max_num_segments": 1 }}},
      "cold": { "min_age": "365d", "actions": { "freeze": {} }},
      "delete": { "min_age": "1095d","actions": { "delete": {} }}
    }
  }
}

# 本地生活 ILM（热 → 删除，1 年周期）
PUT _ilm/policy/locallife_ilm
{
  "policy": {
    "phases": {
      "hot":  { "min_age": "0d",   "actions": { "rollover": { "max_size": "30GB", "max_age": "30d" }}},
      "warm": { "min_age": "30d",  "actions": { "allocate": { "number_of_replicas": 0 }, "forcemerge": { "max_num_segments": 1 }}},
      "delete": { "min_age": "365d","actions": { "delete": {} }}
    }
  }
}

# B2B ILM（热 → 温 → 冷 → 删除，7 年周期）
PUT _ilm/policy/b2b_ilm
{
  "policy": {
    "phases": {
      "hot":  { "min_age": "0d",   "actions": { "rollover": { "max_size": "20GB", "max_age": "90d" }}},
      "warm": { "min_age": "365d", "actions": { "allocate": { "number_of_replicas": 0 }, "forcemerge": { "max_num_segments": 1 }}},
      "cold": { "min_age": "1095d","actions": { "freeze": {} }},
      "delete": { "min_age": "2555d","actions": { "delete": {} }}
    }
  }
}
```

### 6. 迁移策略

#### 6.1 双写 + 对账 + 灰度切换

```
迁移方案：渐进式不停机迁移

Phase 1 — 双写（T 到 T+2 周）
  ┌──────────────────────────────────────────────────────────────┐
  │ order-core 写入时同时写两张表：                                 │
  │                                                              │
  │   createOrder(request):                                      │
  │     # 写旧表（共享 order 表）                                   │
  │     oldOrderMapper.insert(order);   ← 老路径（正常）           │
  │     # 写新表（业务线独立表）                                     │
  │     RouteContext.set(businessType, buyerId);                  │
  │     newOrderMapper.insert(order);   ← 新路径（双写）           │
  │     RouteContext.clear();                                     │
  │                                                              │
  │  双写影响：写放大 2 倍，接口延迟增加 < 5ms（INSERT 本身是批量）  │
  └──────────────────────────────────────────────────────────────┘

Phase 2 — 数据对账（T+1 周到 T+3 周）
  ┌──────────────────────────────────────────────────────────────┐
  │ 每日对账脚本（XXL-Job），对比旧表和新表数据的一致性：              │
  │                                                              │
  │  @XxlJob("orderTableConsistencyCheck")                       │
  │  Step 1: 扫描旧表（按 create_time 分批，每批 1000 条）         │
  │  Step 2: 在新表中查找同一 order_id                             │
  │  Step 3: 对比关键字段（status/pay_amount/gmt_modified）        │
  │  Step 4: 不一致 → 以旧表为准回填新表                           │
  │  Step 5: 输出一致性报告                                        │
  │                                                              │
  │  对账通过标准：连续 3 天一致性 > 99.99%                         │
  └──────────────────────────────────────────────────────────────┘

Phase 3 — 灰度切换（T+3 周到 T+5 周）
  ┌──────────────────────────────────────────────────────────────┐
  │ 按业务线灰度切换（从风险最低的开始）：                             │
  │                                                              │
  │  Step 1: 本地生活（低并发，风险最小）→ 切换为只读新表           │
  │  Step 2: 观察 3 天，无异常 → 电商切换                             │
  │  Step 3: 电商观察 1 周，无异常 → B2B 切换                       │
  │  Step 4: 全部切换完成后，旧表保留只读 1 个月                     │
  │                                                              │
  │  切换方式：Apollo 配置开关                                     │
  │  db.migration.ecommerce.read-new = true   # 读新表             │
  │  db.migration.ecommerce.write-old = false  # 停写旧表          │
  └──────────────────────────────────────────────────────────────┘

Phase 4 — 旧表清理（T+5 周后）
  ┌──────────────────────────────────────────────────────────────┐
  │  1. 确认新表运行 1 个月无异常                                    │
  │  2. 下线旧表双写代码                                            │
  │  3. 旧表数据归档到冷存储（OSS/备份集群）                          │
  │  4. 删除旧表（保留最近 3 个月分区做回滚兜底）                     │
  └──────────────────────────────────────────────────────────────┘
```

#### 6.2 Apollo 迁移开关

```yaml
# Apollo 配置 — 数据迁移控制
db.migration:
  # 双写控制
  dual-write:
    ecommerce: true     # 电商双写开启
    locallife: true     # 本地生活双写开启
    b2b: true           # B2B 双写开启

  # 读取切换（灰度用 — 0 = 读旧表, 1 = 读新表, 0-1 间的值 = 按比例读新表）
  read-route:
    ecommerce: 0        # 电商 100% 读旧表
    locallife: 0        # 本地生活 100% 读旧表
    b2b: 0              # B2B 100% 读旧表

  # 写入切换（0 = 写旧表, 1 = 写新表 + 旧表, 2 = 仅写新表）
  write-route:
    ecommerce: 1
    locallife: 1
    b2b: 1

  # 回滚开关（true = 切回旧表）
  rollback:
    ecommerce: false
    locallife: false
    b2b: false
```

#### 6.3 ES 数据迁移

```
ES 索引拆分迁移（与 DB 迁移并行）：

Step 1 — 创建新索引
  PUT orders-ecommerce-2026-06
  PUT orders-locallife-2026-06
  PUT orders-b2b-2026-06

Step 2 — Canal 双写 ES
  Canal es-writer 改造为按 business_type 分发：
    ── 继续写旧索引 orders-search（兼容运行）
    ── 同时写新索引 orders-{business_type}-{yyyy-MM}

Step 3 — 历史数据 reindex
  旧索引 → 新索引，按 business_type 过滤：
  POST _reindex
  {
    "source": {
      "index": "orders-search-2026-05",
      "query": { "term": { "business_type": "ecommerce" } }
    },
    "dest": { "index": "orders-ecommerce-2026-05" }
  }

Step 4 — 切换查询路由
  灰度切换时，order-query 根据 business_type 读对应的新索引别名

Step 5 — 旧索引下线
  ES 旧索引保留 3 个月后删除
```

### 7. 回滚与降级

```
回滚方案：

Plan A — Apollo 读切换（秒级回滚）：
  db.migration.read-route.ecommerce = 0  → 读回旧表
  应用 5s 生效，风险 0

Plan B — 关闭双写（写旧表）：
  db.migration.write-route.ecommerce = 0  → 只写旧表，停写新表
  新表数据不更新但不影响线上

Plan C — 数据回填（如果新表数据有问题）：
  从旧表批量回填新表数据（对账脚本的反向执行）

降级场景：

场景 1：新表查询延迟高
  ── 即时切回读旧表（Plan A）
  ── 排查新表索引/数据分布问题

场景 2：双写导致旧表写入变慢
  ── 停新表双写（Plan B）
  ── 双写改为异步（MQ 缓冲 + 批量写入新表）

场景 3：数据不一致超预期
  ── 延长对账观察期（从 3 天到 1 周）
  ── 增加对账采样率（从 1% 到 100%）
```

### 8. 监控指标

#### 8.1 Prometheus 指标

```java
@Bean
public MeterRegistry businessLineMeterRegistry() {

    // 各业务线订单量
    Counter.builder("order.create.total")
        .tag("business_type", "ecommerce")
        .tag("business_type", "locallife")
        .tag("business_type", "b2b")
        .register(registry);

    // 各业务线 TPS
    Gauge.builder("order.tps", meterManager, m -> m.getTps("ecommerce"))
        .tag("business_type", "ecommerce")
        .register(registry);
    Gauge.builder("order.tps", meterManager, m -> m.getTps("locallife"))
        .tag("business_type", "locallife")
        .register(registry);
    Gauge.builder("order.tps", meterManager, m -> m.getTps("b2b"))
        .tag("business_type", "b2b")
        .register(registry);

    // 双写一致性（对账差异条数）
    Gauge.builder("db.migration.diff_count", migrationManager, m -> m.getDiffCount("ecommerce"))
        .tag("business_type", "ecommerce")
        .register(registry);

    // 双写延迟
    Timer.builder("db.migration.dual_write_duration")
        .tag("business_type", "ecommerce")
        .tag("business_type", "locallife")
        .tag("business_type", "b2b")
        .register(registry);

    // ES 各业务线查询延迟
    Timer.builder("es.search.duration")
        .tag("business_type", "ecommerce")
        .tag("business_type", "locallife")
        .tag("business_type", "b2b")
        .publishPercentiles(0.5, 0.99)
        .register(registry);

    return registry;
}
```

#### 8.2 告警规则

```yaml
groups:
  - name: business_isolation
    rules:
      - alert: DualWriteDiffHigh
        expr: db_migration_diff_count > 100
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "双写数据差异超过 100 条"
          description: "{{ $labels.business_type }} 双写差异 {{ $value }} 条，需检查双写逻辑"

      - alert: BusinessLineTpsSpike
        expr: |
          order_tps{business_type="ecommerce"} > 3000
          or order_tps{business_type="locallife"} > 500
          or order_tps{business_type="b2b"} > 100
        for: 1m
        labels: { severity: P2 }
        annotations:
          summary: "业务线 TPS 超过安全阈值"
          description: "{{ $labels.business_type }} TPS {{ $value }}, 超过日常水位线"

      - alert: BusinessTableQuerySlow
        expr: histogram_quantile(0.99, rate(order_query_duration_seconds_bucket[5m])) > 0.2
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "业务线订单查询 P99 超过 200ms"
          description: "{{ $labels.business_type }} 查询延迟 {{ $value }}s"

      - alert: MigrationReadRouteNotFullyCut
        expr: db_migration_read_route{ecommerce} < 1
        labels: { severity: P3 }
        annotations:
          summary: "业务线读路由尚未 100% 切换到新表"
          description: "电商读路由 {{ $value }}, 迁移尚未完成"
```

---

## 实施计划

### Phase 1：表结构 + 路由层（3 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 基础字段抽取 + 业务线扩展表 DDL | 1d | 3 套基础表 + 3 套扩展表 DDL |
| BusinessRouter + 分片算法 + 拦截器 | 1d | DAO 层动态路由组件 |
| 数据源配置 + MyBatis 适配 | 0.5d | ShardingSphere 多数据源配置 |
| 单元测试 | 0.5d | 路由逻辑正确性验证 |

### Phase 2：ES 索引拆分（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| ES 索引模板 + Mapping 配置 | 0.5d | 3 套索引模板 + ILM 策略 |
| Canal 多路分发改造 | 0.5d | EsMultiRouter 按 business_type 分发 |
| 索引别名 + 查询路由 | 0.5d | EsIndexRouter + 查询侧改造 |
| 历史数据 reindex 脚本 | 0.5d | _reindex 全量迁移脚本 |

### Phase 3：双写 + 对账（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 双写逻辑开发（order-core + Canal） | 1d | 双写 DAO + 灰度开关 |
| 一致性对账 XXL-Job | 0.5d | 对比统计 + 差异修复 |
| Apollo 迁移控制开关 | 0.5d | 灰度配置 + 回滚配置 |

### Phase 4：灰度切换 + 演练（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 本地生活灰度切换 & 验证 | 0.5d | 切换后稳定性观察 |
| 电商灰度切换 & 验证 | 0.5d | 大促流量的隔离效果验证 |
| B2B 灰度切换 & 验证 | 0.5d | 审批流/长周期验证 |
| 旧表清理 + 回滚演练 | 0.5d | 清理脚本 + 回滚验证 |

**总计：9 人天**

> 与 ADR-010~016 的 7 天估算相比，本 ADR 估算 9 天，因为涉及 SQL DDL + DAO 层改造 + ES 索引拆分 + 数据迁移四个大模块。实际工期取决于团队对 ShardingSphere 和 ES 迁移的熟悉程度。

---

## 上线检查清单

### 表结构
- [ ] 业务线基础表 DDL 已执行（order_ecommerce / order_locallife / order_b2b）
- [ ] 业务线扩展表 DDL 已执行（order_ecommerce_ext / order_locallife_ext / order_b2b_ext）
- [ ] 分区策略已按业务线生命周期配置
- [ ] 分库分表路由规则验证通过

### 路由层
- [ ] BusinessRouter 正确路由所有业务线 + 分片键组合
- [ ] TableRoutingInterceptor SQL 替换正确
- [ ] 多数据源事务配置正确（@Transactional 跨数据源时用 Seata/手工补偿）
- [ ] BusinessContext ThreadLocal 在请求结束时正确清理

### ES 索引
- [ ] 3 套 ES 索引模板已配置并生效
- [ ] 索引别名（orders-{business_type}-search）已配置
- [ ] ILM 策略已按业务线生命周期配置
- [ ] Canal 多路分发逻辑验证正确
- [ ] 历史数据 reindex 完成

### 双写 + 迁移
- [ ] 双写写入新表逻辑正确（旧表 + 新表数据一致）
- [ ] 一致性对账脚本运行正常，差异为 0
- [ ] Apollo 迁移开关可正常控制读/写路由
- [ ] 回滚演练通过（Plan A/B/C 均验证）

### 监控
- [ ] 各业务线 TPS / 延迟指标已上报
- [ ] 双写差异告警已配置
- [ ] ES 各业务线查询延迟已分业务线观测
- [ ] Grafana 看板已就绪

---

## 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §3.1 订单表结构 | 基础字段抽取为共享集，业务线扩展字段拆分到各自扩展表 |
| 架构文档 §3.3 分库分表 | 补充按业务线差异化的分片策略 |
| 架构文档 §4.8 ES 索引 | 补充按业务线独立的索引模板和 ILM 策略 |
| 架构文档 §6 部署 | 各业务线可独立扩缩容，补充资源规划差异 |
| ADR-013 Canal | Canal es-writer 多路分发改造 |
| ADR-015 容量模型 | 各业务线独立容量计算和 HPA 策略 |
| optimization-opportunities.md §9 | 本 ADR 是对 P3 #9 的详细展开 |
