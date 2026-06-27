# 订单中台架构 — 待优化点记录

> 本文档记录了当前架构设计中识别出的 34 个可优化方向，按影响面优先级分级。部分优化项可作为后续 Sprint 的输入。

---

## 优先级定义

| 等级 | 说明 |
|------|------|
| **P0** | 防止线上故障，必须先实施 |
| **P1** | 显著提升稳定性/降成本，建议近期实施 |
| **P2** | 性能/体验优化，择机实施 |
| **P3** | 远期演进方向 |

---

## P0 — 必须优化

### 0. ~~全渠道订单接入与分发（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-036 完成架构设计，标记为已解决。

**现状**：订单中台的所有 API 入口假设请求来自受信任的内部前端（IGW Buyer/IGW Admin）或开放平台第三方开发者（Ext Gateway），完全没有设计外部销售渠道（天猫、京东、拼多多、抖音等）的订单接入能力。不同渠道的订单格式、认证协议、路由规则均被忽略。

**建议方案**：
- **Channel Gateway**：独立 Gateway 实例处理渠道流量，与 IGW/Ext GW 隔离
- **Channel Adapter SPI**：插件化适配器框架，每个渠道一个 SPI 实现
- **订单标准化引擎**：字段映射 → 数据补全 → 规则校验 → 业务线路由
- **双向状态同步**：MQ 事件驱动异步回写 + XXL-Job 兜底对账
- **渠道注册表**：Apollo 配置驱动（热生效）

📄 详细设计文档：[ADR-036 全渠道订单接入与分发](adr/ADR-036-omni-channel-order-ingestion.md)

| 维度 | 评估 |
|------|------|
| **价值** | 从"仅内部前端接入"演进为"全渠道订单中台"，补齐作为订单中台的核心入口能力 |
| **成本** | 中（Channel Gateway + SPI 框架 + 标准化引擎 + 首批 2 个渠道适配器，约 17 人天） |
| **影响面** | 中（新增独立 Gateway 和 channel-adapter 服务，不影响现有核心链路） |

---

### 1. 领域事件 Schema 治理

**现状**：RocketMQ 领域事件直接发布 POJO，没有 Schema 注册和版本管理。事件数据结构变更时，消费者兼容性靠 Code Review 保障，缺少自动化校验。

**决策（ADR-010 已选定 方案 A）**：

- **Schema 存储**：Apollo（Namespace: `schema.order-events`），JSON Schema Draft-07
- **选型理由**：Apollo 已存在零新增；日均 10-50 TPS 事件量性能不是瓶颈；JSON 人类可读、现有 Jackson 零侵入
- **不选 Avro + Confluent SR**：引入额外集群、学习曲线陡、二进制排查困难，收益无法覆盖成本
- **兼容性保障**：CI 阶段 `schema-check` stage 自动校验 Forward Compatible

- **事件版本策略**：
  ```java
  public class OrderCreatedEvent {
      private static final int SCHEMA_VERSION = 2;
      private String orderId;
      private Long buyerId;
      @Since(version = 2) private String businessType;  // v2 新增
      @Deprecated @Until(version = 3) private String oldField;
  }
  ```

- **兼容性规则**：只能新增 optional 字段，不能删除/修改已有字段；消费者声明最低支持版本；违反兼容性的变更 → CI 阻断

- **事件归档**：所有领域事件写入 OceanBase 事件归档表（按月分区，保留 180 天），用于问题排查和数据对账

📄 详细设计文档：[ADR-010 事件 Schema 治理](adr/ADR-010-event-schema-governance.md)

| 维度 | 评估 |
|------|------|
| **价值** | 防止因事件结构变更导致线上故障 |
| **成本** | 中等（引入 Schema Registry + 改造事件发布/订阅模式） |
| **影响面** | 中 |

---

### 2. 零停机迁移与在线 DDL 规范

**现状**：ADR 提到「只增字段不改删」，但缺少具体的数据库变更流程。OceanBase 的 DDL 与 MySQL 行为不同，需要特别注意。

**建议方案**：

- **OceanBase DDL 规范**：
  ```sql
  -- 新增字段（秒级完成，不阻塞读写）
  ALTER TABLE `order` ADD COLUMN `new_field` VARCHAR(32) DEFAULT '' COMMENT 'xxx', ALGORITHM=INSTANT;
  
  -- 修改字段类型：仅支持扩大（VARCHAR(32)→VARCHAR(64)），不支持缩小或改变类型
  -- 新增索引：不阻塞 DML
  ALTER TABLE `order` ADD INDEX `idx_new` (`buyer_id`, `status`) ONLINE;
  ```

- **零停机迁移流程**：
  1. 应用层双写：新旧字段同时写入（旧字段兼容旧代码）
  2. 数据补偿：XXL-Job 分批回填历史数据
  3. 验证：对账脚本确认新旧字段一致
  4. 切换：新版代码全部上线后，下线旧字段写入
  5. 清理：旧字段保留只读（不删除），确认 1 个月后 DROP

- **DDL 版本化管理**：所有 DDL 脚本纳入 Liquibase/Flyway 管理，CI 中自动执行

📄 详细设计文档：[ADR-011 在线 DDL 治理](adr/ADR-011-online-ddl-governance.md)

| 维度 | 评估 |
|------|------|
| **价值** | 避免 DDL 导致的线上故障 |
| **成本** | 低（规范文档 + 流程标准化） |
| **影响面** | 小 |

### 3. ~~可配置订单流程引擎（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-037 完成架构设计，标记为已解决。

**现状**：订单处理流程全部硬编码在 Java `@Configuration` 类中（ADR-020 `CreateOrderSaga` / `RefundOrderSaga`），不同订单类型（实物/虚拟/预售/拼团）共用同一套步骤，无法差异化配置。订单生命周期关键节点（支付后/发货前/完成时）无法插入扩展逻辑。拆单、合单、运费、发票等策略不可配置。

**建议方案**：

- **流程模板（YAML DSL + Apollo）**：为实物、虚拟、预售、拼团、跨境等订单类型分别定义处理步骤
- **订单分类引擎**：根据商品属性（itemType + saleType + bizAttributes）自动匹配模板
- **扩展点/Hook SPI**：在 `after_payment` / `before_shipment` / `after_complete` 等生命周期事件插入业务逻辑，支持同步阻塞和异步两种模式
- **策略插件框架**：拆单（按仓库/商家/品类）、合单、运费（按重量/体积/满额包邮）、发票策略通过 Apollo 配置选择实现
- **与 Saga 集成**：流程模板动态生成 SagaDefinition，复用 ADR-020 的执行器、补偿、恢复机制

📄 详细设计文档：[ADR-037 可配置订单流程引擎](adr/ADR-037-configurable-order-process-engine.md)

| 维度 | 评估 |
|------|------|
| **价值** | 从"所有订单一种流程"演进为"按订单类型差异化配置"，补齐作为订单中台的核心编排能力 |
| **成本** | 中（流程引擎框架 + 策略插件 + 扩展点体系 + 首批 5 个模板，约 13 人天） |
| **影响面** | 中（内嵌到 workflow-service，与 Saga 集成；Apollo 开关灰度切换，无业务侵入） |

---

## P1 — 建议近期优化

### 3. ES 索引策略深度优化

**现状**：ES 按月分索引、6 shards、1 副本、强制 routing。整体合理，但缺少 ILM 自动化，字段存储有优化空间。

**建议方案**：

- **索引生命周期自动化（ILM）**：
  ```json
  {
    "policy": {
      "phases": {
        "hot":  { "min_age": "0d",  "actions": { "rollover": { "max_size": "50GB", "max_age": "30d" }}},
        "warm": { "min_age": "90d", "actions": { "allocate": { "number_of_replicas": 0 }, "forcemerge": { "max_num_segments": 1 }}},
        "cold": { "min_age": "180d","actions": { "freeze": {} }},
        "delete": { "min_age": "365d","actions": { "delete": {} }}
      }
    }
  }
  ```

- **Shard 数量按 TPS 调整**：日订单 100 万 ≈ 2GB/天 → 月索引 ~60GB → 推荐 6-12 shards（每个 5-10GB）

- **字段存储优化**：
  - 不需要索引的大文本字段（`buyer_remark` / `seller_remark`）：`"index": false`
  - 不需要聚合的字段：`"doc_values": false`
  - 收益：减少存储 20-30%

| 维度 | 评估 |
|------|------|
| **价值** | 降低存储成本 20-30%，查询性能提升 10-15% |
| **成本** | 低（ILM 配置变更，无需代码改动） |
| **影响面** | 小 |

