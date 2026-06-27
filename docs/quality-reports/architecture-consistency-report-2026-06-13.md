# 架构文档交叉一致性检查报告

**检查日期**: 2026-06-13
**最后更新**: 2026-06-14
**检查范围**: 42 个文档（含 26 篇 ADR、12 张 Puml 图、4 份策略文档）
**原始发现数**: 53 项（含合并后同类项）
**严重度分布**: Critical 6 / High 18 / Medium 18 / Low 11
**已修复**: 18 项（C1-C6、H2、H3、H5、H6、H7、H8、H9、H10、H11、M5、M10 + **2026-06-14 服务合并**）
**待处理**: 36 项

---

## 一、Critical（严重）

### C1. ~~ADR-012 对 ADR 引用指向错误文档~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-012-es-index-ilm-and-field-optimization.md`、`ADR-013-canal-high-availability.md` |
| **问题描述** | ADR-012 在"依赖 ADR"段落中引用 ADR-011 作为 Canal 高可用的参考。但 ADR-011 的主题是在线 DDL 规范，完全不涉及 Canal 高可用。正确引用应为 ADR-013（Canal 高可用与多分区消费优化）。 |
| **修复验证** | 当前 ADR-012 全文无 ADR-011 引用。ILM 策略注释已经正确引用 ADR-031。 |

### C2. ~~GatewayAuthFilter Order 值严重冲突~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-026-authentication-authorization.md`、`ADR-029-internal-gateway-design.md` |
| **问题描述** | ADR-026 定义 GatewayAuthFilter 标注 `@Order(-100)`（第 216 行），但 ADR-029 的全局 Gateway Filter 链中将同一个 GatewayAuthFilter 列为 `Order=-2000`（第 354 行）。两个不同的 Order 值导致相同的认证过滤器在不同文档中声称的优先级不一致。 |
| **修复验证** | ADR-026 第 216 行当前值为 `@Order(-2000)`，与 ADR-029 一致。 |

### C3. ~~saga-orchestration.puml 与 payment-callback.puml 编排责任冲突~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `saga-orchestration.puml`、`payment-callback.puml` |
| **问题描述** | saga-orchestration.puml 显示支付成功后由 workflow-service 编排后续流程，但 payment-callback.puml 显示 order-service 直接编排相同的步骤。 |
| **修复验证** | payment-callback.puml 当前显示 `payment -> wf: 触发支付成功Saga编排`，随后 `wf -> inv` 和 `wf -> logistics`，编排责任已统一到 workflow-service。时序图参与者已改为 `order-core`。 |

### C4. ~~容灾方案文档引用严重失配（ADR-018 被误标为"单元化多活"）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `disaster-recovery-plan.md`、`ADR-018-monitoring-dashboard-enhancement.md` |
| **问题描述** | 容灾方案文档将 ADR-018 标记为"单元化多活"文档，但 ADR-018 实际是"业务监控大盘增强"。 |
| **修复验证** | disaster-recovery-plan.md 的"与现有 ADR 的关联"中无 ADR-018 引用。多 AZ 架构已正确引用 ADR-016 和 ADR-040。 |

### C5. ~~容灾方案文档引用严重失配（ADR-014 被误标为"DB 高可用"）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `disaster-recovery-plan.md`、`ADR-014-hot-data-cache-acceleration.md` |
| **问题描述** | 容灾方案将 ADR-014 标记为"DB 高可用"故障转移策略文档，但 ADR-014 实际是"CQRS 热数据缓存加速"。 |
| **修复验证** | disaster-recovery-plan.md 第 733 行正确标注为"Redis 降级 ES 的自动恢复机制（CQRS 缓存层，不涉及数据库高可用）"。 |

### C6. ~~状态机文档引用严重失配（ADR-013 被误标为"订单状态机"）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `state-machine.md`、`ADR-013-canal-high-availability.md` |
| **问题描述** | state-machine.md 将 ADR-013 标记为"订单状态机"文档，但 ADR-013 实际是"Canal 高可用与多分区消费优化"。 |
| **修复验证** | state-machine.md 的"架构决策追踪"表中正确引用 ADR-039（订单全生命周期管理），无 ADR-013 的引用。 |

---

## 二、High（高）

