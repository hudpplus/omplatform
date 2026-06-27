# 订单中台架构图集

> 本目录包含订单中台系统的全套架构设计图，采用 **PlantUML + Mermaid 混合方案**。

## 图集索引

### 🏗️ C4 模型（PlantUML）

| 级别 | 文件 | 说明 | 渲染方式 |
|------|------|------|---------|
| L1 | [系统上下文图](c4/context-diagram.puml) | 订单中台与外部角色/系统的关系 | `plantuml -tsvg` |
| L2 | [容器图](c4/container-diagram.puml) | 服务 + 中间件部署与交互 | `plantuml -tsvg` |
| L3 | [组件图 - order-core](c4/order-service-component.puml) | 订单核心服务内部架构 | `plantuml -tsvg` |

### 🔄 时序图（PlantUML）

| 文件 | 说明 | 核心关注点 |
|------|------|-----------|
| [订单创建流程](sequence/order-create.puml) | 买家下单到订单创建完成 | Saga 正向流程 + 补偿 |
| [支付回调流程](sequence/payment-callback.puml) | 支付网关回调处理 | 幂等校验 + 库存扣减 |
| [售后退款流程](sequence/refund-flow.puml) | 退款申请到退款完成 | 自动/人工审核 + 补偿 |
| [Saga 全景编排](sequence/saga-orchestration.puml) | 分布式事务编排全景 | 正向 + 补偿 + 超时重试 |

### 🖥️ 部署架构（PlantUML）

| 文件 | 说明 |
|------|------|
| [同城三中心部署图](deployment.puml) | AZ-A/B/C 三可用区部署，Paxos 2-2-1 副本策略 |

### 🔒 安全架构（PlantUML）

| 文件 | 说明 |
|------|------|
| [安全四层防御](security.puml) | 传输层 → 应用层 → 存储层 → 审计合规 |

### 📊 流程类图（Mermaid）

| 文件 | 说明 | 自动渲染平台 |
|------|------|-------------|
| [订单状态机](state-machine.md) | 订单全生命周期状态迁移 | GitHub / GitLab |
| [CI/CD 流水线](cicd-pipeline.md) | 从代码提交到生产部署 | GitHub / GitLab |
| [灰度发布流程](canary-release.md) | 金丝雀 + 分批放量 + 回滚 | GitHub / GitLab |

## 渲染方式

### PlantUML

```bash
# 安装 PlantUML（需要 Java + Graphviz）
# IDEA 插件：PlantUML Integration
# VS Code 插件：PlantUML

# CLI 渲染
plantuml -tsvg docs/diagrams/c4/context-diagram.puml
plantuml -tpng docs/diagrams/sequence/order-create.puml

# 批量渲染所有 .puml 文件
plantuml -tsvg "docs/diagrams/**/*.puml"
```

### Mermaid

Mermaid 文件（`.md`）在 GitHub/GitLab 上自动渲染为图表。本地查看可使用：

- VS Code 插件：Markdown Preview Mermaid Support
- IDEA 插件：Markdown + Mermaid

## 绘图规范

1. **C4 模型**使用 `C4-PlantUML` 标准库（`!include` 方式引入）
2. **时序图**统一使用 PlantUML `sequence` 图类型
3. **部署/安全图**使用 PlantUML `cloud`/`component`/`package` 图类型
4. **流程类图**使用 Mermaid（Git 平台原生支持，无需额外渲染）
5. 所有 `.puml` 文件应能独立渲染（自包含依赖）
6. 同色系、同字体风格（`skinparam` 全局配置）