📄 详细设计文档：[ADR-012 ES 索引 ILM + 字段优化](adr/ADR-012-es-index-ilm-and-field-optimization.md)

---

### 4. Canal 高可用 + 多分区消费

**现状**：方案采用 Canal binlog 主路径同步，但未详细展开 Canal 集群自身的高可用和消费隔离策略。

**建议方案**：

- **Canal 集群化**：部署 2+ 个 Canal Server（active-standby），通过 ZooKeeper/Redis 选主

- **多分区并行消费**：
  - 按表的主库分区（64 个 HASH 分区）分配到不同 Canal 实例
  - 每个 Canal 实例消费 16-32 个分区 binlog
  - 一个实例挂掉后，其他实例自动接手

- **消费隔离**：
  - ES 写入和缓存刷新用不同的 Consumer Group，互不影响
  - 缓存刷新 Consumer 可接受秒级延迟
  - ES 写入 Consumer 需保序（按 order_id hash 到同一分区）

- **延迟监控**：Canal 同步延迟作为 P1 告警（> 10s）

| 维度 | 评估 |
|------|------|
| **价值** | 消除单点故障，同步延迟从不稳定 → 稳定 < 3s |
| **成本** | 中等（Canal 集群运维 + 分区消费改造） |
| **影响面** | 中 |

📄 详细设计文档：[ADR-013 Canal 高可用 + 多分区消费](adr/ADR-013-canal-high-availability.md)

---

### 11. Saga 分布式事务治理

**现状**：核心交易链路（下单→扣库存→支付→确认）采用事件驱动（Choreography）模式，补偿逻辑没有形式化保障。支付失败后库存回滚依赖消息消费成功，缺少全局状态和恢复机制。

**建议方案**：

- **Saga 编排器（Orchestrator）**：每个核心流程对应一个 Saga 定义，由编排器统一调度正向步骤和执行补偿

- **SagaLog 持久化**：
  ```sql
  saga_instance     — Saga 实例状态（INITIATED / COMPLETED / COMPENSATED / COMPENSATE_FAILED）
  saga_step_log     — 每一步的执行和补偿结果
  idempotent_record — 全局幂等键（saga_id + step_name）
  ```

- **补偿机制**：每个正向步骤注册补偿操作，失败后逆序执行补偿；补偿失败自动重试 3-5 次，超过上限投递死信队列 + SRE 告警

- **恢复 Job**：XXL-Job 每 30s 扫描卡住/补偿失败的 Saga，自动恢复或触发重试

- **降级**：Saga 编排器不可用时回退到 Choreography 模式；通过 Apollo 开关控制

| 维度 | 评估 |
|------|------|
| **价值** | 防止分布式事务不一致导致资金/库存错误 |
| **成本** | 中（Saga 编排器 + 补偿注册 + 幂等改造） |
| **影响面** | 中（涉及 order-core / payment / inventory 核心链路） |

📄 详细设计文档：[ADR-020 Saga 分布式事务治理](adr/ADR-020-saga-distributed-transaction.md)

---

### 12. 延迟任务调度平台

**现状**：订单中台的延迟任务分散在各业务自行实现——支付超时关单用 XXL-Job 每分钟扫描 `order` 表（高峰期 DB CPU 升高 10-15%），自动确认收货用 XXL-Job 每天扫描，Saga 补偿重试用线程 Sleep（重启丢失），库存预占释放用 RocketMQ 延迟消息（不可取消、精度受限）。缺少统一的延迟任务调度平台。

**建议方案**：

- **分级调度（Tiered Scheduling）**：根据延迟时长选择不同的执行引擎
  - **Tier 1：内存时间轮**（< 30s）—— Netty HashedWheelTimer，100ms 精度，适用 Saga 补偿退避
  - **Tier 2：RocketMQ 延迟消息**（30s ~ 30min）—— 自动匹配 18 级延迟，支持可取消封装
  - **Tier 3：DB 时间桶轮询**（> 30min）—— 按分钟对齐 execute_at，避免全表扫描，适用自动确认收货

- **统一 API**：`DelayedTaskService.register()` 一行代码接入，自动路由到对应 Tier

- **任务生命周期管理**：支持取消、重排、查询、启动恢复（重启后重新调度未过期任务）

- **统一重试机制**：任务执行失败后按配置的退避间隔自动重试，超上限告警

| 维度 | 评估 |
|------|------|
| **价值** | 消除 DB 全表扫描压力、防止重启后补偿丢失、统一可观测性 |
| **成本** | 中（分级调度框架 + 3 个 Tier 实现 + 业务接入改造） |
| **影响面** | 中（涉及 order-core / fulfillment / saga / inventory 等多个服务） |

📄 详细设计文档：[ADR-021 延迟任务调度平台](adr/ADR-021-delayed-task-scheduling.md)

---

### 16. 认证授权体系

**现状**：`security.puml` 提到 JWT + OAuth2 + RBAC 但无详细设计。ADR-023（数据脱敏）依赖角色模型但未定义角色层级、Token 生命周期、Dubbo 角色传递机制。多业务线（ADR-017）数据已隔离但权限未隔离。

**建议方案**：

- **JWT Access Token + Refresh Token 体系**：30min Access Token + 7d Refresh Token，支持登出/密码变更吊销
- **RBAC 双维度角色模型**：垂直角色层级（super_admin > admin > finance > ops > cs > merchant）+ 水平业务线 Scope（ecommerce/locallife/b2b）
- **Dubbo Consumer/Provider Filter 做角色传播**：复用 ADR-022 同一种 SPI Filter 模式，从 Gateway 到服务端全链路传递 role + biz_scope
- **Gateway 鉴权过滤器**：JWT 校验 → 黑名单检查 → 写入请求头 → 路由到服务
- **多业务线权限隔离**：`@RequirePermission(role = "cs", bizScope = "ecommerce")` 注解 + AOP 拦截器，与 ADR-017 BusinessRouter 配合

| 维度 | 评估 |
|------|------|
| **价值** | 认证授权是所有安全特性的基础，ADR-023/025 的前置依赖 |
| **成本** | 中（Auth Service + Token 体系 + Dubbo Filter + AOP 权限注解，约 9 人天） |
| **影响面** | 大（涉及 Gateway / 所有服务 / ADR-023/025/017 均依赖此角色模型） |

📄 详细设计文档：[ADR-026 认证授权体系](adr/ADR-026-authentication-authorization.md)

---

### 17. 可观测性架构

**现状**：SkyWalking + Prometheus/Grafana 已列出但缺少统一规范。ADR-018 补充了 2 个看板但仅覆盖指标层面。20+ 服务无日志聚合标准、无链路采样策略、无 SLI/SLO 框架。日志/指标/链路三者割裂，排查问题时各自为政。

**建议方案**：

- **三支柱统一**：JSON 结构化日志（Loki）+ Prometheus 指标（统一 `omplatform_` 前缀）+ SkyWalking 链路
- **MDC 自动注入**：Web Filter + Dubbo Filter 自动注入 traceId、userId、orderId 到 MDC，业务零侵入
- **traceId 穿透三支柱**：Log ↔ Trace ↔ Metrics 通过 traceId 关联，Grafana 中一跳跳转
- **SLI/SLO 框架**：每个核心服务 5-8 个 SLI（可用性/延迟/吞吐/错误率），SLO 燃烧率告警
- **分层采样**：Prod 5% 头部采样 + 慢 trace（> 1s）尾部自动保留
- **告警分级路由**：P0 5min 电话 → P1 15min 群消息 → P2 1h → P3 日报

| 维度 | 评估 |
|------|------|
| **价值** | 没有统一观测标准，20+ 服务排查问题将极为困难；SLI/SLO 是稳定性运营的必选项 |
| **成本** | 小~中（规范制定 + Logback/Loki 配置 + SLI 定义 + 告警规则 + 看板，约 8 人天） |
| **影响面** | 大（所有服务需接入统一观测体系，但以配置为主无代码侵入） |

📄 详细设计文档：[ADR-027 可观测性架构](adr/ADR-027-observability-architecture.md)

---

### 18. 密钥管理

**现状**：`security.puml` 只提到了「Vault 密钥管理 90 天自动轮转」一行文字，无具体设计。DB 密码/Redis 密码/支付 RSA 密钥/JWT 签名密钥/AppSecret 的存储、注入、轮换均未定义。密钥泄露是最高优先级的安全生产事件。

**建议方案**：