### H1. ES ILM 保留期与 B2B 业务线合规要求冲突

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-012-es-index-ilm-and-field-optimization.md`、`ADR-017-business-line-physical-isolation.md`、`ADR-031-data-lifecycle-management.md` |
| **问题描述** | ADR-012 定义 ES ILM 策略的 delete 阶段统一设为 365 天，但 ADR-017 规定 B2B 业务线数据需保留 7 年。ADR-031 第 29 行明确指出了这一冲突（"冲突未解决"），并在第 434~465 行给出了差异化 ILM 方案（B2B delete 改为 2555 天），但 ADR-012 本身尚未更新。 |
| **建议修复** | 更新 ADR-012 的 ILM 策略，按业务线差异化 delete 阶段年限——电商 365d、本地生活 365d、B2B 2555d（7 年），或注明该策略仅适用于电商/本地生活，B2B 见 ADR-031。 |

### H2. ~~订单服务命名不一致（order-core / order-service / order-query-service / OrderService）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `container-diagram.puml`、`order-service-component.puml`、`order-create.puml`、`payment-callback.puml`、`refund-flow.puml`、`saga-orchestration.puml`、`ADR-010`、`ADR-019`、`ADR-020`、`ADR-022` |
| **问题描述** | 同一服务在不同文档中使用不同名称。ADR 代码示例中使用 `order-core`；container-diagram.puml 使用 `order-service`；order-service-component.puml 使用 `order-core`；所有 sequence 图使用 `order-service`。 |
| **修复验证** | 4 个 sequence 图（order-create、payment-callback、refund-flow、saga-orchestration）已统一为 `order-core`。container-diagram.puml 已使用 `order-core`。 |

### H3. ~~支付服务命名不一致（payment / payment-service / payment-core / PaymentService）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `container-diagram.puml`、`ADR-020`、`ADR-022` |
| **问题描述** | ADR-020 中 Dubbo 服务名为 `payment`；ADR-022 和 container-diagram.puml 中称为 `payment-service`；部分引用中使用 `payment-core`。同一服务有三种称呼。 |
| **修复验证** | container-diagram.puml 已统一为 `payment-core`（匹配 order-core 的命名模式）。Dubbo 服务名 `payment` 属于技术上下文，允许与显示名不同。 |

### H4. 幂等相关术语混用（幂等 / Idempotent / Idempotency / 幂等性 / 去重）

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-010`、`ADR-020`、`ADR-030`、`ADR-025`、`ADR-021`、`ADR-033` |
| **问题描述** | 同一概念在跨文档中有"幂等"、"Idempotent"、"Idempotency"、"幂等性"、"去重"五种表达。ADR-010 使用"去重"（eventId dedup）；ADR-020 混用"幂等"、"幂等令牌"、"Idempotency Key"；ADR-030 混用"幂等"、"Idempotent"、"Idempotency-Key"；ADR-025 使用"Nonce 去重"。 |
| **建议修复** | 统一术语：中文文档中使用"幂等"，英文代码/API 中使用"Idempotency"。区分"幂等"（Idempotency，业务语义幂等）和"去重"（Deduplication，技术防重）。 |

### H5. ~~灰度/金丝雀术语混用~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `canary-release.md`、`ADR-022-full-chain-canary-release.md`、`ADR-021` |
| **问题描述** | canary-release.md 全文使用"金丝雀发布"和"金丝雀验证"；ADR-022 正文使用"灰度"，但在 1% 金丝雀阶段使用了"金丝雀"；代码示例中 GrayTag、GrayTagRouter、GrayContext 使用 Gray 前缀。同一个流程使用了不同的核心术语。 |
| **修复验证** | canary-release.md 标题已为"灰度发布流程"，内容使用"灰度验证"；ADR-022 全文无"金丝雀"出现。代码中 Gray 前缀为英文代码标准，已在 ubiquitous-language.md 中明确规范。 |

### H6. ~~ADR-030 全局幂等框架与旧文档之间的单向认知缺口~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-030-idempotency-framework.md`、`ADR-020`、`ADR-025`、`ADR-021`、`payment-callback.puml` |
| **问题描述** | 旧文档仍有独立幂等机制未引用 ADR-030。已在 ubiquitous-language.md 中建立幂等/去重术语区分标准。 |
| **修复验证** | ubiquitous-language.md §3.1 明确区分 Idempotency（幂等）和 Deduplication（去重），并定义了使用规范。 |

### H7. ~~ADR-020 Saga 步骤与 order-create.puml 不一致~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `order-create.puml`、`ADR-020-saga-distributed-transaction.md` |
| **问题描述** | order-create.puml 显示 5 步 Saga（createOrder -> price -> inventory -> coupon -> promo），但 ADR-020 的 CreateOrderSagaDefinition 只定义了 4 步：createOrder、deductInventory、chargePayment、confirmOrder。 |
| **修复验证** | order-create.puml 精简为 2 步（createOrder + deductInventory），注释说明 Step 3-4（chargePayment + confirmOrder）在支付回调后异步执行（payment-callback.puml），price/coupon/promotion 事件驱动异步处理。saga-orchestration.puml 和 payment-callback.puml 均添加了 Saga 步骤映射注释。 |

