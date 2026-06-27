# ADR-011：在线 DDL 治理与零停机迁移规范

## 状态

已接受

## 背景

订单中台基于 OceanBase 4.x（MySQL 兼容模式）构建，日订单百万级，核心 `order` 表数据量已达数亿行。随着业务迭代，数据库 Schema 变更（DDL）不可避免，但 OceanBase 的 DDL 行为与 MySQL 存在显著差异，未受控的 DDL 变更曾多次导致线上故障。

### 已知故障案例

**案例 1：ALTER TABLE 误以为秒级完成，实际触发了全表重建**

```sql
-- 意图：将备注字段从 VARCHAR(100) 改为 VARCHAR(500)
ALTER TABLE `order` MODIFY COLUMN `buyer_remark` VARCHAR(500);
```

在 MySQL 中，`VARCHAR` 长度从 100 扩到 500 属于 **快速 DDL**（仅修改元数据）。但在 OceanBase（社区版 4.x）中，`MODIFY COLUMN` 无论长度增减都会触发 **全表重建**，2 亿行的 `order` 表重建耗时 47 分钟，期间该分区上的 DML 被阻塞。

**案例 2：新增唯一索引导致数据校验失败**

```sql
-- 意图：防止重复订单号
ALTER TABLE `order` ADD UNIQUE INDEX `uk_order_no` (`order_no`);
```

OceanBase 在 `ADD UNIQUE INDEX` 时会全表扫描校验唯一性。线上 `order` 表因历史数据存在两条 `order_no` 为空的记录（MySQL 模式下 NULL ≠ NULL），唯一索引建表成功但后续 INSERT 空值时报 duplicate key 错误。

**案例 3：DROP COLUMN 导致下游 Canal 同步中断**

```sql
ALTER TABLE `order` DROP COLUMN `legacy_field`;
```

OceanBase 的 `DROP COLUMN` 是逻辑删除（标记不可见，不立即释放空间），但 Canal 解析 binlog 时由于表结构变更，下游的 ES 同步任务反序列化失败，导致数据同步中断 2 小时。

**案例 4：大表 ADD COLUMN 默认值导致数据回填**

```sql
ALTER TABLE `order` ADD COLUMN `new_field` INT NOT NULL DEFAULT 0;
```

在 OceanBase 中，`ADD COLUMN` 虽然是 Instant DDL（秒级完成），但当 `NOT NULL DEFAULT` 为非空默认值时，OceanBase 并不会像 MySQL 8.0 那样立即返回，而是会触发 **数据回填**（backfill）——将默认值写入所有已有行的该列。对于 2 亿行的大表，耗时可能数十分钟，并且期间产生大量 clog（WAL 日志），可能撑爆磁盘。

### 根因总结

| 原因 | 说明 |
|------|------|
| **认知偏差** | 团队习惯 MySQL DDL 行为，不清楚 OceanBase 哪些 DDL 是 Instant、哪些是 Rebuild |
| **缺少分级** | 所有 DDL 走同一流程，没有区分「风险等级」，紧急变更和常规变更混在一起 |
| **缺少 CI 校验** | DDL 脚本直接在生产执行，没有在 CI 阶段做 OceanBase 兼容性检查和影响分析 |
| **回滚困难** | DDL 一旦执行，回滚往往比执行更难（尤其是 Rebuild 类型的 DDL） |
| **缺少零停机流程** | Schema 变更和应用发布没有协调好，导致变更期间服务不可用 |

---

## 决策

建立 **四级 DDL 风险分级 + OceanBase 专属 DDL 规范 + Liquibase 版本化管理 + CI 自动化校验** 的组合方案。

---

## 详细设计

### 1. OceanBase DDL 底层机制

#### 1.1 OceanBase 的 DDL 实现方式

OceanBase 的 DDL 并不完全等价于 MySQL 8.0 的 Instant DDL。它在实现上分为三类：

| 实现方式 | 行为 | 特点 | 对应操作 |
|----------|------|------|----------|
| **Instant**（元数据修改） | 仅修改 OB 内部元数据表，不操作数据行 | 秒级完成，不阻塞 DML | `ADD COLUMN`（无默认值或有默认值的新列）、`DROP COLUMN`（逻辑删除）、`RENAME COLUMN`、`ALTER TABLE SET COMMENT` |
| **Rebuild**（全表重建） | 创建新版本数据表，逐行拷贝数据，切换 | 耗时与数据量成正比，期间阻塞 DML | `MODIFY COLUMN`、`DROP COLUMN` 后加列（空间回收）、`ALTER COLUMN TYPE`、`ADD PRIMARY KEY`、`CHANGE COLUMN` |
| **Online Rebuild**（在线重建） | 重建期间允许查询，但 DML 受限 | OceanBase 4.x 部分支持，但仍有阻塞窗口 | `ADD INDEX`、`ADD UNIQUE INDEX`、`ALTER TABLE ADD FOREIGN KEY` |

> **重要认知**：MySQL 中 `VARCHAR(100)` → `VARCHAR(200)` 是快速操作（仅修改 `.frm`），但在 OceanBase 中属于 Rebuild 操作。这是一个常见的陷阱。

#### 1.2 DDL 对 DML 的影响