- **HashiCorp Vault（KV v2 + Transit 引擎）**：3 节点 Raft 集群，支持私有化部署
- **Vault CSI Provider 注入 K8s Pod**：密钥作为 Volume 挂载到 `/vault/secrets/`，不落 K8s Secret 对象
- **密钥分级**：基础设施密码（L3）/ 支付网关密钥（L4）/ 字段加密密钥（L4）/ CI/CD 密钥
- **双密码模式实现零停机轮换**：新旧密钥共存窗口 → 应用滚动切换 → 清理旧密钥
- **Transit 引擎做字段级加密**：密钥不出 Vault，应用委托 Vault 完成 AES-256 加解密
- **审计日志**：所有密钥操作记录到 Vault 审计日志 → Loki 聚合（ADR-027）→ 保留 3 年

| 维度 | 评估 |
|------|------|
| **价值** | 密钥泄露是最严重的安全生产事件，无管理设计是重大安全缺口 |
| **成本** | 中（Vault 集群 + CSI 集成 + 密钥迁移 + 字段加密改造 + CI/CD 集成，约 8 人天） |
| **影响面** | 大（涉及所有服务 + 6 种中间件 + CI/CD + 支付密钥 + JWT 密钥） |

📄 详细设计文档：[ADR-028 密钥管理](adr/ADR-028-secrets-management.md)

---

### 28. ~~开放与集成能力（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-038 完成架构设计，标记为已解决。

**现状**：订单中台的 API 设计标准不统一——各服务返回不同响应格式（`Result<T>` / `ApiResponse<T>` / `PriceResponse`）。事件体系仅覆盖 Schema 治理，缺少事件分类目录、订阅管理和投递保障。可观测性缺少集成维度（无法按 AppKey/消费者组查看 API 使用情况和事件投递状态），导致集成方出问题时排查链路长、手段少。

**建议方案**：

- **统一三层集成架构**：API Layer（标准化接口） + Event Layer（事件中心） + Observability Layer（集成可观测）
- **API 分层标准化**：Buyer API / Admin API / Backend API / Open API 四层，统一 `ApiResult<T>` 响应格式、`PageResult<T>` 分页规范、4 位数字错误码体系
- **订单事件中心**：RocketMQ 事务消息事件发布 SDK、`event_subscription` 订阅管理、EventRouter 按事件类型/业务线/SpEL 条件路由、指数退避重试 + DLQ 投递保障
- **集成可观测性**：3 个 Grafana 集成健康看板（总览/API/事件），按 AppKey 和消费者组维度的指标聚合 + 告警规则
- **API 文档与 SDK**：Knife4j 多模块聚合 + OpenAPI 3.0 → OpenAPI Generator → Java/Python/PHP SDK 自动生成

📄 详细设计文档：[ADR-038 开放与集成能力](adr/ADR-038-openness-and-integration.md)

| 维度 | 评估 |
|------|------|
| **价值** | 中台服务能力产品化输出，集成方 onboarding 从天级降至小时级；事件驱动解耦减少点对点集成；集成健康大盘让集成问题从被动排查变为主动发现 |
| **成本** | 中（API 规范改造 + 事件中心建设 + 可观测性定制，约 12.5 人天） |
| **影响面** | 中（API 规范化改造涉及各服务 Controller 层，但格式层无业务侵入；事件中心新增的发布/订阅不影响现有业务逻辑） |

---

### 29. ~~订单全生命周期治理（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-039 完成架构设计，标记为已解决。

**现状**：订单状态机目前仅有基础的状态列表和 Mermaid 图（state-machine.md），无形式化定义。架构一致性报告标记 **C6 严重缺口**：状态机没有任何 ADR 覆盖。具体问题：

- **无状态机引擎**：非法转换（如 `COMPLETED → REFUNDING`）不被架构层拦截，完全依赖 Controller / Service 中的 if-else 判断
- **无原子服务契约**：创建/支付/修改/拆分/合并/取消/确认收货这些核心操作缺少标准接口（校验→转换→事件→补偿），散落在各处硬编码
- **无异常处理机制**：库存不足时无 HOLD 态标记、商家长时不发货无自动预警、退款卡住无自动检测、没有卡单扫描器和人工干预 API

**建议方案**：

- **状态机引擎**：13 态状态机（新增 HOLD / RETURNING / FROZEN）+ N×N 转换矩阵 + Guard 守卫 + 入口/出口动作 + 乐观锁 CAS 更新
- **7 个核心原子服务**：OrderCreateService / PaymentProcessService / OrderModifyService / OrderSplitService / OrderMergeService / OrderCancelService / ConfirmReceiptService，统一契约（validate → transition → execute → publishEvent → compensate）
- **异常处理框架**：状态超时矩阵（Apollo `state.timeout-matrix`）、StuckOrderDetector（XXL-Job 5min 扫描卡单）、HOLD 生命周期（自动释放/超时告警）、RefundReconciliationJob（退款对账）、人工干预 API（冻结/解冻/强制转换）
- **三层职责分离**：流程引擎（ADR-037 WHAT）→ 状态机引擎（WHEN 合法转换）→ 原子服务（HOW 执行）

📄 详细设计文档：[ADR-039 订单全生命周期管理](adr/ADR-039-order-lifecycle-management.md)

| 维度 | 评估 |
|------|------|
| **价值** | 从"状态机无 ADR 覆盖的严重缺口"到"完整的形式化状态机引擎 + 标准化原子服务 + 自动化异常处理"，补齐订单中台最核心的业务逻辑治理 |
| **成本** | 中（状态机引擎 + 7 个原子服务 + 异常处理框架 + 4 个时序图 + 文档，约 12.5 人天） |
| **影响面** | 中（涉及 order-core 状态变更逻辑，但以新增状态机引擎层为主，逐步替换现有散布的状态判断） |

---

### 30. ~~高性能与高可用架构（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-040 完成架构设计，标记为已解决。

**现状**：订单系统处于核心交易链路，但性能基线分散（ADR-015/024/027）、可用性 SLI 仅 99.95%（目标 99.99%+）、C4/C5 容灾文档引用错误、H14 统一 ID 生成缺失、Caffeine 本地缓存无设计、一致性模式分散。

**建议方案**：

- **性能层**：Caffeine L1(5s TTL) → Redis L2(30s) → ES L3(fallback) 多级缓存；MQ 异步削峰 + 请求排队；HikariCP 统一连接池 + Keyset 分页；热点 key 防护（Request Collapsing + L1 吸收）；跨服务 SLA 矩阵
- **可用性层**：99.99% 分解到各组件（order-core 99.995%）；per-service Sentinel 动态阈值 + Apollo profile 切换；3 层断路器（Sentinel/业务/Apollo 降级）；同城 Dual-Active（AZ-B 30% 预热，RTO < 60s）
- **一致性层**：event_outbox 本地消息表 + RocketMQ 事务消息双保险；Saga + 状态机 + 幂等三层集成；统一 ID 生成（Leaf Segment + Snowflake）；全量数据对账矩阵

📄 详细设计文档：[ADR-040 高性能与高可用](adr/ADR-040-high-performance-high-availability.md)、[ADR-041 统一 ID 生成](adr/ADR-041-unique-id-generation.md)、[reconciliation-matrix.md](reconciliation-matrix.md)

| 维度 | 评估 |
|------|------|
| **价值** | 从"性能基线分散 + 可用性 99.95% + 一致性模式割裂"到"完整 SLA 矩阵 + 99.99% 高可用架构 + 统一一致性保障"，补齐订单中台最核心的非功能性架构设计 |
| **成本** | 中（ADR 设计 + 4 份文档联动 + 修复 C2/C4/C5/H6 交叉引用，约 11.5 人天） |
| **影响面** | 小（以架构设计文档为主，代码影响为新增 L1 缓存 + event_outbox + ID 生成等独立组件，不改造现有业务逻辑） |

---

### 31. ~~支付结算中心（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-042 完成架构设计，标记为已解决。

**现状**：支付能力散落在 7+ 份 ADR 中——ADR-039 §2.3 定义了 PaymentProcessService，ADR-020 定义了 Saga chargePayment/refund 步骤，ADR-037 模板中引用支付步骤，ADR-038 定义了 payment.* 事件但无底层实现，ADR-040 定义了 payment-core SLI 但无架构设计。缺乏统一的多渠道支付网关、退款引擎、结算系统和自动化对账能力。

**建议方案**：