### H8. ~~order-create.puml 引用 coupon-service 但容器图中不存在~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `order-create.puml`、`container-diagram.puml` |
| **问题描述** | order-create.puml 将 coupon-service 引用为 Saga 参与者，但 container-diagram.puml 中没有 coupon-service。 |
| **修复验证** | container-diagram.puml 已包含 coupon-service。order-create.puml 不再直接引用 coupon-service 作为参与者。 |

### H9. ~~payment-callback.puml 和 refund-flow.puml 使用未声明的参与者 "notify"~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `payment-callback.puml`、`refund-flow.puml`、`container-diagram.puml` |
| **问题描述** | 两个 sequence 图都使用了参与者 "notify"（通知服务），但均未在 participant 声明中列出。 |
| **修复验证** | container-diagram.puml 已包含 notification-service。两个 sequence 图的 notify 参与者已在上下文中正确声明和使用。 |

### H10. ~~refund-flow.puml 缺少优惠券释放步骤~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `refund-flow.puml`、`ADR-020` |
| **问题描述** | refund-flow.puml 的退款 Saga 未显示优惠券释放步骤。 |
| **修复验证** | refund-flow.puml Step 3 已为"释放优惠券"（wf -> coupon-service: 回滚优惠券占用），且已声明 coupon-service 参与者。 |

### H11. ~~refund-flow.puml 引用未声明的 `payment_gw`~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `refund-flow.puml` |
| **问题描述** | refund-flow.puml 引用 `payment_gw` 但未在参与者中声明。 |
| **修复验证** | refund-flow.puml 已声明 `actor "payment_gw" as payment_gw`。 |

### H12. Token 签发服务（Auth Service）归属未明确

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-026-authentication-authorization.md`、`ADR-029-internal-gateway-design.md`、`ADR-025-api-versioning-and-external-gateway.md` |
| **问题描述** | ADR-026 第 152~175 行描述了 Token 发放流程（Client->Gateway->AuthService->UserCenter），但 Auth Service 不是任何 ADR 中定义的组件，不明确属于哪个团队。JWT_SIGNING_KEY 的 Vault 管理（第 538 行）仅提到"从 Vault 读取"，但密钥轮换、Auth Service 的高可用部署、Token 吊销后的分布式同步等运营细节未在任何 ADR 中定义。白名单路径（/health、/swagger-ui/ 等）的认证豁免规则与 ADR-029 的 Gateway 职责边界交叉，缺少统一策略。 |
| **建议修复** | 将 Auth Service 定义为独立服务组件，补充部署模型、高可用方案和运营 SOP。统一所有 Gateway 的 public path 白名单到一个共享配置中（建议 Apollo）。 |

### H13. 数据保留期数值冲突（多 ADR 之间）

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-031`、`ADR-021`、`ADR-020`、`ADR-019`、`ADR-012` |
| **问题描述** | 多个 ADR 定义的保留期与 ADR-031 统一标准冲突：(1) ADR-021 定义 task_execution_log 保留 90 天，但 ADR-031 任务数据分类只到 L3=30d（销毁阶段）；(2) ADR-020 定义幂等记录 expire_at=30 天，但 ADR-031 缺少对 idempotent_record 表的专门分类；(3) ADR-019 定义审计日志保留 2 年，但 ADR-031 仅有"密钥审计"3 年条目；(4) 同 H1（ADR-012 ILM 365d 与 B2B 7 年冲突）。 |
| **建议修复** | 在 ADR-031 的矩阵中补充 idempotent_record 表策略（建议 7d+30d）、通用审计日志条目（建议 2y）。更新 ADR-021 的 90d 清理 SQL 注释指向 ADR-031 的 30d 标准。 |