| DDL 操作 | 执行期间 DML | 执行期间查询 | 说明 |
|----------|-------------|-------------|------|
| `ADD COLUMN`（无 NOT NULL 默认值） | ✅ 不受影响 | ✅ 不受影响 | Instant，秒级 |
| `ADD COLUMN`（NOT NULL DEFAULT 非空值） | ⚠️ 受影响（backfill） | ✅ 不受影响 | Instant 元数据 + 异步 backfill，但 backfill 会产生 clog |
| `DROP COLUMN` | ✅ 不受影响 | ✅ 不受影响 | 逻辑删除，被删列不可见但空间不释放 |
| `MODIFY COLUMN`（扩大长度） | 🚫 阻塞 DML | ✅ 可读 | Rebuild 操作，大表谨慎 |
| `MODIFY COLUMN`（改变类型） | 🚫 阻塞 DML | 🚫 阻塞 | Rebuild，高风险 |
| `ADD INDEX` | ✅ 不受影响 | ✅ 不受影响 | Online Rebuild |
| `ADD UNIQUE INDEX` | ✅ 不受影响 | ✅ 不受影响 | 但会全表扫描校验唯一性 |
| `DROP INDEX` | ✅ 不受影响 | ✅ 不受影响 | 元数据操作 |
| `ADD PRIMARY KEY` | 🚫 阻塞 DML | ⚠️ 部分阻塞 | Rebuild，需数据重组 |
| `TRUNCATE PARTITION` | 🚫 阻塞 DML | 🚫 阻塞 | 谨慎使用 |
| `RENAME TABLE` | ⚠️ 短暂阻塞 | ⚠️ 短暂阻塞 | 元数据操作，毫秒级 |

#### 1.3 OceanBase 特殊限制

```sql
-- ❌ 不支持：直接修改分区键列
ALTER TABLE `order` MODIFY COLUMN `buyer_id` ...;  -- 分区键不可修改

-- ❌ 不支持：缩小字段长度
ALTER TABLE `order` MODIFY COLUMN `buyer_remark` VARCHAR(50);  -- 需要重建且可能丢数据

-- ❌ 不支持：NOT NULL → NULL 反转后立即 DROP
ALTER TABLE `order` MODIFY COLUMN `field` ...;  -- 必须先应用层处理，再修改

-- ❌ 不支持：修改主键（需要重建表，风险极高）
ALTER TABLE `order` DROP PRIMARY KEY, ADD PRIMARY KEY (...);  -- 禁止

-- ⚠️ 有限支持：字段类型变更（仅支持兼容的扩大）
-- VARCHAR → VARCHAR 扩大 ✓
-- INT → BIGINT 扩大 ✓
-- VARCHAR → TEXT 扩大 ✓
-- DECIMAL(10,2) → DECIMAL(12,2) 扩大 ✓
-- VARCHAR → INT ✗ 类型不兼容
```

---

### 2. DDL 四级风险分级

#### 2.1 分级定义

| 等级 | 名称 | 定义 | 审批 | 执行窗口 |
|------|------|------|------|----------|
| **L1** | 无风险（Instant） | 秒级完成，不影响 DML，不产生 clog 增长 | 无需审批 | 随时执行 |
| **L2** | 低风险 | 秒级完成，不影响 DML，但产生一定 clog（backfill） | Code Review | 工作时间 |
| **L3** | 中风险（Online） | 不阻塞 DML，但全表扫描/耗时较长 | 架构师审批 | 低峰期窗口 |
| **L4** | 高风险（Rebuild） | 阻塞 DML 或需要重建表 | 架构师 + 技术负责人审批 | 变更窗口 + 回滚预案 |

#### 2.2 操作映射表

```yaml
L1 - 无风险（随时执行）:
  - ADD COLUMN [无 NOT NULL 默认值]
  - ADD COLUMN [NULL 默认值]          # DEFAULT NULL
  - DROP COLUMN                        # 逻辑删除，但注意 Canal 侧的影响
  - RENAME COLUMN
  - ALTER TABLE SET COMMENT
  - ALTER TABLE RENAME TO              # 表重命名（元数据操作，毫秒级）
  - CREATE INDEX                       # Online Rebuild，不阻塞 DML
  - DROP INDEX
  - TRUNCATE TABLE（小表 < 100 万行）   # 但不建议在生产用 TRUNCATE

L2 - 低风险（工作时间可执行，关注 clog）:
  - ADD COLUMN [NOT NULL DEFAULT 常量]  # 有 backfill，大表可能耗时久
  - ADD UNIQUE INDEX                   # 全表扫描校验唯一性，但不阻塞 DML

L3 - 中风险（低峰期执行）:
  - CREATE INDEX ON 大表（> 1 亿行）     # 执行时间较长
  - ADD UNIQUE INDEX ON 大表（> 1 亿行） # 全表扫描 + 校验，时间长
  - ADD FOREIGN KEY                     # 全表扫描校验
  - MODIFY COLUMN [扩大 VARCHAR]         # Rebuild，大表高风险
  - MODIFY COLUMN [扩大 INT → BIGINT]    # Rebuild
  - DROP COLUMN + 后续 ADD COLUMN       # 空间回收触发 Rebuild

L4 - 高风险（变更窗口 + 回滚预案，建议用零停机模式）:
  - MODIFY COLUMN [NOT NULL → NULL 或 NULL → NOT NULL]
  - MODIFY COLUMN [VARCHAR → TEXT]
  - MODIFY COLUMN [缩小长度或改变类型]     # 可能需要数据迁移
  - ADD/DROP PRIMARY KEY
  - MODIFY 分区键                        # 不支持直接修改，需要建新表
  - DROP TABLE                          # 逻辑删除（RENAME），确认后再物理删除
  - 任何涉及数据迁移的 Schema 变更
```

---

### 3. DDL 执行规范

#### 3.1 通用原则

每条 DDL 必须遵循以下原则：