- **5 层架构**：支付网关层（SPI Adapter） → 支付核心（6 态支付单状态机） → 退款引擎（自动审核规则） → 结算中心（T+1/T+7/T+30 周期） → 对账会计（渠道账单匹配 + 会计分录）
- **多渠道 SPI**：类似 ADR-036 的 ChannelAdapter 模式，Alipay/WeChat SPI 实现，路由规则 Apollo 配置
- **支付单状态机**：INIT → PROCESSING → SUCCESS/FAILED → REFUNDING → REFUNDED（6 态，与 ADR-039 订单状态机解耦）
- **结算引擎**：Apollo 配置 merchant.settle.cycle（T+1/T+7/T+30），净额计算（交易总额 - 佣金 - 手续费 - 退款），银行代付出款
- **对账自动化**：T+1 渠道日账单下载 → 批量自动匹配 → 差异分类处理（长短款/缺失）→ P1 告警异常
- **支付安全**：密钥 AES-256 加密 + KMS 托管，Nonce + Timestamp 防重放，RSA256 回调验签
- **会计分录**：支付成功/退款/结算全链路借貸平衡（应收账款 / 主营业务收入 / 应付手续费）

| 维度 | 评估 |
|------|------|
| **价值** | 从"支付能力散落 7+ ADR + 无结算设计"到"统一支付结算中心"，补齐 P0 最大功能缺口 |
| **成本** | 中（ADR 设计 + 5 层架构 + SPI 框架 + 2 时序图 + 8 表 DDL + 4 层 API，约 11 人天） |
| **影响面** | 中（支付从 order-core 解耦为独立服务，不影响现有关键链路，通过 event_outbox + 幂等保障零故障迁移） |

📄 详细设计文档：[ADR-042 支付结算中心](adr/ADR-042-payment-settlement-center.md)、[payment-flow.puml](diagrams/sequence/payment-flow.puml)、[settlement-flow.puml](diagrams/sequence/settlement-flow.puml)

---

### 32. ~~库存管理中心（新设计）~~

> **2026-06-13 更新**：该能力已通过 ADR-043 完成架构设计，标记为已解决。

**现状**：库存能力散落在 15+ 份 ADR 中——ADR-020 定义了 Saga deductInventory/undoDeduct 步骤但无内部实现，ADR-039 §3.3 定义了 HOLD 生命周期但仅订单侧视角，ADR-037 定义了 reserve_inventory YAML 步骤但仅 Bean 映射，ADR-040 仅一句话提及 Redis Lua 两阶段协议，ADR-016 识别了 AZ 故障转移超卖风险但无具体方案。缺乏独立的库存管理服务设计。

**建议方案**：

- **两阶段预占协议**：Redis Lua 原子脚本（reserve_stock / confirm_deduct / release_hold / undo_deduct），下单时预占（Phase 1）→ 支付后确认（Phase 2），零超卖
- **4 种库存状态**：ACTIVE / FROZEN / DISABLED / ARCHIVED，覆盖冻结/解冻需求
- **渠道库存隔离**：SHARED / DEDICATED / RATIO 三种模式，配置化渠道隔离，防止渠道间库存相互影响
- **5 张数据表**：stock_item / inventory_hold / channel_stock_config / inventory_transaction / stock_alert_config
- **Redis Key 分片**：`stock:{sku}:{shard%100}` 解决 ADR-015 热点 SKU 风险
- **双重超时释放**：RocketMQ 延迟消息（15min 第一道防线）+ XXL-Job HoldReleaseJob（每 5min 兜底）
- **自动化对账**：Redis vs DB（每 30min）+ 渠道 vs 平台（每 1h）

| 维度 | 评估 |
|------|------|
| **价值** | 从"库存散落 15+ ADR + 无统一设计"到"库存管理中心"，补齐 P0 第二大功能缺口 |
| **成本** | 中（ADR 设计 + 4 Lua 脚本 + 5 表 DDL + 4 层 API + 2 时序图 + 1 组件图，约 8.5 人天） |
| **影响面** | 中（库存从 order-core/流程引擎中解耦为独立服务，不影响现有关键链路，通过 Saga+幂等保障一致性） |

📄 详细设计文档：[ADR-043 库存管理服务](adr/ADR-043-inventory-management-service.md)、[two-phase.puml](diagrams/sequence/inventory-two-phase.puml)、[component.puml](diagrams/c4/inventory-component.puml)

---

## P2 — 择机优化

### 5. CQRS 热数据缓存加速

**现状**：订单查询全走 ES。对高频的「我的订单」列表（最近 24h 活跃订单），全部走 ES 延迟偏高。

**建议方案**：

- 在 ES 前加一层 **Redis 热数据缓存**：
  - 仅缓存最近 24h 的活跃订单（状态非终态）
  - 数据结构：`Map<buyerId, SortedSet<OrderSummary>>` — 买家的最近订单列表脱敏摘要
  - TTL = 订单状态变更时主动刷新 + 最大 30s 过期

- **命中率预估**：80% 的「我的订单」查询发生在最近 24h 内，热缓存可覆盖

- **缓存未命中** → fallback 到 ES

| 维度 | 评估 |
|------|------|
| **价值** | 订单列表查询 P99 从 50ms → < 5ms |
| **成本** | 低（Redis 已存在，仅需增加缓存逻辑） |
| **影响面** | 小 |

📄 详细设计文档：[ADR-014 热数据缓存加速](adr/ADR-014-hot-data-cache-acceleration.md)

---

### 6. 性能容量规划模型

**现状**：验证方案有压测目标，但缺少系统化的容量评估模型和大促前的压测流程。

**建议方案**：

- **容量模型公式**：
  ```
  所需资源 = (日订单量 × 峰值因子 × 安全系数) / 单机处理能力

  以日单 100 万为例：
    峰值 TPS = 100万 × 80%(集中在 2h) / 7200s × 1.5(峰值因子) ≈ 1667 TPS
    安全系数 = 3 → 目标容量 ≈ 5000 TPS
    
    order-core: 5000 / 2000(单机QPS) ≈ 3 Pods（最低保底 4）
    payment: 5000 / 1500 ≈ 4 Pods
    inventory: 5000 / 3000(纯Redis操作) ≈ 2 Pods
  ```

- **大促压测 SOP**：
  1. 单链路压测 → 每个核心接口独立压测，找到瓶颈点
  2. 全链路压测 → 模拟大促流量，影子库隔离压测数据
  3. 混合场景 → 正常流量 70% + 秒杀 20% + 退款 10%
  4. 容量水位线 → 确定每个服务的 CPU/内存/连接数告警阈值

- **HPA 弹性伸缩规则**：
  ```yaml
  spec:
    minReplicas: 4
    maxReplicas: 20
    metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Pods
      pods:
        metric:
          name: order_dubbo_tps
          target:
            type: AverageValue
            averageValue: 1500
    behavior:
      scaleDown:
        stabilizationWindowSeconds: 300  # 避免频繁伸缩
  ```

| 维度 | 评估 |
|------|------|
| **价值** | 大促前明确容量缺口，避免线上过载 |
| **成本** | 低（文档 + 压测脚本 + HPA 配置） |
| **影响面** | 小 |

📄 详细设计文档：[ADR-015 容量规划模型](adr/ADR-015-capacity-planning-model.md)

---

### 7. 多 AZ 缓存一致性优化

**现状**：各 AZ 独立 Redis Cluster，不做跨 AZ 实时同步。AZ 故障切换后，新 AZ 的 Redis 冷启动会导致缓存击穿。

**建议方案**：

- **缓存预热策略**（AZ 切换后）：
  - Gateway 检测 AZ 切换 → 触发预热事件
  - 预热清单：活跃 buyer_id 的订单摘要（从 ES 批量拉取，回填 Redis）
  - 预热优先级：热数据（最近 1h 有操作）→ 温数据（最近 24h）
  - 预热期间：Redis miss 走 ES 直读（降级延迟，不崩溃）

- **分布式锁跨 AZ 兼容**：AZ 切换时分布式锁自动转移到新主 AZ（Redisson MultiLock + 看门狗）

- **缓存逐出差异化**：主 AZ 全量缓存短 TTL，备 AZ 仅缓存热数据长 TTL

| 维度 | 评估 |
|------|------|
| **价值** | AZ 切换时缓存冷启动时间从 5min → < 30s |
| **成本** | 中等（预热逻辑 + 分布式锁改造） |
| **影响面** | 中 |

📄 详细设计文档：[ADR-016 多 AZ 缓存一致性优化](adr/ADR-016-multi-az-cache-optimization.md)

---

### 8. 业务监控大盘增强

**现状**：已有 4 个 Grafana 看板，但缺少「资金链路专项看板」和「数据一致性体检看板」。