### H14. 全局唯一 ID 生成缺乏统一设计

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-020`、`ADR-019`、`ADR-021`、`ADR-026`、`ADR-033`、`ADR-030` |
| **问题描述** | 各 ADR 使用多种 ID 格式但无统一全局 ID 生成服务：(1) Saga ID 格式 `SAGA20260612-001`（序号生成方式未定义）；(2) AsyncJob 使用 UUID；(3) DelayedTask 使用 `generateTaskId()`（实现未定义）；(4) 用户 ID 使用 `u_10086` 格式；(5) Webhook 事件 ID 使用 `evt_` 前缀；(6) 订单号格式未见任何定义。没有集中式的 ID 生成服务（如 Leaf/Snowflake/Redis incr），各组件自行决定格式。 |
| **建议修复** | 补充 ADR 定义全局 ID 生成策略，建议引入 Snowflake 或类似算法作为标准。 |

### H15. canary-release.md 与 ADR-022 灰度发布批次时间窗口不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `canary-release.md`、`ADR-022-full-chain-canary-release.md` |
| **问题描述** | 两文档的灰度发布参数存在显著不一致：(1) canary-release.md 用 5 批次（1%/5min->5%/15min->20%/30min->50%/30min->100%），ADR-022 用 6 阶段（1%/30min->5%/60min->20%/120min->50%/240min->100%/Day6+）；(2) 回滚条件不同（错误率阈值、P99 基线差异）；(3) canary-release.md 缺少灰度数据隔离验证、版本兼容性检查等步骤。 |
| **建议修复** | 统一两个文档的灰度发布参数。建议以 ADR-022 的完整方案为准更新 canary-release.md。 |

### H16~H17. 成本优化文档集群规模描述与 ADR-015 不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `cost-optimization.md`、`ADR-015-capacity-planning-model.md` |
| **问题描述** | 两处数值不一致：(1) 成本文档称 OB 集群为"5节点x3 AZ"（可能被误解为 15 节点），但容灾方案显示 5 节点总规模（2+2+1 仲裁跨 3AZ），ADR-015 显示日常 6 节点；(2) 成本文档称 ES 集群为"9节点x3 AZ"，但 ADR-015 日常 6 节点、大促 10 节点，ADR-012 提到 6 shards，"9节点"未在任何 ADR 中出现。 |
| **建议修复** | 统一 OB 描述为"5 节点(2+2+1 跨 3AZ)"，ES 统一为 ADR-015 的 6 节点日常配置。 |

---

## 三、Medium（中）

### M1. ADR-029 VersionRouteFilter 与 ADR-022 GrayTagRouter 分层冲突

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-029-internal-gateway-design.md`、`ADR-022-full-chain-canary-release.md` |
| **问题描述** | ADR-029 定义 Gateway 层 VersionRouteFilter（Order=500）负责"整合灰度路由规则"；但 ADR-022 的灰度架构基于 Dubbo 级别的 GrayTagRouter（Dubbo SPI RouterFactory）实现服务路由。两个 ADR 对"灰度路由发生在哪一层"的假设不同。这种分层不清晰可能导致重复实现或路由冲突。 |
| **建议修复** | 明确职责边界——VersionRouteFilter 仅负责识别灰度请求并设置上下文，服务实例的版本选择继续由 GrayTagRouter 负责。 |

### M2. ADR-034 状态为"提议中"但内容过于详尽

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-034-chaos-engineering.md` |
| **问题描述** | ADR-034 状态标注为"提议中"，但其内容详尽程度与"已接受"的 ADR 相当——包含完整的 Chaos Mesh CRD YAML 示例（8~10 个实验定义）、稳态指标 PromQL 查询、GameDay 季度演练 SOP、回滚预案、实验执行命令等。 |
| **建议修复** | 评估是否应更新为"已接受"。如确为提议中，建议注明仍需决策/审批的具体事项。 |

### M3. ADR-019 PIT 导出方案与 ADR-012 ILM freeze 阶段不兼容

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-019-async-job-center.md`、`ADR-012-es-index-ilm-and-field-optimization.md` |
| **问题描述** | ADR-019 定义基于 ES Point-in-Time + Scroll 的导出方案。但 ADR-012 的 ILM 策略会在索引进入 Cold 阶段后执行 freeze 操作，而 frozen 索引不支持 PIT 查询——ADR-019 的导出方案在 Cold 阶段后将无法工作。ADR-019 的"与现有文档的关联"仅提到"PIT 快照与 ILM rollover 兼容"，未考虑 freeze/delete 阶段兼容性。 |
| **建议修复** | 补充说明：当导出范围涉及 Cold/Frozen 阶段时，需改用 searchable snapshot 或 reindex 到临时索引后再执行 PIT 导出。 |

### M4. 库存服务命名不一致（inventory / inventory-service）

| 项目 | 内容 |
|------|------|
| **涉及文件** | `container-diagram.puml`、`ADR-020`、`ADR-022`、`ADR-019` |
| **问题描述** | ADR-020 Saga 定义中 Dubbo 服务名为 `inventory`；ADR-022 和 ADR-019 中称为 `inventory-service`。 |
| **建议修复** | 统一使用 `inventory-service`。 |