```bash
# 核心五条军规
┌──────────────────────────────────────────────────────────────┐
│ ① 只增字段不改删 —— 应用层兼容旧字段，DDL 只做新增，不做破坏性操作 │
│ ② 先读文档再执行 —— 执行前查本规范，明确目标 DDL 的风险等级      │
│ ③ 先在预发执行 —— 所有 DDL 必须在 PRE 环境执行验证后，才能上 PROD │
│ ④ 大表必须分批 —— 超过 1 亿行的表，DDL 需要做影响评估和时间预估   │
│ ⑤ 回滚优先于执行 —— 执行前写好回滚脚本，确认回滚方案可行         │
└──────────────────────────────────────────────────────────────┘
```

#### 3.2 各风险等级执行流程

**L1 执行流程**（最简单，直接执行）：

```bash
Step 1: 在 PRE 环境执行 DDL，验证通过
Step 2: 在 PROD 执行 DDL
Step 3: 确认 DDL 成功（`SHOW CREATE TABLE`、`INFORMATION_SCHEMA.COLUMNS`）
Step 4: 提交 DDL 到 Liquibase 变更集（可选）
```

**L2 执行流程**（关注大表 clog）：

```bash
Step 1: 预估影响
  - 计算目标表行数: SELECT COUNT(*) FROM `table`
  - 估算 backfill 时间: 行数 × 每行数据量 / 写入带宽
  - 检查当前 clog 使用率: SHOW PARAMETER LIKE '%clog%'

Step 2: 低峰期执行（避开业务高峰期）
  - 观察执行期间的 clog 增长
  - 监控 OBServer 的 memstore 使用率

Step 3: 执行后验证
  - SHOW CREATE TABLE 确认结构
  - 执行常规 SQL 验证功能正常
  - 检查 Canal 同步状态（如果有下游依赖）
```

**L3 执行流程**（需要审批和变更窗口）：

```
申请 → 架构师审批 → 变更窗口 → 执行 → 验证 → 观测
```

详细步骤：

```bash
Step 1: 提交 DDL 变更请求（GitLab Issue 或工单系统）
  - DDL 脚本
  - 目标表行数和预估执行时间
  - 回滚脚本
  - 回滚后的验证脚本

Step 2: 架构师审批
  - 确认 DDL 操作风险等级正确
  - 确认 OceanBase 兼容性
  - 确认回滚方案可行

Step 3: 变更窗口执行
  - 在 PRE 环境先执行一次验证
  - 停止相关业务的灰度发布（避免新代码和 DDL 并发问题）
  - 在 PROD 低峰期窗口执行
  - 同时观察：clog 使用率、memstore、慢查询、应用错误率

Step 4: 观测
  - 执行后观测 30 分钟
  - 确认下游 Canal/ES 同步正常
  - 确认业务指标正常
```

**L4 执行流程**（必须使用零停机模式）：

参见第 4 节「零停机 Schema 迁移」。

#### 3.3 DDL 执行检查清单

```markdown
## DDL 执行前 Checklist

### 通用检查（所有 DDL）
- [ ] 是否在 PRE 环境执行验证过？
- [ ] 是否了解该 DDL 在 OceanBase 中的行为？（Instant / Rebuild / Online）
- [ ] 是否了解该 DDL 对 DML 的影响？（阻塞 / 不阻塞）
- [ ] 是否写好回滚脚本？
- [ ] 是否通知了相关团队？（下游消费者、DBA 值班）

### 大表检查（> 1000 万行）
- [ ] 是否在低峰期执行？
- [ ] 预估执行时间是否可接受？
- [ ] 是否监控了 clog 使用率和磁盘空间？
- [ ] 是否有足够磁盘空间应对 backfill？

### 索引检查
- [ ] UNIQUE INDEX：当前表是否有重复数据？SELECT COUNT(*) vs SELECT COUNT(DISTINCT col)
- [ ] 新建索引：是否会导致写入性能下降？（越多索引，写入越慢）
- [ ] 索引命名是否规范？（idx_表名_字段名 / uk_表名_字段名）

### 有下游依赖的检查（Canal / ES / 分析型查询）
- [ ] DDL 是否影响 Canal 解析 binlog？
- [ ] ES mapping 是否需要同步更新？
- [ ] 分析型查询（ClickHouse 等）是否需要同步变更？
```

---

### 4. 零停机 Schema 迁移模式

当遇到 L4 级别的变更（如字段类型变更、NOT NULL 转换），不能直接在表上执行 DDL，而要用应用层双写 + 数据迁移的方式实现零停机。

#### 4.1 Expand-Contract 模式（最常用）

这是零停机 Schema 迁移的通用模式，分三阶段：

```
        Expand（扩张期）          Migrate（迁移期）          Contract（收缩期）
        ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
 写入   │ 双写新旧字段      │     │ 只写新字段        │     │ 只写新字段        │
 读取   │ 读旧字段          │     │ 读新字段          │     │ 读新字段          │
 代码   │ 旧版本 + 新字段   │     │ 新版本（全量上线） │     │ 清理旧字段代码    │
 数据   │ -               │     │ 数据补偿 Job      │     │ 确认后 DROP 旧列  │
        └─────────────────┘     └─────────────────┘     └─────────────────┘
 时间        第 1 天                  第 3 天                   第 33 天
```

#### 4.2 分步操作详解

**场景示例**：需要将 `order.buyer_remark` 从 `VARCHAR(100)` 改为 `VARCHAR(500)`

**Step 1 — Expand：新增字段 + 应用双写**

```sql
-- L1 操作：新增一个字段用于存储新数据，保留旧字段
-- OceanBase: ADD COLUMN 是 INSTANT 操作，秒级完成
ALTER TABLE `order` ADD COLUMN `buyer_remark_v2` VARCHAR(500) DEFAULT '' COMMENT '买家备注(v2)';
```