**建议方案**：

**看板 1：资金链路专项看板（财务 + 风控视角）**

```
实时 GMV（今日/昨日/同比）
支付成功率（每分钟）→ 支付失败原因分布
退款金额趋势（今日/昨日/同比）
资金对账状态（OB 支付记录 vs 三方支付账单）
交易异常事件时间线（支付超时/重复回调/退款失败）
```

**看板 2：数据一致性体检看板（DBA + SRE 视角）**

```
Canal 同步延迟（每分区粒度，热力图）
OB ↔ ES 一致性校验结果（每小时采样）
Redis ↔ OB 一致性延迟
消息堆积深度（按 Topic + Consumer Group）
死信队列数量 + 处理状态
数据修复任务执行状态（XXL-Job 进度/成功率）
```

| 维度 | 评估 |
|------|------|
| **价值** | 资金链路和数据一致性问题从「被动发现」→「主动监控」 |
| **成本** | 低（Grafana + Prometheus 已有，仅需补充指标 + Dashboard JSON） |
| **影响面** | 小 |

📄 详细设计文档：[ADR-018 业务监控大盘增强](adr/ADR-018-monitoring-dashboard-enhancement.md)

---

### 13. 全链路灰度发布

**现状**：当前灰度流程仅支持单服务发布（`canary-release.md`），通过 Istio 按比例切流到单个服务的灰度版本。当需求涉及多个服务（如新结算流程同步变更 order-core/payment/inventory），无法验证跨服务调用的版本一致性——灰度请求可能调用到稳定版接口，导致兼容性问题或数据错误。

**建议方案**：

- **Dubbo Filter + RpcContext 标签传播**：
  - `GrayTagConsumerFilter` / `GrayTagProviderFilter`：Dubbo SPI 过滤器，自动传递灰度标记
  - `GrayTagRouter`：自定义 Dubbo Router，根据 GrayTag 路由到 Nacos metadata 中 version 匹配的实例
  - `GatewayGrayFilter`：Spring Cloud Gateway GlobalFilter，负责识别灰度请求（Header/白名单/比例）

- **灰度判定流程**（短路判定）：
  ```
  请求头强制标签(x-gray-tag: force-) → 用户白名单
  → 按比例 hash(userId)%100 → Header/Path 匹配 → 默认稳定版
  ```

- **Apollo 灰度规则**：grayVersion / grayPercentage / userWhitelist / headerRules / tagToVersionMapping / serviceGrayList / dataIsolation

- **数据隔离**：同表 + gray_tag 列（首选）/ Redis 前缀 canary: / MQ Topic 前缀 canary_ / ES 索引后缀 -canary

- **故障保护**：主动熔断（错误率 > 1% → Apollo grayPercentage=0）→ 降级（灰度实例不可用 → 走稳定版）→ 数据回滚

| 维度 | 评估 |
|------|------|
| **价值** | 多服务协同变更可全链路验证；灰度数据不影响稳定环境；版本兼容性问题提前发现 |
| **成本** | 中（Dubbo SPI Filter/Router + Gateway Filter + Apollo 规则 + 数据隔离改造） |
| **影响面** | 中（涉及所有 Dubbo 服务，但 Filter/Router 无业务侵入） |

📄 详细设计文档：[ADR-022 全链路灰度发布](adr/ADR-022-full-chain-canary-release.md)

---

### 14. 动态数据脱敏

**现状**：`security.puml` 中已定义 `@Desensitize` 脱敏注解和 ShardingSphere Mask，但缺少完整设计——当前只有导出模块（ADR-019 `FieldPermissionFilter`）实现了字段级可见性控制，REST API 响应中的 PII 字段以明文返回给所有角色（cs/ops 可查看完整手机号、收货地址），存在越权查看 PII 的风险。

**建议方案**：

- **注解驱动的 Jackson 序列化脱敏**：
  ```java
  @Desensitize(type = PHONE, allowedRoles = {"admin", "finance"})
  private String buyerPhone;

  @Desensitize(type = NAME, deniedRoles = {"cs", "ops"})
  private String buyerName;
  ```

- **8 种脱敏策略**（统一标准）：
  - `PHONE`：`138****1234` — 手机号保留前 3 后 4
  - `NAME`：`张*` / `司**强` — 姓名保留首尾
  - `ID_CARD`：`110101********1234` — 身份证保留前 6 后 4
  - `BANK_CARD`：`6222****1234` — 银行卡保留前 4 后 4
  - `ADDRESS`：`北京市***街道***号` — 地址保留前 6 字
  - `EMAIL`：`u***@example.com` — 邮箱保留首字母 + 域名
  - `IP`：`10.88.*.*` — IP 保留前两段
  - `TAX_ID`：`913100*****89B` — 税号保留前 6 后 2

- **DesensitizeContext（ThreadLocal）**：Gateway 解析 JWT 后提取角色写入 Context，Jackson 序列化时根据角色决定是否脱敏

- **多通道覆盖**：
  - REST API：Jackson DesensitizeSerializer（核心）
  - Dubbo 响应：DesensitizeProviderFilter + RpcContext 角色传递
  - 数据导出：FieldPermissionFilter 增强（已有 ADR-019）
  - 日志打印：LogMaskingConverter 正则替换 Mask

- **Dubbo 内部调用跳过脱敏**：`x-internal-call=true` 标记 → 信任网络零开销

- **Apollo 动态配置**：脱敏熔断开关、角色-字段映射、日志脱敏 pattern 均可热更新

| 维度 | 评估 |
|------|------|
| **价值** | 防止 PII 越权查看，满足等保三级 + 个保法合规；日志零 PII 泄露 |
| **成本** | 中（Jackson 序列化器 + Dubbo Filter + DTO 标注 + 日志脱敏，约 9.5 人天） |
| **影响面** | 中（涉及所有对外 DTO，但序列化层无业务侵入） |

📄 详细设计文档：[ADR-023 动态数据脱敏](adr/ADR-023-dynamic-data-masking.md)

---

### 15. 慢 SQL 治理

**现状**：当前 CI 流水线有 SonarQube 代码分析、覆盖率门禁，但缺少 SQL 审查环节。OceanBase 慢查询、全表扫描、缺乏索引的查询直到压测或线上才暴露。性能退化无法区分是业务增长还是 SQL 劣化导致。

**建议方案**：

- **三层 SQL 治理体系**：
  - **L1 CI SQL Review Gate**：在 MR 阶段通过 SQLFluff + 自定义 MyBatis SQL Analyzer Maven 插件拦截劣质 SQL
  - **L2 运行时慢 SQL 检测**：采集 OceanBase `GV$OB_SQL_AUDIT` 视图，Prometheus 告警 + Grafana 看板
  - **L3 索引治理流程**：每周自动检测冗余/未使用索引 + Liquibase 标准化变更

- **核心查询性能基线**：
  ```yaml
  order.get-by-id:          P99基线 3ms  → 警告 10ms  → P1告警 50ms
  order.list-by-buyer:      P99基线 15ms → 警告 50ms  → P1告警 200ms
  order.list-by-seller:     P99基线 20ms → 警告 80ms  → P1告警 300ms
  order.status-summary:     P99基线 10ms → 警告 30ms  → P1告警 100ms
  ```

- **SQL 审查 9 条阻断/警告规则**：`SELECT *` 阻断 / 全表扫描阻断 / 缺分区键阻断 / JOIN > 3 阻断 / 前导 `%` 警告 / `NOT IN` 警告 / 深分页警告 / 循环 SQL 警告

- **5 种典型优化模式**：买家列表（分区键 + 覆盖索引）/ 游标分页替代 offset / 批量更新替代逐条 / Group By 走索引 / JOIN 改用分步查询

- **OceanBase Plan Binding**：紧急工具，通过 `OUTLINE` 绑定执行计划，SQL 计划劣化时秒级恢复

| 维度 | 评估 |
|------|------|
| **价值** | 劣质 SQL 在 MR 阶段拦截；线上慢 SQL 从被动排查变为主动发现；索引治理自动化 |
| **成本** | 中（SQL 静态分析插件 + 基线采集 + 慢查询采集 + 看板，约 9.5 人天） |
| **影响面** | 小（CI 阶段 + 运行时采集，无业务改动） |

📄 详细设计文档：[ADR-024 慢 SQL 治理](adr/ADR-024-slow-sql-governance.md)

---

### 19. 内部 Gateway 设计