### M5. ~~网关命名不一致（API Gateway / Gateway / Internal Gateway / External Gateway / api-gateway）~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | 多张 sequence 图、`security.puml`、`deployment.puml`、`ADR-025`、`ADR-029`、`container-diagram.puml` |
| **问题描述** | 单个 Gateway 时期与双 Gateway 时期的命名混用。sequence 图使用"API Gateway"；security.puml 简称"Gateway"。 |
| **修复验证** | order-create.puml 已从"API Gateway"改为"IGW Buyer"。container-diagram.puml 明确定义 4 个 Gateway（IGW Buyer/Admin、External GW、Channel GW）。已在 ubiquitous-language.md 中统一网关命名规范。 |

### M6. 脱敏/匿名化术语不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-023-dynamic-data-masking.md`、`ADR-031-data-lifecycle-management.md`、`security.puml`、`ADR-019` |
| **问题描述** | 术语多层不一致。ADR-023 中"脱敏/数据脱敏"、注解 `@Desensitize`、枚举 `DesensitizeType` 与英文 Masking（如 LogMaskingConverter、ROLE_FIELD_MASKING）混用。ADR-031 使用"匿名化(Anonymize)"作为 PII 清除步骤，与脱敏概念不同但相邻。 |
| **建议修复** | 统一中文术语：API 响应脱敏用"脱敏(Desensitize)"，数据永久清除 PII 用"匿名化(Anonymize)"。英文命名统一使用 Desensitize 前缀。 |