```java
// 应用层双写（兼容新旧字段）
public class Order {
    // 旧字段（只读）
    private String buyerRemark;
    // 新字段（读写）
    private String buyerRemarkV2;

    @Deprecated
    public String getBuyerRemark() {
        return buyerRemarkV2 != null && !buyerRemarkV2.isEmpty() ? buyerRemarkV2 : buyerRemark;
    }

    public void setBuyerRemark(String remark) {
        // 双写：新旧字段同时写入
        this.buyerRemark = remark;
        this.buyerRemarkV2 = remark;
    }
}

// DAO 层 Mapper 映射
// <result column="buyer_remark" property="buyerRemark"/>
// <result column="buyer_remark_v2" property="buyerRemarkV2"/>
// INSERT/UPDATE 同时写入两个字段
```

**验证**：上线后运行对账脚本，确认双写一致。

```sql
-- 对账脚本：检查双写一致性
SELECT COUNT(*) AS inconsistent_count
FROM `order`
WHERE buyer_remark != buyer_remark_v2
  AND buyer_remark_v2 != '';
```

**Step 2 — Migrate：数据补偿 Job**

```java
// XXL-Job：回填历史数据的 buyer_remark → buyer_remark_v2
@XxlJob("migrateBuyerRemark")
public void migrateBuyerRemark() {
    // 分批处理，每批 1000 条
    int batchSize = 1000;
    long minId = 0;

    while (true) {
        List<Order> orders = orderMapper.selectPage(
            new Page<>(1, batchSize),
            Wrappers.lambdaQuery(Order.class)
                .gt(Order::getId, minId)
                .eq(Order::getBuyerRemarkV2, "")   // 只处理未回填的
                .orderByAsc(Order::getId)
        );
        if (orders.isEmpty()) break;

        for (Order order : orders) {
            order.setBuyerRemarkV2(order.getBuyerRemark());
            orderMapper.updateById(order);
        }
        minId = orders.get(orders.size() - 1).getId();
    }
}
```

**Step 3 — Contract：切换只写新字段 + 清理**

待新代码全量上线 + 数据补偿完成 + 验证无误后：

```java
// 切换为仅写入新字段，旧字段变成只读
public void setBuyerRemark(String remark) {
    // 仅写入新字段，旧字段不再维护
    this.buyerRemarkV2 = remark;
    // this.buyerRemark 不再赋值（保留旧值用于兼容旧版本）
}

// Getter 统一返回新字段值
public String getBuyerRemark() {
    return buyerRemarkV2;
}
```

观察 30 天，确认无人使用旧字段后：

```sql
-- L1 操作：DROP COLUMN 是逻辑删除，秒级完成
ALTER TABLE `order` DROP COLUMN `buyer_remark`;
```

#### 4.3 其他零停机模式

**模式 2：新表 + 数据迁移（适用于大规模重构）**

```bash
# 适用场景：需要修改分区键、重建主键、或者涉及 10+ 个字段的结构调整

Step 1: 创建新表 `order_v2`（新结构）
  CREATE TABLE `order_v2` (...);

Step 2: 应用层双写（同时写入 order 和 order_v2）
  - 开启双写开关（Apollo 配置控制）
  - 异步比较新旧表写入结果

Step 3: 历史数据迁移（XXL-Job）
  - 分批将 order 数据迁移到 order_v2
  - 迁移后校验一致性

Step 4: 切换读流量到新表
  - Apollo 配置：`order.read.table = order_v2`
  - 验证新表查询性能

Step 5: 关闭旧表写入，删除旧表
  - 保留旧表 30 天作为回滚手段
  - 确认无误后 DROP TABLE
```

**模式 3：视图 + 触发器兼容（适用于 API 兼容性要求高的场景）**

```bash
# 适用场景：API 返回的字段名不能变，但内部表结构调整
# OceanBase 支持视图和触发器

Step 1: 创建新结构表
Step 2: 创建视图保持旧接口兼容
  CREATE VIEW `order_v1` AS SELECT ... FROM `order_v2`;

Step 3: 逐步切换消费者从视图到新表
Step 4: 最终下线视图
```

---

### 5. DDL 版本化管理（Liquibase）

#### 5.1 Liquibase 项目结构

```
omplatform/
├── deploy/
│   ├── db/
│   │   ├── order-core/                    # order-core 服务专属 DDL
│   │   │   ├── changelog-root.xml          # 根变更日志（入口）
│   │   │   ├── v1.0.0/                     # 版本目录
│   │   │   │   ├── 001-create-order-table.xml
│   │   │   │   ├── 002-create-order-indexes.xml
│   │   │   │   └── 003-init-data.xml
│   │   │   ├── v1.1.0/
│   │   │   │   ├── 001-add-business-type.xml
│   │   │   │   └── 002-add-order-ext.xml
│   │   │   ├── v2.0.0/
│   │   │   │   ├── 001-expand-buyer-remark.xml
│   │   │   │   └── 002-add-partition-maintenance.xml
│   │   │   └── hotfix/                     # 紧急修复变更集
│   │   │       └── 20260601-fix-index.xml
│   │   │
│   │   ├── payment-service/
│   │   ├── inventory-service/
│   │   └── ...
│   │
│   └── liquibase.properties                # 全局 Liquibase 配置
```

#### 5.2 变更集规范