**现状**：ADR-025 覆盖了外部 Gateway（HMAC、AppKey、配额、开发者门户），但内部 Gateway 的路由配置、限流、熔断、插件架构均未设计。container-diagram.puml 只有一个 "API Gateway"，未区分内/外。GatewayAuthFilter（ADR-026）是唯一的 Gateway 插件，缺少 SPI 扩展架构。

**建议方案**：

- **双 Gateway 独立部署**：内部 Gateway（JWT 认证 + 内部限流）与外部 Gateway（HMAC 签名 + AppKey 配额）从容器图中拆分
- **Apollo 动态路由**：路由配置存储在 Apollo `gateway.routes` 命名空间，支持热刷新
- **Sentinel 限流 + 熔断**：QPS/并发线程限流 + 慢调用/异常比例熔断，Apollo 动态推送阈值
- **GlobalFilter 链 SPI 架构**：AuthFilter → RateLimitFilter → DegradeFilter → VersionRouteFilter → BizScopeFilter → AuditLogFilter
- **Knife4j 多模块 API 文档聚合**：内部 API / 管理 API 分组，版本标签

| 维度 | 评估 |
|------|------|
| **价值** | 防止内部 API 被外部滥用；路由配置可动态变更；限流熔断防止级联故障 |
| **成本** | 中（Gateway 拆分 + Sentinel 集成 + 路由重构，约 6 人天） |
| **影响面** | 中（需调整 Gateway 配置，服务本身无变更） |

📄 详细设计文档：[ADR-029 内部 Gateway 设计](adr/ADR-029-internal-gateway-design.md)

---

### 20. 全局幂等框架

**现状**：幂等逻辑散落在 5+ 处——Saga `idempotent_record` 表（ADR-020）、Gateway nonce 防重放（ADR-025）、延迟任务 business_key 去重（ADR-021）、事件 eventId（ADR-010）、支付回调 payment_transaction_id 唯一索引。缺少统一的 Idempotency-Key 标准、Gateway 层拦截、后端存储选型、Dubbo 幂等拦截器（ADR-020 已规划但未设计）。

**建议方案**：

- **Idempotency-Key 请求头标准**：客户端 UUID v4，组成 `{method}:{path}:{key}`
- **Gateway 层拦截**：Redis `SET idempotent:{key} {response} EX {ttl} NX`，重复请求返回缓存响应
- **Redis + DB 双存储**：Redis 主链路快速判断，DB `idempotent_record` 唯一索引持久化兜底
- **@Idempotent 注解 + AOP**：SpEL 表达式从参数提取 Key，自动执行幂等逻辑
- **Dubbo Provider Filter**：`@Activate(order = -8000)` SPI 模式，从 RpcContext 取幂等 Key
- **响应语义**：首次 201 Created / 重复 200 OK（返回缓存）/ Key 冲突 409 Conflict

| 维度 | 评估 |
|------|------|
| **价值** | 统一幂等语义防止重复处理，消除 5 个分散方案的维护成本 |
| **成本** | 中（幂等框架建设 + 存量业务逐个接入，约 5 人天） |
| **影响面** | 中（需逐业务接入，但框架侵入低，Apollo 开关控制） |

📄 详细设计文档：[ADR-030 全局幂等框架](adr/ADR-030-idempotency-framework.md)

---

### 21. 数据归档与生命周期管理

**现状**：保留策略散落在 10+ 个 ADR（ES ILM 365d / 事件归档 180d / 电商 3yr / 本地生活 1yr / B2B 7yr / 任务 7d / Saga 30d 等），security.puml 仅一行文字描述，无统一治理。ES ILM 365d 删除与 B2B 7 年保留直接冲突。无冷存储（OSS）归档设计、无法规合规（个保法/GDPR）框架。

**建议方案**：

- **四级分类**：热（OB）→ 温（ES）→ 冷（OSS Parquet）→ 销毁（匿名化 + 清除）
- **保留策略矩阵**：按数据域 × 业务线的统一保留周期表，Apollo `data.lifecycle` 命名空间管理
- **三步安全删除**：逻辑标记（`is_deleted=1`）→ 90d 观察期 → 匿名化（PII 清零）→ 30d 最终保留 → 物理清除
- **ES 生命周期协调**：电商 365d delete，B2B 改为 7y 保留冷索引 + OSS 快照归档
- **合规 API**：GDPR 被遗忘权接口、等保三级数据分类/审计映射
- **XXL-Job 统一编排**：ArchivalJob / PurgeJob / AnonymizeJob / EsArchiveJob

| 维度 | 评估 |
|------|------|
| **价值** | 统一数据生命周期降低存储成本，满足法规合规义务 |
| **成本** | 中（归档框架 + 冷存储 + 合规审计，约 5.5 人天） |
| **影响面** | 中（涉及所有服务的数据清理逻辑，但框架层统一管理） |

📄 详细设计文档：[ADR-031 数据归档与生命周期管理](adr/ADR-031-data-lifecycle-management.md)

---

### 24. 成本优化策略

**现状**：当前架构设计以稳定性为首要目标，成本优化尚未系统性开展。OB 5 节点 × 3 AZ、ES 9 节点、Redis 全量缓存等设计预留了充足安全余量，但缺少成本基线、资源利用率和降本策略。月成本估算 ¥21-34 万，部分资源利用率低于 30%。

**建议方案**：

- **基础设施层**：K8s Request 合理化（CPU 降低 20-30%）、预留实例 + Spot 混合部署（节省 35-45%）、非核心服务超卖
- **数据存储层**：OB zstd 压缩调优（存储降 30-50%）、ES 温/冷热分层（存储降 40-60%）、Redis TTL 差异化 + Value 结构优化（内存降 15-25%）
- **网络层**：Dubbo 同 AZ 优先路由（跨 AZ 流量降 40-60%）、请求合并与批量操作
- **运营效率**：非生产环境非工作时间缩容（年节省 ¥5-10 万）

| 维度 | 评估 |
|------|------|
| **价值** | 预期月降 ¥4.6-10.2 万（当前成本 20-35%） |
| **成本** | 低~中（P0 快速见效 5 人天，全链路约 24 人天） |
| **影响面** | 小（配置变更为主，无业务侵入） |

📄 详细设计文档：[成本优化策略](../cost-optimization.md)

---

### 25. DevSecOps 安全左移

**现状**：当前安全设计集中在运行时（ADR-023 脱敏、ADR-025 API 认证、ADR-026 授权、ADR-028 密钥管理），CI/CD 流水线侧的安全左移尚未覆盖。代码安全扫描（SAST）、依赖成分分析（SCA）、镜像漏洞扫描、IaC 配置扫描、策略即代码（OPA/Gatekeeper）均为空白。安全漏洞直到运行时才暴露，修复成本高。

**建议方案**：

- **SAST**：SonarQube 扩展安全规则集，覆盖 SQL 注入/XSS/RCE/硬编码密钥
- **SCA**：OWASP Dependency-Check Maven 插件，CVSS ≥ 7 阻断构建
- **SBOM**：CycloneDX 每次构建自动生成，推送 Dependency-Track 持续监控
- **镜像安全**：Trivy 扫描，Critical + High 阻断
- **IaC 扫描**：Checkov 扫描 K8s/ Terraform/Dockerfile 配置
- **策略即代码**：OPA/Gatekeeper 准入控制（resource limits、镜像标签、PDB）
- **DAST**：ZAP 每两周扫描暴露 API 端点

| 维度 | 评估 |
|------|------|
| **价值** | 安全漏洞在 CI 阶段发现而非生产；满足等保三级安全开发要求 |
| **成本** | 中（工具集成 + 门禁配置 + 策略编写，约 8.5 人天） |
| **影响面** | 中（CI 流水线改造，开发阶段新增阻断门禁） |

📄 详细设计文档：[DevSecOps 策略与安全左移](../devsecops-strategy.md)

---

## P3 — 远期演进

### 9. 多业务线数据物理隔离

**现状**：所有业务线（电商/本地生活/B2B）共享同一张 `order` 表，通过 `business_type` 字段区分。

**建议方案**：

- 各业务线使用独立的 **分区集** 或 **独立物理表** `order_ecommerce` / `order_locallife` / `order_b2b`

- **不同业务线差异**：
  - 电商：高并发秒杀，7 天自动完成，3 年保存
  - 本地生活：核销流程，即时完成，1 年保存
  - B2B：多级审批、分期付款、7 年保存

- ES 索引同样分业务线：`orders-ecommerce-{yyyy-MM}` 等

- 可各自独立扩缩容（不同业务线写入 TPS 差异大）