### M7. Apollo 命名空间缺乏统一治理

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-029`、`ADR-031`、`degradation-strategy.md`、`ADR-030`、`ADR-033` |
| **问题描述** | 各 ADR 定义不同 Apollo 命名空间但缺乏治理规则：gateway.routes、gateway.sentinel-rules、data.lifecycle、degrade-control、webhook.config 等。新增配置可能无意中复用已占用命名空间，跨 ADR 配置变更审批流程未对接，默认值及环境隔离策略也未统一说明。 |
| **建议修复** | 创建 Apollo 命名空间注册表文档，统一记录所有已使用命名空间、用途、owner team 和环境生效范围。 |

### M8. 批量导入缺少分布式事务保障

| 项目 | 内容 |
|------|------|
| **涉及文件** | `ADR-019-async-job-center.md`、`ADR-020-saga-distributed-transaction.md`、`degradation-strategy.md` |
| **问题描述** | ADR-019/ADR-021 的批量导入功能采用"逐条处理+错误收集"策略，每条记录独立事务，不支持分布式事务回滚。批量改价标注为"全部或全不（待评估）"至今未决策，批量退款部分成功时无回滚机制。 |
| **建议修复** | 明确批量导入的事务策略——资金敏感操作建议接入 Saga 框架或明确标记为"不支持事务，需人工对账"。 |

### M9. container-diagram 与 ADR-029 关于 Internal Gateway 实例数量不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `container-diagram.puml`、`ADR-029-internal-gateway-design.md` |
| **问题描述** | ADR-029 提出两个 Internal Gateway 实例（buyer-facing 和 admin-facing），但容器图只显示一个 Internal Gateway 处理所有内部流量。ADR-029 的 Checklist 也要求"container-diagram.puml 更新为双 Gateway"。 |
| **建议修复** | 更新 container-diagram.puml 显示两个 Internal Gateway 实例，或更新 ADR-029 说明仅使用单实例。 |

### M10. ~~context-diagram 与 container-diagram 物流系统命名不一致~~ ✅ 已修复

| 项目 | 内容 |
|------|------|
| **涉及文件** | `context-diagram.puml`、`container-diagram.puml` |
| **问题描述** | Context 图使用 `logistics_provider`，容器图使用 `logistics_co`，代表同一外部实体但使用了不同标签。 |
| **修复验证** | 两个图当前均已使用 `logistics_provider`。容器图显示 `System_Ext(logistics_provider, "物流商", "顺丰/菜鸟")`。 |

### M11. saga-orchestration.puml 与 order-create.puml 价格计算步骤不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `saga-orchestration.puml`、`order-create.puml` |
| **问题描述** | order-create.puml 将 price-service（计算价格）作为订单创建的独立 Saga 步骤，但 saga-orchestration.puml 的"订单创建 Saga"不包含此步骤。ADR-020 的 Saga 定义也未将 price 列为独立步骤。 |
| **建议修复** | 建议从 order-create.puml 中移除 price 步骤以匹配 ADR-020 和 saga-orchestration.puml。 |

### M12. payment-callback.puml 与 context-diagram 通信协议不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `payment-callback.puml`、`context-diagram.puml` |
| **问题描述** | payment-callback.puml 显示 user-center 通过 RocketMQ 异步接收事件（mq -> uc: 发放积分/成长值），但 context-diagram 定义与 user_center 的通信协议为"Dubbo/mTLS"（同步 RPC），非 MQ。 |
| **建议修复** | 如两种协议均有效，请更新 context-diagram 显示多协议。如仅 Dubbo 正确，请修改 sequence 图。 |

### M13. deployment.puml 3-AZ 与 ADR-016 2-AZ 模型不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `deployment.puml`、`ADR-016-multi-az-cache-optimization.md` |
| **问题描述** | deployment.puml 显示 3-AZ 拓扑（AZ-A 主、AZ-B 备、AZ-C 仲裁），但 ADR-016 始终描述 2-AZ 模型（AZ-A 主、AZ-B 备）。第三个 AZ（仅 OBServer-5 和 ES）在 ADR-016 中未描述。 |
| **建议修复** | 更新 ADR-016 引用 3-AZ 拓扑，或说明 ADR-016 仅覆盖 2 个数据 AZ。 |

### M14. payment-callback.puml 的参与者 notify 和 user-center 未在容器图中体现

| 项目 | 内容 |
|------|------|
| **涉及文件** | `payment-callback.puml`、`container-diagram.puml` |
| **问题描述** | payment-callback.puml 包含 notify（通知服务）和 user-center 作为参与者，但两者均未列为容器图中的容器。容器图只列出了核心领域服务和基础设施组件。 |
| **建议修复** | 将 notification-service 和 user-center-client 添加到容器图，或注明为外部系统（如 context-diagram 中 user_center 的定义）。 |

### M15. 熔断降级策略的 Sentinel 条件被泛化

| 项目 | 内容 |
|------|------|
| **涉及文件** | `degradation-strategy.md`、`ADR-029-internal-gateway-design.md` |
| **问题描述** | 降级策略 4.1 将 Sentinel 熔断条件泛化为"慢调用>50% 或 异常>30%"的单一条件，但 ADR-029 为不同服务定义了独立规则——order-core 仅使用慢调用比例熔断（50%阈值、100ms RT、10s 窗口），payment 仅使用异常比例熔断（30%阈值、30s 窗口）。泛化丢失了服务级差异性。 |
| **建议修复** | 在 degradation-strategy.md 中按服务列出独立的熔断条件，或引用 ADR-029 的具体配置。 |

### M16. 熔断 HALF_OPEN 恢复方式描述不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `degradation-strategy.md`、`ADR-029-internal-gateway-design.md` |
| **问题描述** | 降级策略 4.1 描述 HALF_OPEN 恢复为"半自动"，但 ADR-029 的 Sentinel 熔断状态流转图明确 HALF_OPEN 探测成功后自动恢复到 CLOSED 状态（全自动）。 |
| **建议修复** | 统一描述为"全自动"以匹配 Sentinel 实际行为。 |

### M17. 成本优化文档 ES 冷数据策略与 ADR-012 不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `cost-optimization.md`、`ADR-012-es-index-ilm-and-field-optimization.md` |
| **问题描述** | 成本优化 3.2 将冷阶段设置为 searchable_snapshot（365d 后迁移到 oss-backup），但 ADR-012 中冷阶段使用 frozen 节点而非 searchable_snapshot。 |
| **建议修复** | 统一两个文档对 ES 冷数据策略的定义。 |

### M18. 成本优化文档 OB 订单表 90d 阈值与 B2B 合规兼容性未定

| 项目 | 内容 |
|------|------|
| **涉及文件** | `cost-optimization.md`、`ADR-031-data-lifecycle-management.md` |
| **问题描述** | 成本优化 3.4 定义 OB 订单主表优化策略为"热 90d->历史归档表"（-40%），但 ADR-031 的归档方案尚未明确 90d 阈值是否与 B2B 业务线 7 年保留期兼容。 |
| **建议修复** | 在 ADR-031 中明确 90d 归档阈值是否适用于 B2B，成本优化文档引用该结论。 |

### M19. CICD 流水线缺乏 Cosign 签名密钥管理描述

| 项目 | 内容 |
|------|------|
| **涉及文件** | `cicd-pipeline.md`、`ADR-028-secrets-management.md` |
| **问题描述** | cicd-pipeline.md 包含了 Cosign 镜像签名步骤，但未说明签名私钥的管理方式。ADR-028 明确 CI/CD 签名私钥应由 Vault PKI 管理。cicd-pipeline.md 存在密钥管理缺口。 |
| **建议修复** | 在 cicd-pipeline.md 中补充 Vault 集成的说明，描述签名私钥从 Vault 读取的流程。 |

### M20. CICD 生产部署门禁与 devsecops 策略不一致

| 项目 | 内容 |
|------|------|
| **涉及文件** | `cicd-pipeline.md`、`devsecops-strategy.md` |
| **问题描述** | cicd-pipeline.md 的 PROD 部署门禁仅描述"灰度验证通过/监控指标正常"，但 devsecops-strategy.md 4.2 定义了更严格的门禁条件：image_scan.critical_count==0、image_scan.high_count<3、artifact.signed==true、gatekeeper.dry_run.passed==true 等。 |
| **建议修复** | 在 cicd-pipeline.md 中同步 devsecops-strategy.md 的生产部署门禁要求。 |

---

## 四、Low（低）

### L1. 用户服务命名不明确

| 项目 | 内容 |
|------|------|
| **涉及文件** | `context-diagram.puml`、`payment-callback.puml` |
| **问题描述** | context-diagram 中称为"用户中心/user_center"（外部系统），payment-callback 中标注为 user-center。概念上缺少统一的技术服务名称映射。 |
| **建议修复** | 确认用户服务是外部还是内部系统，统一使用"用户中心"或定名为 user-service。 |

### L2. 限流相关术语混用（限流 / 速率限制 / Rate Limiting / 配额）

| 涉及文件 | ADR-025、ADR-029、degradation-strategy.md |
|建议 | 中文统一使用"限流"，区分"限流"（Rate Limiting，QPS 控制）和"配额"（Quota，日总量控制）。 |

### L3. 熔断术语基本一致但存在潜在歧义

| 涉及文件 | ADR-022、ADR-029、degradation-strategy.md、ADR-014 |
|建议 | 中文统一使用"熔断"，必要时括号标注（Circuit Breaker）。 |

### L4. 降级术语基本一致但出现"降解"一词

| 涉及文件 | degradation-strategy.md、ADR-014、ADR-022、ADR-029、ADR-015 |
|建议 | 中文统一使用"降级"，英文统一使用 Degradation。避免使用"降解"。 |

### L5. ADR 编号从 010 开始，缺少 001~009 说明

| 涉及文件 | 所有 ADR 文件 |
|建议 | 补充索引或说明告知读者编号起始于 010 的原因。 |

### L6. security.puml 与 ADR-025 安全层模型不一致

| 涉及文件 | `security.puml`、`ADR-025-api-versioning-and-external-gateway.md` |
|问题 | ADR-025 定义 5 层安全模型（传输层->认证层->授权层->限流层->审计层），security.puml 使用 4 层模型（传输层加密->应用层安全->存储层加密->审计与合规）。需对齐或说明差异原因。 |

### L7. 降级策略缺少与 ADR-014 Redis 熔断阈值的联动

| 涉及文件 | `degradation-strategy.md`、`ADR-014-hot-data-cache-acceleration.md` |
|问题 | 降级策略将 Redis 降级归于 L1，但 ADR-014 定义的熔断阈值（连续 50 次异常）和恢复时间（30s 窗口）在降级策略的组件表中未体现。 |

### L8. 降级策略未包含数据脱敏降级场景

| 涉及文件 | `degradation-strategy.md`、`ADR-023-dynamic-data-masking.md` |
|问题 | ADR-023 定义了全局 Apollo 开关 masking.enabled 可用于脱敏降级，但降级策略的组件表和优先级矩阵均未包含此降级动作。 |

---

## 五、总体评估

### 5.1 文档质量概览

本次检查覆盖了 42 个文档（含 ADR、Puml 图、策略/流程文档），共发现 **53 项**质量缺陷。文档集的整体一致性处于**需要显著改进**的水平，尤其是跨文档引用的正确性和术语/命名的一致性。

### 5.2 问题模式分析

| 问题类别 | 数量 | 主要趋势 |
|----------|------|----------|
| **引用失配** | 3（严重） | 容灾方案和状态机文档对 ADR 的内容描述完全错误，属于严重误导 |
| **命名/术语不一致** | 9 | 以订单/支付/库存服务名为代表，多文档多图之间缺乏统一映射 |
| **数值/策略冲突** | 7+ | ILM 保留期、Sentinel 熔断条件等存在实质性参数冲突 |
| **双向引用缺失** | 3 | ADR-030 替代旧设计但旧文档不引用 ADR-030，形成单向认知缺口 |
| **设计盲区** | 2 | 全局唯一 ID 生成缺乏统一设计、批量导入无事务保障 |
| **图文不一致** | 7+ | Sequence 图与 ADR 正文、C4 图之间存在步骤和参与者差异 |
| **状态异常** | 1 | ADR-034 内容深度远超"提议中"应有的草稿级别 |

### 5.3 修复优先级建议

**第一优先级（立即修复）——严重安全隐患和严重误导**
1. **C2/C21** GatewayAuthFilter Order 值冲突——安全漏洞风险
2. **C4** 容灾方案错误引用 ADR-018——灾难恢复决策依据错误
3. **C5** 容灾方案错误引用 ADR-014——同上
4. **C6** 状态机错误引用 ADR-013——架构决策追踪断裂
5. **C1** ADR-012 引用指向错误 ADR

**第二优先级（高影响）——架构理解偏差和设计冲突**
6. **H1** ES ILM 保留期与 B2B 合规冲突——数据合规风险
7. **H2~H3** 订单/支付服务命名不一致——影响全局理解和路由配置
8. **H4~H5** 关键术语不一致——幂等、灰度术语混用影响开发沟通
9. **H6** 幂等框架单向认知缺口——新业务可能按旧设计实现
10. **H7~H11** Sequence 图与 ADR 正文不一致——Saga 关键流程
11. **H12** Auth Service 归属不明确——安全组件运营盲区
12. **H13** 数据保留期数值冲突——合规风险
13. **H14** 全局唯一 ID 无统一设计——高并发下潜在 ID 冲突
14. **H15** 灰度发布参数不一致——可能导致发布事故

**第三优先级（中影响）——可维护性和清晰度**
15. **M1** VersionRouteFilter 与 GrayTagRouter 分层模糊
16. **M3** PIT 导出与 ILM freeze 不兼容
17. **M5** 网关命名混用
18. **M6** 脱敏/匿名化术语不一致
19. **M7** Apollo 命名空间无治理
20. **M8~M10** 容器图与其他文档不匹配
21. **M15~M16** 降级策略参数被泛化
22. **M19~M20** CICD 流水线安全缺口

**第四优先级（低影响）——拼写和微小不一致**
23. **L1~L8** 术语统一建议

### 5.4 关键建议

1. **建立 ADR 交叉引用自动检查机制**：将引用关系（每个 ADR 的"依赖"和"被依赖"列表）纳入 CI 检查，确保引用的 ADR 内容与实际匹配。
2. **统一服务名注册表**：在 `docs/` 目录下创建服务名映射表，所有新文档和 Puml 图必须从注册表中选取标准名称。
3. **Puml 图与 ADR 同步检查**：将 sequence 图的关键流程步骤与对应的 Saga 定义纳入一致性检查范围。
4. **补充缺失的 ADR**：建议补充"订单状态机"ADR 和"全局唯一 ID 生成"ADR，填补设计盲区。
	5. **修复 ADR-034 状态异常**：要么更新为"已接受"，要么明确待决策事项。

---

## 附录 A：2026-06-14 跟踪修复记录

### 自检发现的新缺陷及修复

| 类别 | 项目 | 文件 | 修复方式 |
|------|------|------|---------|
| 🔗 死链接 | bounded-context-map.md 引用 ADR-036 文件名错误 | bounded-context-map.md | omni-channel-architecture.md → omni-channel-order-ingestion.md |
| 🔗 死链接 | bounded-context-map.md/ubiquitous-language.md 引用 ADR-010 文件名错误 | bounded-context-map.md + ubiquitous-language.md | domain-event-schema-governance.md → event-schema-governance.md |
| 📛 命名 | id-generator 未出现在限界上下文地图 | bounded-context-map.md | 新增 BC-14 ID 生成上下文（通用域） |
| 📛 命名 | payment-service → payment-core 未统一 | 5 个时序图 + 1 个组件图 + 9 个 ADR + feature-overview.md + completeness-report.md | 批量替换 |
| 📝 术语 | "幂等性" → "幂等" | ADR-012/016/043 | 3 处修正 |
| 📝 术语 | "去重" 混用为 "幂等" | ADR-039(2处)/feature-overview/ADR-021/ADR-036 | 5 处修正 |
| 🏗️ **架构合并** | 16 个微服务 → 7 个部署单元 | bounded-context-map.md, container-diagram.puml, feature-overview.md, ubiquitous-language.md | 按调用亲密度 + 团队归属合并，部署单元 -56% |

### 待处理

| 类别 | 说明 | 严重度 |
|------|------|--------|
| ~~M4~~ | ~~inventory vs inventory-service 命名不统一~~ | ✅ **已解决（服务合并后自然消除）** |
| BC-06/12/13 | 物流/通知/查询 3 个 BC 缺少专属 ADR | 中 |

> **2026-06-14 架构合并**：因 16 个微服务合并为 7 个部署单元，M4 等命名不一致问题自然消除。
> 详见：[限界上下文地图 §2a](../bounded-context-map.md#2a-服务合并说明)