```xml
<!-- deploy/db/order-core/v2.0.0/001-expand-buyer-remark.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="001-expand-buyer-remark-v2" author="zhangsan" labels="v2.0.0">
        <comment>
            [ADR-011] 零停机 Schema 迁移 - Expand 阶段
            JIRA: ORDER-5678
            风险等级: L1（ADD COLUMN 是 Instant 操作）
            回滚脚本: ALTER TABLE order DROP COLUMN buyer_remark_v2;
        </comment>

        <preConditions onFail="MARK_RAN">
            <!-- 幂等：仅当字段不存在时才执行 -->
            <not>
                <columnExists tableName="order" columnName="buyer_remark_v2"/>
            </not>
        </preConditions>

        <addColumn tableName="order">
            <column name="buyer_remark_v2"
                    type="VARCHAR(500)"
                    defaultValue=""
                    remarks="买家备注(v2) - ADR-011 Expand阶段">
                <constraints nullable="false"/>
            </column>
        </addColumn>

        <!-- 同时记录 DDL 到审计表 -->
        <sql dbms="oceanbase">
            INSERT INTO `schema_change_log` (
                `change_id`, `table_name`, `ddl_type`, `risk_level`,
                `jira_id`, `author`, `description`, `rollback_sql`
            ) VALUES (
                '001-expand-buyer-remark-v2', 'order', 'ADD_COLUMN', 'L1',
                'ORDER-5678', 'zhangsan',
                '零停机迁移 - 新增 buyer_remark_v2 字段',
                'ALTER TABLE `order` DROP COLUMN `buyer_remark_v2`;'
            );
        </sql>

        <rollback>
            <dropColumn tableName="order" columnName="buyer_remark_v2"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

#### 5.3 Liquibase 配置

```properties
# deploy/liquibase.properties（按环境使用不同配置文件）
changeLogFile=deploy/db/order-core/changelog-root.xml
driver=com.mysql.cj.jdbc.Driver

# DEV 环境（开发者本地）
url=jdbc:mysql://localhost:2881/omplatform_dev
username=dev_user
password=${DEV_DB_PASSWORD}

# PRE 环境
# url=jdbc:oceanbase://pre-obproxy:2883/omplatform_pre
# username=pre_user
# password=${PRE_DB_PASSWORD}

# PROD 环境（CI 中注入，不存储在 Git）
# url=jdbc:oceanbase://prod-obproxy:2883/omplatform_prod
# username=prod_user
# password=${PROD_DB_PASSWORD}
```

#### 5.4 根变更日志

```xml
<!-- deploy/db/order-core/changelog-root.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="...">
    <!-- 版本 1.0.0 - 初始建表 -->
    <include file="v1.0.0/001-create-order-table.xml"/>
    <include file="v1.0.0/002-create-order-indexes.xml"/>
    <include file="v1.0.0/003-init-data.xml"/>

    <!-- 版本 1.1.0 - 业务扩展 -->
    <include file="v1.1.0/001-add-business-type.xml"/>
    <include file="v1.1.0/002-add-order-ext.xml"/>

    <!-- 版本 2.0.0 - 架构升级 -->
    <include file="v2.0.0/001-expand-buyer-remark.xml"/>

    <!-- 注意：禁止删除或修改已执行过的变更集！
         已有变更集必须通过新的变更集来修改 -->
</databaseChangeLog>
```

---

### 6. CI/CD 集成

#### 6.1 DDL 校验阶段

在 CI Pipeline 中增加 `schema-check` 阶段，在 MR 合并前自动校验 DDL 脚本：

```yaml
# .gitlab-ci.yml 新增阶段
stages:
  - schema-check    # 第一阶段：DDL 合规性校验
  - compile
  - test
  - ...

schema-check:
  stage: schema-check
  script:
    - |
      # 检查 MR 中修改/新增的 Liquibase 变更集
      CHANGED_DDL=$(git diff --name-only origin/main...HEAD -- 'deploy/db/**/*.xml')
      if [ -z "$CHANGED_DDL" ]; then
        echo "无 DDL 变更，跳过 Schema 校验"
        exit 0
      fi

      # 对每个变更集执行校验
      for DDL_FILE in $CHANGED_DDL; do
        echo "========================="
        echo "校验文件: $DDL_FILE"
        echo "========================="

        # 1. XML 格式校验
        xmllint --noout "$DDL_FILE" || exit 1

        # 2. OceanBase 兼容性校验
        #    检查是否使用了 OceanBase 不支持的语法
        #    调用 oceanbase-compat-check.sh 脚本
        bash deploy/ci/oceanbase-compat-check.sh "$DDL_FILE" || exit 1

        # 3. 风险等级标注检查
        #    每个变更集必须包含 risk_level 注释
        grep -q "风险等级" "$DDL_FILE" || {
          echo "❌ 缺少风险等级标注！请在变更集 comment 中添加 '风险等级: L1/L2/L3/L4'"
          exit 1
        }

        # 4. 回滚脚本检查
        #    检查是否包含 rollback 定义
        grep -q "<rollback>" "$DDL_FILE" || {
          echo "❌ 缺少回滚脚本！请添加 <rollback> 定义"
          exit 1
        }

        # 5. 变更集 ID 唯一性检查
        #    确保新变更集的 id 不与其他变更集冲突
        EXISTING_IDS=$(find deploy/db -name '*.xml' -exec grep -h 'changeSet id=' {} \;)
        NEW_ID=$(grep 'changeSet id=' "$DDL_FILE" | sed "s/.*id=\"\(.*\)\".*/\1/")
        if echo "$EXISTING_IDS" | grep -q "$NEW_ID"; then
          echo "❌ 变更集 ID ${NEW_ID} 已存在，请使用新的 ID"
          exit 1
        fi

        # 6. L3/L4 级别的额外校验
        RISK_LEVEL=$(grep "风险等级" "$DDL_FILE" | grep -oP '[L][1-4]')
        if [ "$RISK_LEVEL" = "L4" ] || [ "$RISK_LEVEL" = "L3" ]; then
          echo "⚠️  风险等级 ${RISK_LEVEL}，需要架构师审批"

          # L4 必须提供零停机迁移方案
          if [ "$RISK_LEVEL" = "L4" ]; then
            grep -q "零停机\|zero-downtime\|Expand.*Contract" "$DDL_FILE" || {
              echo "❌ L4 变更必须提供零停机迁移方案！"
              exit 1
            }
          fi
        fi
      done
    - |
      # 7. 在测试数据库上 dry-run
      echo "在测试环境执行 dry-run..."
      mvn liquibase:updateSQL \
        -Dliquibase.changeLogFile=deploy/db/order-core/changelog-root.xml \
        -Dliquibase.url=jdbc:oceanbase://test-obproxy:2883/omplatform_test