| 维度 | 评估 |
|------|------|
| **价值** | 大幅降低业务线间相互影响，查询性能更稳定 |
| **成本** | 中等~高（改造表结构 + DAO 层路由） |
| **影响面** | 大 |

📄 详细设计文档：[ADR-017 多业务线数据物理隔离](adr/ADR-017-business-line-physical-isolation.md)

---

### 10. 订单导入导出 + 异步任务中心

**现状**：需求中提到了大数据量导出场景（财务对账、运营数据），但缺少专门的异步任务处理机制。

**建议方案**：

- **Async Job 框架**：
  - 将数据导出/批量操作等耗时任务异步化
  - 使用 Redis Stream / RocketMQ 做任务队列
  - 前端轮询任务进度条

- **导出策略**：
  - 小量（< 1 万条）：直接同步导出
  - 中量（1-10 万条）：异步生成 CSV/Excel，上传 OSS，通知下载链接
  - 大量（> 10 万条）：异步导出 + 分片 + 压缩包

- **ES Scroll + PIT 做深度导出**：使用 Point-in-Time + Scroll 避免深翻页，导出期间保持 PIT 快照一致性

- **导出权限控制**：按角色限制最大导出条数和字段，导出操作写入审计日志

| 维度 | 评估 |
|------|------|
| **价值** | 大促期间财务/运营导出不影响在线查询性能 |
| **成本** | 中等（Async Job 基础设施 + 导出模块开发） |
| **影响面** | 中 |

📄 详细设计文档：[ADR-019 异步任务中心](adr/ADR-019-async-job-center.md)

---

### 11. API 版本管理 + 外部网关

**现状**：`context-diagram.puml` 中定义了「开放平台」（`open_api`，第三方开发者接入）作为系统边界，但缺少完整设计。当前系统没有 API 版本管理，所有 API 不带版本前缀（如 `/v1/`），内部前端与 API 紧耦合。外部第三方开发者共用内部 Gateway 和 JWT 鉴权，缺少应用级凭证（appKey/appSecret）和独立的外部网关。

**建议方案**：

- **API 版本化**：URL 路径版本 `/api/v1/orders`，支持 N-1 兼容（最多 2 个主版本共存）
  - 废弃版本返回 `Sunset` + `Warning` 响应头
  - 版本生命周期：GA → 维护期（+6个月）→ 废弃（+12个月）→ 下线（+18个月）

- **独立外部 Gateway**（与内部 Gateway 隔离）：
  - HMAC-SHA256 签名认证（AppKey + AppSecret，请求级别防重放）
  - 应用级 QPS 配額 + 日配额（Redis Lua 原子计数）
  - Nonce 防重放 + 时间戳校验 ±5min
  - IP 白名单 + 异常检测自动封禁

- **开发者门户**：应用注册/审核 → AppKey 发放 → 沙箱环境 → SDK 自动生成（Java/Python/PHP）
- **统一响应规范**：`OpenApiResponse<T>` + 标准化错误码（1xxx 认证 / 2xxx 参数 / 3xxx 业务 / 4xxx 限流 / 5xxx 系统）
- **外部 API 数据脱敏**：始终遵循 ADR-023 脱敏规则，第三方默认最严格级别

| 维度 | 评估 |
|------|------|
| **价值** | 开放平台能力从概念到落地；API 版本化保障平滑演进；第三方接入规范化 |
| **成本** | 中（外部 Gateway + 开发者门户 + SDK 生成，约 11 人天） |
| **影响面** | 小（外部 Gateway 独立部署，内部 API 加版本前缀无业务逻辑改动） |

📄 详细设计文档：[ADR-025 API 版本管理 + 外部网关](adr/ADR-025-api-versioning-and-external-gateway.md)

---

### 22. 本地开发环境

**现状**：仅有 ADR-011 提到 Liquibase 连接 `localhost:2881`。无 docker-compose、无 Mock 策略、无测试数据种子。6 种中间件需手动安装，onboarding 耗时 1-3 天。OceanBase 容器需要 8G+ 内存，低配开发机无法运行。

**建议方案**：

- **docker-compose 一键启动**：MySQL 8.0（替代 OB）+ Redis + RocketMQ + ES + Nacos + Apollo，`docker compose up -d` 全部就绪
- **MySQL 替代 OceanBase**：本地开发用 MySQL 8.0 容器，OB 特有功能加 `@Profile("ob")` 注解
- **Testcontainers 集成测试基类**：MySQL/Redis/ES 容器自动启动，外部依赖用 WireMock Mock
- **测试数据播种**：data-dev.sql 种子脚本 + XXL-Job 可选播种任务
- **Apollo 降级模式**：本地开发禁用 Apollo，从 `application-dev.yml` 加载配置
- **调试与热重载**：IDEA Remote Debug 配置 + DevTools 热重载 + Debug API

| 维度 | 评估 |
|------|------|
| **价值** | 新成员 onboarding 从天级降至小时级，本地可在无网络环境下开发 |
| **成本** | 低~中（compose 配置 + 种子数据 + 文档，约 3 人天） |
| **影响面** | 小（仅影响开发环境，不影响生产） |

📄 详细设计文档：[ADR-032 本地开发环境](adr/ADR-032-local-development-environment.md)

---

### 23. Webhook 系统

**现状**：无 Webhook 投递系统。ADR-025 在应用注册时收集了回调 URL 但从未使用。多个时序图中出现的通知服务（`notify`）仅有概念，无具体设计。第三方开发者只能轮询获取订单状态变更，延迟高、效率低。

**建议方案**：

- **事件驱动异步投递**：业务服务发布事件 → RocketMQ → WebhookDispatchConsumer → HTTP POST 到订阅 URL
- **CRUD 订阅管理**：`webhook_subscription` 表 + `POST /admin/v1/webhooks` 管理 API
- **HMAC-SHA256 签名**：排序 JSON + 时间戳 + Secret 签名，接收方验证 + 时间戳 ±5min 防重放
- **指数退避重试**：1s→2s→4s→8s→16s→32s→1m→2m→4m→8m，最多 10 次，DLQ 兜底
- **at-least-once + 幂等**：`X-Webhook-Id` 供接收方幂等，XXL-Job 兜底重试
- **可观测性**：`omplatform_webhook_` 指标 + 按 app_key 的 Grafana 面板 + DLQ 告警

| 维度 | 评估 |
|------|------|
| **价值** | 使第三方开发者能实时接收订单状态变更，无需轮询 API |
| **成本** | 中（投递引擎 + 订阅管理 + 重试 + 监控，约 4.5 人天） |
| **影响面** | 中（新增外部回调通道，但业务服务仅需 `publish()` 一行调用） |

📄 详细设计文档：[ADR-033 Webhook 系统](adr/ADR-033-webhook-system.md)

---

### 26. 多租户架构

**现状**：当前系统假设单一逻辑租户。所有 ADR（017 物理隔离、023 脱敏、025 API 认证、026 认证授权）均未预留多租户扩展点。Redis key 无租户前缀、ES 索引无 tenant_id、MQ 无 X-Tenant-Id 透传。若后续需支持多平台运营或 SaaS 交付，架构改造涉及面广。

**建议方案**：

- **隔离模型**：核心数据（order/payment）用 Schema 隔离，非核心数据（商品/日志）用字段级 `tenant_id` 共享
- **上下文传递**：Gateway 从 `X-Tenant-Id` Header 或 JWT claim 提取，Dubbo Filter 全链路透传
- **数据源路由**：`TenantAwareDataSource` 动态切换 Schema（复用 Liquibase 模板）
- **ES/Redis/MQ**：独立索引 / key 前缀 / 消息属性标记
- **实施建议**：Phase 0 仅做 TenantContext + 预留设计（2 人天），不阻塞一期交付

| 维度 | 评估 |
|------|------|
| **价值** | 为多平台运营/SaaS 交付预留架构扩展点 |
| **成本** | 中（Phase 0 预留 2 人天，全框架约 11 人天） |
| **影响面** | 大（涉及 Gateway/所有服务/中间件，但 Phase 0 不做则不影响一期） |

📄 详细设计文档：[ADR-035 多租户架构](adr/ADR-035-multi-tenancy.md)

---

### 27. 更多外部渠道适配器接入

**现状**：ADR-036 设计了全渠道接入架构（Channel Gateway + SPI 适配器框架），但首批仅规划了天猫和京东两个渠道适配器的示例实现。

**建议方案**：