```

#### 6.2 OceanBase 兼容性检查脚本

```bash
#!/bin/bash
# deploy/ci/oceanbase-compat-check.sh
# 检查 DDL 变更集是否使用了 OceanBase 不支持的语法

DDL_FILE=$1
HAS_ERROR=0

# 检测不支持的操作
check_unsupported() {
    local PATTERN=$1
    local MESSAGE=$2
    if grep -qi "$PATTERN" "$DDL_FILE"; then
        echo "❌ $MESSAGE"
        HAS_ERROR=1
    fi
}

# 检测高风险操作
check_high_risk() {
    local PATTERN=$1
    local MESSAGE=$2
    if grep -qi "$PATTERN" "$DDL_FILE"; then
        echo "⚠️  [高风险] $MESSAGE"
    fi
}

check_unsupported "DROP PRIMARY KEY" "OceanBase 不支持 DROP PRIMARY KEY 后重新添加（需要重建表）"
check_unsupported "ALTER.*PRIMARY KEY" "ALTER PRIMARY KEY 需要重建表，请使用零停机模式"
check_unsupported "MODIFY.*VARCHAR.*REDUCE\|MODIFY.*VARCHAR.*[0-9]+.*<" "缩小 VARCHAR 长度在 OB 中会触发全表重建且可能丢数据"
check_unsupported "ALTER.*PARTITION\|DROP PARTITION\|TRUNCATE PARTITION" "分区操作在 OB 中大表高风险，需要审批"

check_high_risk "MODIFY COLUMN" "MODIFY COLUMN 在 OB 中触发全表重建，确认目标表行数 < 1000 万"
check_high_risk "ADD UNIQUE" "ADD UNIQUE INDEX 会全表扫描校验唯一性，大表需要低峰期执行"
check_high_risk "ALTER.*FOREIGN KEY" "FOREIGN KEY 校验在 OB 中有性能影响"

# 检查大表 DDL（如果文件中有 TABLE 名，可以通过规则匹配）
check_large_table() {
    local TABLE=$1
    if grep -qi "TABLE.*${TABLE}" "$DDL_FILE" 2>/dev/null; then
        echo "⚠️  目标表 ${TABLE} 可能为大表（日订单百万级），确认是否需要低峰期执行"
    fi
}
check_large_table "order"
check_large_table "order_item"
check_large_table "payment"

exit $HAS_ERROR
```

#### 6.3 Schema 漂移检测

定时任务检测数据库实际 Schema 与 Liquibase 期望的 Schema 是否一致：

```yaml
# deploy/ci/schema-drift-detection.yaml（定时任务，每天凌晨执行）
schema-drift:
  script:
    - |
      # 生成当前数据库的 Schema 快照
      mvn liquibase:snapshot \
        -Dliquibase.outputFile=/tmp/current-schema.json

      # 生成 Liquibase 期望的 Schema
      mvn liquibase:snapshot \
        -Dliquibase.snapshot.reference=$EXPECTED_SCHEMA \
        -Dliquibase.outputFile=/tmp/expected-schema.json

      # 对比差异
      mvn liquibase:diff \
        -Dliquibase.referenceUrl=offline:postgresql?snapshot=/tmp/expected-schema.json \
        -Dliquibase.url=offline:postgresql?snapshot=/tmp/current-schema.json

      # 如果存在差异（且不是已记录的变更）→ 告警
      if [ $? -ne 0 ]; then
        echo "⚠️  Schema 漂移检测到差异，请人工确认！"
        # 发送告警到企业微信群
      fi
  schedule: "0 6 * * *"    # 每天早上 6 点执行
```

#### 6.4 部署协同（DDL + 应用发布协调）

DDL 和应用发布必须按正确顺序执行，否则会导致服务不可用。

```
应用发布 + DDL 协同矩阵：

                    │  新增字段       │  删除字段       │  修改字段类型    │  新增索引
───────────────────┼────────────────┼───────────────┼────────────────┼────────────────
  DDL 先于发布      │ ✅ 推荐         │ 🚫 不行         │ 🚫 不行         │ ✅ 推荐
  发布先于 DDL      │ ✅ 可行         │ ⚠️ 需要双写/兼容 │ ⚠️ 需要双写     │ ✅ 可行
  同时执行          │ 🚫 不建议       │ 🚫 不行         │ 🚫 不行         │ 🚫 不建议

具体规则:
  新增 NOT NULL 字段: DDL 必须先执行（表加字段）→ 再发布代码（写入新字段）
  新增 NULL 字段: 先执行 DDL 或先发布代码都可以
  删除字段: 先发布代码（停写旧字段）→ 再执行 DDL（删字段）
  修改字段类型: 见零停机流程（Expand-Contract，不能一步到位）
  新增索引: DDL 先执行（索引创建不影响已有查询）
  删除索引: 先发布代码（应用不再使用该索引）→ 再删索引
```

---

### 7. Schema 变更审计表

每次 DDL 自动记录到 `schema_change_log` 表：

```sql
-- 该表所在位置：在 omplatform 的公共库中（非业务库）
CREATE TABLE `schema_change_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `change_id`     VARCHAR(128) NOT NULL COMMENT '变更集 ID（对应 Liquibase changeSet id）',
    `table_name`    VARCHAR(128) NOT NULL COMMENT '变更的表',
    `ddl_type`      VARCHAR(64)  NOT NULL COMMENT 'DDL 类型（ADD_COLUMN/DROP_COLUMN/MODIFY_COLUMN/ADD_INDEX/DROP_INDEX）',
    `risk_level`    VARCHAR(8)   NOT NULL COMMENT '风险等级（L1/L2/L3/L4）',
    `ddl_sql`       TEXT         NOT NULL COMMENT '执行的 DDL SQL',
    `rollback_sql`  TEXT                  COMMENT '回滚 SQL',
    `jira_id`       VARCHAR(64)           COMMENT '关联 JIRA/Issue 编号',
    `author`        VARCHAR(64)  NOT NULL COMMENT '执行人',
    `reviewer`      VARCHAR(64)           COMMENT '审批人（L3/L4 需要）',
    `exec_status`   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '执行状态（PENDING/SUCCESS/FAILED/ROLLED_BACK）',
    `exec_duration_ms` INT                COMMENT '执行耗时（毫秒）',
    `affected_rows` BIGINT                COMMENT 'DDL 影响行数（如 ADD INDEX 扫描的行数）',
    `clog_growth_mb` INT                  COMMENT 'DDL 导致的 clog 增长量（MB）',
    `env`           VARCHAR(16)  NOT NULL COMMENT '环境（dev/pre/prod）',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_table` (`table_name`, `gmt_create`),
    KEY `idx_author` (`author`),
    KEY `idx_jira` (`jira_id`)
) DEFAULT CHARSET=utf8mb4 COMMENT='Schema 变更审计表 - 所有 DDL 操作自动记录';
```

---

### 8. OceanBase 分区运维 DDL

订单中台使用 `PARTITION BY HASH + SUBPARTITION BY RANGE`，分区相关的 DDL 有特殊规范。

#### 8.1 新增子分区（按时间）

OceanBase 的子分区模板（`SUBPARTITION TEMPLATE`）允许自动管理时间分区，但如果模板中没有未来的子分区，需要定期手动新增。

```sql
-- 定期增加未来的子分区（建议提前 3 个月）
-- 风险等级：L2
ALTER TABLE `order` ADD SUBPARTITION (
    SUBPARTITION p2026q4 VALUES LESS THAN (1735689600),
    SUBPARTITION p2027q1 VALUES LESS THAN (1743465600),
    SUBPARTITION p2027q2 VALUES LESS THAN (1751328000)
);
```

#### 8.2 分区维护自动化（XXL-Job）

```java
// XXL-Job：每月 1 日凌晨执行，提前创建未来 6 个月的子分区
@XxlJob("autoCreatePartitions")
public void autoCreatePartitions() {
    List<String> tables = Arrays.asList("order", "order_item", "payment", "event_archive");
    for (String table : tables) {
        // 查询当前最大分区边界
        Long maxVal = partitionMapper.getMaxPartitionValue(table);
        // 如果最大分区 < 6 个月后，创建新分区
        LocalDate targetDate = LocalDate.now().plusMonths(6);
        while (maxVal < targetDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond()) {
            String partitionName = generatePartitionName(maxVal);
            partitionMapper.addSubpartition(table, partitionName, maxVal + 86400 * 90);
            maxVal += 86400 * 90;
        }
    }
}
```

#### 8.3 历史分区清理

```sql
-- 清理超过 3 年的历史分区（风险等级：L3）
-- 执行前必须确认：① 数据已备份 ② 业务不需要 ③ 已通知财务/法务
ALTER TABLE `order` TRUNCATE SUBPARTITION p2022q1;
ALTER TABLE `order` DROP SUBPARTITION p2022q2;