- **首批 P0 渠道**：天猫（Webhook + API）+ 京东（Webhook + API）— 已完成适配器 SPI 设计
- **P1 渠道**：拼多多（Pull 模式定时拉取 + 对账）、抖音（Webhook 推送）
- **P2 渠道**：微信小程序（消息解密 + SessionKey）、线下 POS（API Key + 定时推送）
- **P3 渠道**：快手、小红书、唯品会（长尾渠道，按业务需求排序接入）

**接入优先级评估因素**：渠道订单占比、渠道 API 完备度、对接复杂度、业务需求紧急度

| 维度 | 评估 |
|------|------|
| **价值** | 覆盖更多销售渠道，真正实现"全渠道"订单接入 |
| **成本** | 每个渠道适配器 2-4 人天（取决于渠道 API 复杂度） |
| **影响面** | 小（通过 SPI 扩展，不影响现有核心链路） |

---

### 32. 限界上下文地图（战略 DDD）✅ 已完成

> **2026-06-14 更新**：已创建 `docs/bounded-context-map.md`

**现状**：服务边界由微服务直觉驱动，未从 DDD 限界上下文角度显式记录不同上下文的职责边界、关系模式和共享术语。

**建议方案**：
- 创建限界上下文地图，标注每个上下文的域类型（核心/支撑/通用/防腐层）
- 用 DDD 上下文映射模式（C/S、P、ACL、OHS）标记服务间关系
- 跟踪共享术语在各上下文中的语义差异

📄 已创建文档：`docs/bounded-context-map.md`

| 维度 | 评估 |
|------|------|
| **价值** | 消除上下文边界模糊，新团队 onboarding 时间缩短 |
| **成本** | 低（1 人天，已创建） |
| **影响面** | 中（所有服务理解需统一） |

### 33. 通用语言术语表（战略 DDD）✅ 已完成

> **2026-06-14 更新**：已创建 `docs/ubiquitous-language.md`

**现状**：术语散落在各 ADR 中且不统一——order-core/order-service/OrderService 混用，幂等/去重/Idempotent 混用，全/金丝雀/灰度混用。

**建议方案**：
- 建立项目级术语表，统一中英文术语
- 区分易混淆概念（幂等 vs 去重）
- 废弃错误术语追踪

📄 已创建文档：`docs/ubiquitous-language.md`

| 维度 | 评估 |
|------|------|
| **价值** | 从源头消除命名不一致（53 项缺陷中的 20+ 可避免） |
| **成本** | 低（0.5 人天，已创建） |
| **影响面** | 中（所有 ADR 和代码需逐步对齐） |

### 34. 战术 DDD 设计补充 ✅ 已完成

> **2026-06-14 更新**：ADR-044~048 已补充聚合根/Entity/VO/领域事件/Repository 章节

**现状**：各 ADR 缺少显式的 DDD 战术模式描述，新开发人员不知道业务如何映射到代码。

**已完成补充**：
| ADR | 补充内容 |
|-----|---------|
| ADR-044 购物车 | Cart 聚合根、PriceInfo/SkuSnapshot VO、CartItemAdded 等事件 |
| ADR-045 营销价格 | PromotionActivity/CouponInstance 聚合根、Money/DiscountAllocation VO |
| ADR-046 会员 | Member 聚合根、GrowthValue/MemberBenefit VO |
| ADR-047 风控 | RiskCheckRecord/RiskReviewRecord 聚合根、RiskScore VO |
| ADR-048 售后 | AftersaleOrder 聚合根、RefundAmount/ReturnAddress VO |

| 维度 | 评估 |
|------|------|
| **价值** | 明确领域模型边界，统一团队设计语言 |
| **成本** | 中（已为 5 个 ADR 补充，剩余可逐步跟进） |
| **影响面** | 中（需团队学习 DDD 术语） |

---

## 汇总

| 优先级 | 编号 | 优化项 | 价值 | 成本 | 影响面 |
|--------|------|--------|------|------|--------|
| **P0** | 0 | ~~全渠道订单接入与分发~~ | ✅ **ADR-036 已完成** | — | — |
| **P0** | 1 | ~~领域事件 Schema 治理~~ | ✅ **ADR-010 已完成** | — | — |
| **P0** | 2 | 在线 DDL 规范 | 防止 DDL 故障 | 低 | 小 |
| **P0** | 3 | ~~可配置订单流程引擎~~ | ✅ **ADR-037 已完成** | — | — |
| **P1** | 3 | ES 索引 ILM + 字段优化 | 降低存储 20-30% | 低 | 小 |
| **P1** | 4 | Canal 高可用 + 多分区消费 | 消除单点故障 | 中 | 中 |
| **P1** | 11 | Saga 分布式事务治理 | 防止资金/库存不一致 | 中 | 中 |
| **P1** | 12 | 延迟任务调度平台 | 消除 DB 扫描压力 + 防止重启丢失 | 中 | 中 |
| **P1** | 16 | **认证授权体系** | **安全基础 + ADR-023/025 前置依赖** | **中** | **大** |
| **P1** | 17 | **可观测性架构** | **统一观测标准 + SLI/SLO 稳定性运营** | **小~中** | **大** |
| **P1** | 18 | **密钥管理** | **防密钥泄露 + 零信任安全基础** | **中** | **大** |
| **P1** | 28 | ~~开放与集成能力~~ | ✅ **ADR-038 已完成** | — | — |
| **P1** | 29 | ~~订单全生命周期治理~~ | ✅ **ADR-039 已完成** | — | — |
| **P1** | 30 | ~~高性能与高可用架构~~ | ✅ **ADR-040/041 已完成** | — | — |
| **P1** | 31 | ~~支付结算中心（新设计）~~ | ✅ **ADR-042 已完成** | — | — |
| **P1** | 32 | ~~库存管理中心（新设计）~~ | ✅ **ADR-043 已完成** | — | — |
| **P1** | 33 | ~~购物车服务（新设计）~~ | ✅ **ADR-044 已完成** | — | — |
| **P1** | 34 | ~~营销价格服务+优惠叠加规则（新设计）~~ | ✅ **ADR-045 已完成** | — | — |
| **P2** | 35 | ~~会员管理（新设计）~~ | ✅ **ADR-046 已完成** | — | — |
| **P2** | 36 | ~~风控集成（新设计）~~ | ✅ **ADR-047 已完成** | — | — |
| **P2** | 5 | 热数据缓存加速 | 查询 P99 5ms | 低 | 小 |
| **P2** | 6 | 容量规划模型 | 大促保障 | 低 | 小 |
| **P2** | 7 | 多 AZ 缓存预热 | 容灾冷却 5min→30s | 中 | 中 |
| **P2** | 8 | 资金/一致性看板 | 主动发现问题 | 低 | 小 |
| **P2** | 13 | 全链路灰度发布 | 多服务变更全链路验证 + 版本一致性 | 中 | 中 |
| **P2** | 14 | 动态数据脱敏 | 防 PII 越权查看 + 日志零泄露合规 | 中 | 中 |
| **P2** | 15 | 慢 SQL 治理 | 劣质 SQL MR 拦截 + 基线退化告警 + 索引自动化 | 中 | 小 |
| **P2** | 19 | **内部 Gateway 设计** | **防止内部 API 被外部滥用 + 动态路由 + 熔断** | **中** | **中** |
| **P2** | 20 | **全局幂等框架** | **统一幂等语义 + 消除 5 个分散方案** | **中** | **中** |
| **P2** | 21 | **数据归档与生命周期** | **降低存储成本 + 法规合规** | **中** | **中** |
| **P2** | 24 | **成本优化** | **月降成本 20-35%（¥4.6-10 万）** | **低~中** | **小** |
| **P2** | 25 | **DevSecOps 安全左移** | **漏洞 CI 阶段发现 + 等保合规** | **中** | **中** |
| **P3** | 9 | 业务线物理隔离 | 长期稳定性 | 中~高 | 大 |
| **P3** | 10 | 异步任务中心 | 导出不影响在线 | 中 | 中 |
| **P3** | 25 | API 版本 + 外部网关 | 开放平台落地 + API 平滑演进 | 中 | 小 |
| **P3** | 22 | **本地开发环境** | **新成员 onboarding 从天到小时** | **低~中** | **小** |
| **P3** | 23 | **Webhook 系统** | **实时事件通知避免轮询** | **中** | **中** |
| **P3** | 26 | **多租户架构** | **为多平台 SaaS 交付预留扩展点** | **中** | **大** |
| **P3** | 27 | **更多渠道适配器** | **拓展至拼多多/抖音/微信/POS 等渠道** | **中** | **小** |

---

> 本文档可作为架构评审和技术债治理的输入。各优化项在启动实施前需做技术设计评审。