-- 注意：TRUNCATE/DROP PARTITION 在 OB 中会阻塞该分区的 DML
-- 建议在业务最低谷执行，并分批进行
```

---

### 9. 回滚策略

#### 9.1 DDL 回滚矩阵

| DDL 操作 | 回滚方式 | 说明 |
|----------|----------|------|
| `ADD COLUMN` | `DROP COLUMN`（L1，秒级） | 但注意 DROP 后该列数据永久丢失 |
| `DROP COLUMN` | `ADD COLUMN`（L1，秒级） | OceanBase 的 DROP 是逻辑删除，空间暂时不回收，但数据不可恢复 |
| `MODIFY COLUMN` | 不支持直接回滚 | 必须重建表或通过 Expand-Contract 反向迁移 |
| `ADD INDEX` | `DROP INDEX`（L1，秒级） | 索引重建时间长，回滚只需删除索引，秒级 |
| `DROP INDEX` | `ADD INDEX`（L3，大表耗时） | 索引删除后数据还在，但回滚需要重新建索引 |
| `ADD UNIQUE INDEX` | `DROP INDEX`（L1，秒级） | 如果索引创建失败（数据不唯一），需要先清理重复数据 |
| `RENAME TABLE` | `RENAME TABLE` 反向（L1，毫秒级） | 改名操作可逆 |
| `RENAME COLUMN` | `RENAME COLUMN` 反向（L1，秒级） | 改名操作可逆 |

#### 9.2 回滚原则

```bash
# 回滚黄金法则
1. 执行 DDL 前必须写好回滚脚本 ✅
2. 回滚脚本必须在 PRE 环境验证 ✅
3. Rebuild 类型的 DDL 没有快速回滚方式 → 用零停机模式 ✅
4. DDL 执行失败不要立即重试 → 先定位原因 ✅
5. DDL 执行超过预期时间 → 不要 kill 进程（可能导致数据不一致）→ 联系 DBA 评估 ✅
```

---

### 10. 实施计划

| 步骤 | 内容 | 产出 | 预估工期 |
|------|------|------|----------|
| **Step 1** | 团队培训：OceanBase DDL 机制 + 常见陷阱 | 培训文档 + 内部 Workshop | 1 天 |
| **Step 2** | 制定 DDL 规范细则，发布到团队 Wiki | `<ADR-011> 在线 DDL 规范.md` | 2 天 |
| **Step 3** | Liquibase 基础设施搭建 + Project 结构调整 | 各服务的 `deploy/db/` 目录结构 | 2 天 |
| **Step 4** | CI `schema-check` Pipeline 开发 | Schema 校验脚本 + Pipeline 配置 | 3 天 |
| **Step 5** | 现有表结构梳理：补全 Liquibase 变更集（初始快照） | 初始 changelog-root.xml | 3 天 |
| **Step 6** | 零停机迁移工具类开发（Expand-Contract 辅助代码） | `MigrationHelper.java` + 对账脚本 | 3 天 |
| **Step 7** | Schema 变更审计表 + XXL-Job 分区自动管理 | `schema_change_log` 表 + Job 代码 | 2 天 |
| **Step 8** | 存量 DDL 治理：清理无用的字段和索引 | 每个表逐一评审，生成迁移计划 | 5 天（穿插进行） |

---

### 11. 降级与异常处理

| 场景 | 处理方式 |
|------|----------|
| **DDL 执行超时** | 不要 kill DDL 会话。OceanBase 的 DDL 是原子操作：成功则提交，失败则回滚。Kill 会话可能导致中间状态。先检查 `oceanbase.DBA_OB_TASKS` 视图查看 DDL 进度 |
| **DDL 导致磁盘满** | 如果是 Rebuild DDL，OceanBase 需要额外磁盘空间（旧表 + 新表同时存在）。空间不足时 DDL 会失败回滚。预先用 `SHOW PARAMETER LIKE '%data_disk%'` 检查剩余空间 |
| **clog 暴涨** | ADD COLUMN 带 NOT NULL DEFAULT 会触发 backfill，产生大量 clog。监控 `__all_virtual_clog_stat` 表，clog 使用率 > 80% 时暂停 DDL |
| **Canal 同步中断** | DDL 后 Canal 需要 refresh schema。先检查 Canal 的 `destination` 状态，必要时手动 reload |
| **回滚失败** | 回滚 DDL 本身也是 DDL，可能遇到同样的问题。回滚前先评估回滚操作的风险等级 |
| **OceanBase 版本升级影响 DDL** | OB 4.x 每个 minor 版本的 DDL 行为可能不同（如 4.1 和 4.2 的 INSTANT DDL 支持范围不同）。升级前重新验证 DDL 兼容性 |

---

### 12. 与现有文档的关联

- **ADR-010 事件 Schema 治理**：[ADR-010-event-schema-governance.md](ADR-010-event-schema-governance.md) —— DDL 变更如果涉及领域事件字段，需要同步更新事件 Schema
- **Canal 数据同步**：DDL 变更后需确认 Canal 是否重新拉取了表结构，确保 ES 同步不中断
- **ES mapping 同步**：DDL 新增/修改字段时，ES mapping 可能也要同步更新（见 4.8 节 ES 索引设计）

---

### 13. 常见 DDL 变更速查表

```markdown
| 你想做什么 | OceanBase 行为 | 风险等级 | 推荐做法 |
|-----------|---------------|----------|----------|
| 新增一个字段（无默认值） | Instant，秒级 | L1 | 直接 ALTER TABLE ADD COLUMN |
| 新增一个字段（NOT NULL DEFAULT 'x'） | Instant + backfill | L2 | 小表直接执行，大表用 `DEFAULT ''` 绕开 backfill |
| 扩大 VARCHAR(100)→VARCHAR(500) | **Rebuild** | L3 | 用 Expand-Contract 零停机模式 |
| 缩小 VARCHAR(500)→VARCHAR(100) | **Rebuild + 可能丢数据** | L4 | 用零停机模式，先确保所有数据长度 ≤ 100 |
| 新增普通索引 | Online，不阻塞 DML | L1 | 直接 CREATE INDEX |
| 新增唯一索引 | Online + 全表扫描 | L2 | 先校验数据唯一性，再执行 |
| 删除一个字段 | 逻辑删除，秒级 | L1 | 先停写，30 天后再 DROP |
| 修改字段名 | Instant，秒级 | L1 | 直接 RENAME COLUMN（但注意下游依赖） |
| 增加分区 | Instant | L1 | 提前 3 个月自动创建 |
| 删除旧分区 | 阻塞该分区 DML | L3 | 确认数据已备份，低峰期执行 |
| 修改分区键 | 不支持 | L4 | 只能建新表 + 数据迁移 |
```

---

## 与架构文档的关联

本 ADR 补充了第 4.9 节（灰度发布-数据库 Schema 兼容性）的详细实现，以及第 12 节（OceanBase 运维要点）中 DDL 相关内容的具体规范。
