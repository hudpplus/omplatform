-- ============================================================================
-- OM Platform — 全数据库建库建表脚本
-- 目标：MySQL 8.x
-- 使用方式：source deploy/sql/init-all-databases.sql
-- ============================================================================

-- 1. oms_common — 通用基础设施
CREATE DATABASE IF NOT EXISTS oms_common DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_common;

-- Leaf ID 分段表
CREATE TABLE IF NOT EXISTS id_worker (
    `biz_tag`     VARCHAR(64)  NOT NULL COMMENT '业务标识',
    `max_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '当前最大 ID',
    `step`        INT          NOT NULL DEFAULT 1000 COMMENT '步长',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `gmt_create`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Leaf 号段模式 ID 生成';

INSERT INTO id_worker(biz_tag, max_id, step, description) VALUES
    ('order_no',    1, 1000, '订单号'),
    ('payment_no',  1, 1000, '支付单号'),
    ('refund_no',   1, 1000, '退款单号'),
    ('cart_id',     1, 1000, '购物车 ID'),
    ('coupon_no',   1, 1000, '优惠券实例编号');

-- 事件存储
CREATE TABLE IF NOT EXISTS event_store (
    `event_id`      VARCHAR(64)  NOT NULL COMMENT '事件 ID',
    `aggregate_id`  VARCHAR(64)  NOT NULL COMMENT '聚合根 ID',
    `event_type`    VARCHAR(128) NOT NULL COMMENT '事件类型',
    `event_data`    JSON         NOT NULL COMMENT '事件数据',
    `version`       INT          NOT NULL DEFAULT 0 COMMENT '版本号',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`event_id`),
    KEY `idx_aggregate` (`aggregate_id`, `event_type`),
    KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件溯源存储';

-- 事件投递日志
CREATE TABLE IF NOT EXISTS event_delivery_log (
    `log_id`        VARCHAR(64)  NOT NULL COMMENT '日志 ID',
    `event_id`      VARCHAR(64)  NOT NULL COMMENT '事件 ID',
    `mq_topic`      VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    `mq_tag`        VARCHAR(64)  DEFAULT NULL COMMENT 'MQ Tag',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retries`   INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `last_error`    TEXT         DEFAULT NULL COMMENT '最后一次错误信息',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`log_id`),
    KEY `idx_event` (`event_id`),
    KEY `idx_status` (`status`, `retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件投递日志';

-- Saga 实例
CREATE TABLE IF NOT EXISTS saga_instance (
    `saga_id`       VARCHAR(64)   NOT NULL COMMENT 'Saga ID',
    `saga_name`     VARCHAR(128)  NOT NULL COMMENT 'Saga 名称 (CREATE_ORDER/REFUND)',
    `order_no`      VARCHAR(64)   DEFAULT NULL COMMENT '关联订单号',
    `status`        VARCHAR(32)   NOT NULL DEFAULT 'INITIATED' COMMENT 'INITIATED/COMPLETED/FAILED/COMPENSATING/COMPENSATED',
    `current_step`  INT           NOT NULL DEFAULT 0 COMMENT '当前步骤索引',
    `step_count`    INT           NOT NULL DEFAULT 0 COMMENT '总步骤数',
    `failed_step`   VARCHAR(64)   DEFAULT NULL COMMENT '失败步骤名',
    `error_message` TEXT          DEFAULT NULL COMMENT '错误信息',
    `context_json`  JSON          DEFAULT NULL COMMENT 'Saga 上下文 JSON',
    `initiator`     VARCHAR(64)   DEFAULT NULL COMMENT '发起人',
    `started_at`    DATETIME(3)   DEFAULT NULL COMMENT '开始时间',
    `completed_at`  DATETIME(3)   DEFAULT NULL COMMENT '完成时间',
    `version`       BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `gmt_create`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`saga_id`),
    KEY `idx_order` (`order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga 编排实例';

-- Saga 补偿日志
CREATE TABLE IF NOT EXISTS saga_compensation (
    `comp_id`       VARCHAR(64)  NOT NULL COMMENT '补偿记录 ID',
    `saga_id`       VARCHAR(64)  NOT NULL COMMENT 'Saga ID',
    `step_name`     VARCHAR(64)  NOT NULL COMMENT '步骤名',
    `action`        VARCHAR(32)  NOT NULL COMMENT 'FORWARD/COMPENSATE',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    `request_json`  JSON         DEFAULT NULL COMMENT '请求参数',
    `response_json` JSON         DEFAULT NULL COMMENT '响应结果',
    `error_message` TEXT         DEFAULT NULL COMMENT '错误信息',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`comp_id`),
    KEY `idx_saga` (`saga_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga 补偿操作日志';

-- ============================================================================

-- 2. oms_trade — 交易核心
CREATE DATABASE IF NOT EXISTS oms_trade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_trade;

-- 订单主表
CREATE TABLE IF NOT EXISTS `order` (
    `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号',
    `parent_order_no` VARCHAR(64)   DEFAULT NULL COMMENT '父订单号（拆单后子单关联）',
    `buyer_id`        VARCHAR(64)   NOT NULL COMMENT '买家 ID',
    `shop_id`         VARCHAR(64)   NOT NULL COMMENT '店铺 ID',
    `status`          VARCHAR(32)   NOT NULL COMMENT '订单状态',
    `previous_status` VARCHAR(32)   DEFAULT NULL COMMENT '上一状态（用于逆向恢复）',
    `total_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    `pay_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `freight_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `address_id`      VARCHAR(64)   DEFAULT NULL COMMENT '收货地址 ID',
    `remark`          VARCHAR(512)  DEFAULT NULL COMMENT '买家备注',
    `channel_source`      VARCHAR(32)   DEFAULT NULL COMMENT '渠道来源',
    `coupon_instance_id`  VARCHAR(64)   DEFAULT NULL COMMENT '优惠券实例 ID',
    `pay_channel`         VARCHAR(32)   DEFAULT NULL COMMENT '支付渠道 (ALIPAY/WECHAT)',
    `transaction_id`  VARCHAR(128)  DEFAULT NULL COMMENT '渠道交易号',
    `hold_reason`     VARCHAR(256)  DEFAULT NULL COMMENT 'HOLD 原因',
    `frozen_reason`   VARCHAR(256)  DEFAULT NULL COMMENT 'FROZEN 原因',
    `status_changed_at` DATETIME    DEFAULT NULL COMMENT '状态变更时间',
    `status_expires_at` DATETIME    DEFAULT NULL COMMENT '状态过期时间',
    `version`         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`order_no`),
    KEY `idx_buyer` (`buyer_id`, `status`),
    KEY `idx_shop` (`shop_id`, `status`),
    KEY `idx_status_expire` (`status`, `status_expires_at`),
    KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 订单商品行（ADR-039 行级拆分）
CREATE TABLE IF NOT EXISTS order_items (
    `item_id`         BIGINT        AUTO_INCREMENT COMMENT '行 ID',
    `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号',
    `sku_id`          VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
    `sku_name`        VARCHAR(256)  NOT NULL COMMENT 'SKU 名称（快照）',
    `sku_spec`        VARCHAR(256)  DEFAULT NULL COMMENT 'SKU 规格（快照，如"颜色:黑,尺寸:L"）',
    `image_url`       VARCHAR(512)  DEFAULT NULL COMMENT '商品图片（快照）',
    `quantity`        INT           NOT NULL DEFAULT 1 COMMENT '数量',
    `unit_price`      DECIMAL(12,2) NOT NULL COMMENT '下单时单价',
    `total_amount`    DECIMAL(12,2) NOT NULL COMMENT '行总价 = unit_price × quantity',
    `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行分摊优惠',
    `pay_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行实付 = total - discount',
    `category_id`     VARCHAR(64)   DEFAULT NULL COMMENT '类目 ID',
    `line_type`       VARCHAR(32)   NOT NULL DEFAULT 'NORMAL' COMMENT '行类型 NORMAL/GIFT/EXCHANGE',
    `status`          VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '行状态 PENDING/SHIPPED/RECEIVED/RETURNING/RETURNED',
    `promotion_info`  JSON          DEFAULT NULL COMMENT '促销信息（快照）',
    `version`         BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`item_id`),
    KEY `idx_order` (`order_no`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品行';

-- 订单操作日志
CREATE TABLE IF NOT EXISTS order_operation_log (
    `log_id`      VARCHAR(64)  NOT NULL COMMENT '日志 ID',
    `order_no`    VARCHAR(64)  NOT NULL COMMENT '订单号',
    `operator_id` VARCHAR(64)  DEFAULT NULL COMMENT '操作人 ID',
    `operator_type` VARCHAR(32) DEFAULT NULL COMMENT '操作人类型 BUYER/ADMIN/SYSTEM',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作动作',
    `from_status` VARCHAR(32)  DEFAULT NULL COMMENT '来源状态',
    `to_status`   VARCHAR(32)  DEFAULT NULL COMMENT '目标状态',
    `detail`      JSON         DEFAULT NULL COMMENT '操作详情',
    `gmt_create`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`log_id`),
    KEY `idx_order` (`order_no`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单操作日志';

-- ============================================================================

-- 3. oms_seckill — 秒杀服务（独立微服务）
-- ============================================================================
CREATE DATABASE IF NOT EXISTS oms_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_seckill;

-- 秒杀活动
CREATE TABLE IF NOT EXISTS seckill_activity (
    `id`                BIGINT        AUTO_INCREMENT COMMENT '活动 ID',
    `activity_name`     VARCHAR(128)  NOT NULL COMMENT '活动名称',
    `sku_id`            VARCHAR(64)   NOT NULL COMMENT '秒杀 SKU',
    `seckill_price`     DECIMAL(12,2) NOT NULL COMMENT '秒杀价',
    `total_stock`       INT           NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_stock`   INT           NOT NULL DEFAULT 0 COMMENT '当前可用库存',
    `limit_per_user`    INT           NOT NULL DEFAULT 1 COMMENT '每人限购',
    `start_time`        DATETIME      NOT NULL COMMENT '开始时间',
    `end_time`          DATETIME      NOT NULL COMMENT '结束时间',
    `status`            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/ENDED',
    `version`           INT           NOT NULL DEFAULT 0,
    `gmt_create`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_time_window` (`start_time`, `end_time`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- ============================================================================

-- 4. oms_cart — 购物车服务
CREATE DATABASE IF NOT EXISTS oms_cart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_cart;

-- 购物车主表
CREATE TABLE IF NOT EXISTS cart_cart (
    `cart_id`    VARCHAR(64)  NOT NULL COMMENT '购物车 ID',
    `user_id`    VARCHAR(64)  DEFAULT NULL COMMENT '登录用户 ID',
    `device_id`  VARCHAR(128) DEFAULT NULL COMMENT '设备指纹',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/MERGED/EXPIRED',
    `item_count` INT          NOT NULL DEFAULT 0 COMMENT '商品总数',
    `expired_at` DATETIME     DEFAULT NULL COMMENT '匿名购物车过期时间',
    `version`    BIGINT       NOT NULL DEFAULT 0,
    `gmt_create` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`cart_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_device` (`device_id`),
    KEY `idx_status_expire` (`status`, `expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车主表';

-- 购物车行表
CREATE TABLE IF NOT EXISTS cart_item (
    `item_id`       VARCHAR(64)   NOT NULL COMMENT '行 ID',
    `cart_id`       VARCHAR(64)   NOT NULL COMMENT '购物车 ID',
    `sku_id`        VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
    `sku_name`      VARCHAR(256)  DEFAULT NULL COMMENT 'SKU 快照名称',
    `image_url`     VARCHAR(512)  DEFAULT NULL COMMENT 'SKU 快照图片',
    `quantity`      INT           NOT NULL DEFAULT 1 COMMENT '数量',
    `unit_price`    DECIMAL(12,2) NOT NULL COMMENT '加入时单价',
    `selected`      TINYINT       NOT NULL DEFAULT 1 COMMENT '是否勾选 0/1',
    `promotion_info` JSON         DEFAULT NULL COMMENT '促销信息 JSON',
    `sort_order`    INT           NOT NULL DEFAULT 0 COMMENT '排序值',
    `version`       BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`item_id`),
    KEY `idx_cart` (`cart_id`, `sort_order`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车行表';

-- 购物车 Redis 同步发件箱
CREATE TABLE IF NOT EXISTS cart_sync_outbox (
    `id`           VARCHAR(64)  NOT NULL COMMENT '主键 UUID',
    `cart_id`      VARCHAR(64)  NOT NULL COMMENT '购物车 ID',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
    `retry_count`  INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `created_at`   DATETIME(3)  NOT NULL COMMENT '创建时间',
    `updated_at`   DATETIME(3)  DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车 Redis 同步发件箱（事务性 outbox 保证最终一致）';

-- ============================================================================

-- 4. oms_marketing — 营销服务
CREATE DATABASE IF NOT EXISTS oms_marketing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_marketing;

-- 促销定义
CREATE TABLE IF NOT EXISTS promotion_definition (
    `promo_id`     VARCHAR(64)  NOT NULL COMMENT '促销 ID',
    `type`         VARCHAR(32)  NOT NULL COMMENT '类型 ITEM_DISCOUNT/FULL_REDUCTION/DISCOUNT/GROUP_BUY/FLASH_SALE',
    `name`         VARCHAR(128) NOT NULL COMMENT '促销名称',
    `config_json`  JSON         NOT NULL COMMENT '配置（满减阈值/折扣率等）',
    `priority`     INT          NOT NULL DEFAULT 0 COMMENT '优先级 1-10',
    `stack_group`  VARCHAR(32)  DEFAULT NULL COMMENT '叠加分组',
    `time_start`   DATETIME     NOT NULL COMMENT '开始时间',
    `time_end`     DATETIME     NOT NULL COMMENT '结束时间',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/ENDED',
    `version`      BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`promo_id`),
    KEY `idx_type_status` (`type`, `status`),
    KEY `idx_time_range` (`time_start`, `time_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销定义';

-- 促销活动实例
CREATE TABLE IF NOT EXISTS promotion_activity (
    `activity_id`   VARCHAR(64)  NOT NULL COMMENT '活动 ID',
    `promo_id`      VARCHAR(64)  NOT NULL COMMENT '关联促销定义',
    `scope_type`    VARCHAR(32)  NOT NULL COMMENT '范围 GLOBAL/SHOP/CATEGORY/SKU',
    `scope_value`   VARCHAR(256) DEFAULT NULL COMMENT '范围值',
    `conditions_json` JSON       DEFAULT NULL COMMENT '条件（满金额/满件数）',
    `benefits_json` JSON         NOT NULL COMMENT '优惠（减金额/折扣率）',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`activity_id`),
    KEY `idx_promo` (`promo_id`),
    KEY `idx_scope` (`scope_type`, `scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销活动实例';

-- 优惠券模板
CREATE TABLE IF NOT EXISTS coupon_template (
    `template_id`    VARCHAR(64)  NOT NULL COMMENT '模板 ID',
    `type`           VARCHAR(32)  NOT NULL COMMENT '类型 FULL_REDUCTION/DISCOUNT/CASH',
    `name`           VARCHAR(128) NOT NULL COMMENT '券名称',
    `value`          DECIMAL(12,2) NOT NULL COMMENT '优惠值（减金额/折扣率）',
    `min_amount`     DECIMAL(12,2) DEFAULT NULL COMMENT '最低使用金额',
    `stack_group_id` VARCHAR(32)  DEFAULT NULL COMMENT '叠加分组',
    `validity_days`  INT          NOT NULL DEFAULT 30 COMMENT '有效期天数',
    `total_count`    INT          NOT NULL DEFAULT 0 COMMENT '发行总量',
    `remain_count`   INT          NOT NULL DEFAULT 0 COMMENT '剩余数量',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/EXPIRED',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`template_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- 用户优惠券实例
CREATE TABLE IF NOT EXISTS coupon_instance (
    `instance_id`   VARCHAR(64)  NOT NULL COMMENT '实例 ID',
    `template_id`   VARCHAR(64)  NOT NULL COMMENT '模板 ID',
    `user_id`       VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    `order_no`      VARCHAR(64)  DEFAULT NULL COMMENT '锁定/使用的订单号',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/LOCKED/USED/EXPIRED/REFUNDED',
    `lock_time`     DATETIME     DEFAULT NULL COMMENT '锁定时间',
    `use_time`      DATETIME     DEFAULT NULL COMMENT '使用时间',
    `expire_time`   DATETIME     NOT NULL COMMENT '过期时间',
    `version`       BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`instance_id`),
    KEY `idx_user` (`user_id`, `status`),
    KEY `idx_order` (`order_no`),
    KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券实例';

-- 叠加规则配置
CREATE TABLE IF NOT EXISTS stacking_rule_config (
    `rule_id`   VARCHAR(64) NOT NULL COMMENT '规则 ID',
    `type_a`    VARCHAR(32) NOT NULL COMMENT '优惠类型 A',
    `type_b`    VARCHAR(32) NOT NULL COMMENT '优惠类型 B',
    `relation`  VARCHAR(32) NOT NULL COMMENT '关系 MUTEX/STACKABLE/CONDITIONAL',
    `priority`  INT         DEFAULT NULL COMMENT '优先级策略',
    PRIMARY KEY (`rule_id`),
    KEY `idx_types` (`type_a`, `type_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠叠加规则配置';

-- 会员主表
CREATE TABLE IF NOT EXISTS member (
    `user_id`        VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    `tier`           VARCHAR(8)   NOT NULL DEFAULT 'L0' COMMENT '等级 L0-L5',
    `growth_value`   BIGINT       NOT NULL DEFAULT 0 COMMENT '成长值',
    `total_points`   BIGINT       NOT NULL DEFAULT 0 COMMENT '总积分余额',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`),
    KEY `idx_tier` (`tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员主表';

-- 会员等级定义
CREATE TABLE IF NOT EXISTS member_tier (
    `level`         INT         NOT NULL COMMENT '等级 0-5',
    `name`          VARCHAR(32) NOT NULL COMMENT '等级名',
    `min_growth`    BIGINT      NOT NULL DEFAULT 0 COMMENT '最小成长值',
    `max_growth`    BIGINT      DEFAULT NULL COMMENT '最大成长值',
    `benefits_json` JSON        DEFAULT NULL COMMENT '权益配置 JSON',
    PRIMARY KEY (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级定义';

INSERT INTO member_tier(level, name, min_growth, max_growth) VALUES
    (0, '普通',  0,     99),
    (1, '铜卡',  100,   499),
    (2, '银卡',  500,   1999),
    (3, '金卡',  2000,  7999),
    (4, '铂金',  8000,  19999),
    (5, '黑卡',  20000, NULL);

-- 成长值流水
CREATE TABLE IF NOT EXISTS member_growth_transaction (
    `txn_id`     VARCHAR(64)  NOT NULL COMMENT '流水 ID',
    `user_id`    VARCHAR(64)  NOT NULL COMMENT '用户 ID',
    `type`       VARCHAR(32)  NOT NULL COMMENT '类型 ORDER/BONUS/EXPIRY/ADJUST',
    `amount`     BIGINT       NOT NULL COMMENT '变动值（正/负）',
    `source`     VARCHAR(64)  DEFAULT NULL COMMENT '来源订单号',
    `gmt_create` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`txn_id`),
    KEY `idx_user` (`user_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长值流水';

-- 积分账户
CREATE TABLE IF NOT EXISTS member_points_account (
    `account_id`   VARCHAR(64) NOT NULL COMMENT '账户 ID',
    `user_id`      VARCHAR(64) NOT NULL COMMENT '用户 ID',
    `balance`      BIGINT      NOT NULL DEFAULT 0 COMMENT '可用积分',
    `total_earned` BIGINT      NOT NULL DEFAULT 0 COMMENT '累计获得',
    `total_spent`  BIGINT      NOT NULL DEFAULT 0 COMMENT '累计消费',
    `version`      BIGINT      NOT NULL DEFAULT 0,
    `gmt_create`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`account_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户';

-- 积分流水
CREATE TABLE IF NOT EXISTS member_points_transaction (
    `txn_id`     VARCHAR(64)  NOT NULL COMMENT '流水 ID',
    `account_id` VARCHAR(64)  NOT NULL COMMENT '账户 ID',
    `type`       VARCHAR(32)  NOT NULL COMMENT '类型 EARN/SPEND/EXPIRE/ADJUST',
    `points`     BIGINT       NOT NULL COMMENT '变动积分',
    `source`     VARCHAR(64)  DEFAULT NULL COMMENT '来源订单号',
    `gmt_create` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`txn_id`),
    KEY `idx_account` (`account_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- 定价规则
CREATE TABLE IF NOT EXISTS price_rule (
    `rule_id`     VARCHAR(64)  NOT NULL COMMENT '规则 ID',
    `rule_type`   VARCHAR(32)  NOT NULL COMMENT '规则类型',
    `rule_config` JSON         NOT NULL COMMENT '规则配置',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '执行顺序',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `version`     BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价规则';

-- ============================================================================

-- 5. oms_finance — 资金服务
CREATE DATABASE IF NOT EXISTS oms_finance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_finance;

-- 支付单
CREATE TABLE IF NOT EXISTS payment_order (
    `payment_no`   VARCHAR(64)   NOT NULL COMMENT '支付单号',
    `order_no`     VARCHAR(64)   NOT NULL COMMENT '订单号',
    `channel`      VARCHAR(32)   NOT NULL COMMENT '渠道 ALIPAY/WECHAT',
    `amount`       DECIMAL(12,2) NOT NULL COMMENT '支付金额',
    `status`       VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/REFUNDING/REFUNDED',
    `channel_trade_no` VARCHAR(128) DEFAULT NULL COMMENT '渠道交易号',
    `paid_at`      DATETIME      DEFAULT NULL COMMENT '支付时间',
    `notify_raw`   TEXT          DEFAULT NULL COMMENT '回调原始数据',
    `version`      BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`payment_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_channel_trade` (`channel`, `channel_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

-- 退款单
CREATE TABLE IF NOT EXISTS refund_order (
    `refund_no`      VARCHAR(64)   NOT NULL COMMENT '退款单号',
    `order_no`       VARCHAR(64)   NOT NULL COMMENT '订单号',
    `payment_no`     VARCHAR(64)   NOT NULL COMMENT '原支付单号',
    `channel`        VARCHAR(32)   NOT NULL COMMENT '渠道',
    `amount`         DECIMAL(12,2) NOT NULL COMMENT '退款金额',
    `reason`         VARCHAR(512)  DEFAULT NULL COMMENT '退款原因',
    `status`         VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    `channel_refund_no` VARCHAR(128) DEFAULT NULL COMMENT '渠道退款号',
    `version`        BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`refund_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_payment` (`payment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- 结算单
CREATE TABLE IF NOT EXISTS settlement_order (
    `settle_no`    VARCHAR(64)   NOT NULL COMMENT '结算单号',
    `order_no`     VARCHAR(64)   NOT NULL COMMENT '订单号',
    `shop_id`      VARCHAR(64)   NOT NULL COMMENT '店铺 ID',
    `amount`       DECIMAL(12,2) NOT NULL COMMENT '结算金额',
    `commission`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '平台佣金',
    `commission_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.0060 COMMENT '佣金比例',
    `status`       VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/FAILED',
    `settle_at`    DATETIME      DEFAULT NULL COMMENT '结算时间',
    `version`      BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`settle_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_shop` (`shop_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单';

-- 对账记录
CREATE TABLE IF NOT EXISTS reconciliation_record (
    `id`              BIGINT        AUTO_INCREMENT COMMENT '主键',
    `reconcile_date`  VARCHAR(10)   NOT NULL COMMENT '对账日期 yyyy-MM-dd',
    `channel`         VARCHAR(32)   NOT NULL COMMENT '渠道 ALIPAY/WECHAT',
    `order_no`        VARCHAR(64)   DEFAULT NULL COMMENT '系统订单号',
    `channel_trade_no` VARCHAR(128) DEFAULT NULL COMMENT '渠道交易号',
    `system_amount`   DECIMAL(12,2) DEFAULT NULL COMMENT '系统侧金额',
    `channel_amount`  DECIMAL(12,2) DEFAULT NULL COMMENT '渠道侧金额',
    `status`          VARCHAR(32)   NOT NULL COMMENT 'MATCHED/MISMATCHED/SYSTEM_ONLY/CHANNEL_ONLY',
    `difference`      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '差异金额',
    `resolved`        TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已处理 0/1',
    `resolve_note`    VARCHAR(512)  DEFAULT NULL COMMENT '处理备注',
    `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_date` (`reconcile_date`),
    KEY `idx_channel` (`channel`, `reconcile_date`),
    KEY `idx_status` (`status`, `resolved`),
    KEY `idx_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账记录';

-- ============================================================================

-- 6. oms_fulfillment — 履约服务
CREATE DATABASE IF NOT EXISTS oms_fulfillment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_fulfillment;

-- 库存
CREATE TABLE IF NOT EXISTS inventory (
    `sku_id`            VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
    `total_quantity`    INT           NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_quantity` INT          NOT NULL DEFAULT 0 COMMENT '可用库存',
    `hold_quantity`     INT           NOT NULL DEFAULT 0 COMMENT '预占库存',
    `deducted_quantity` INT           NOT NULL DEFAULT 0 COMMENT '已扣减库存',
    `version`           BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存';

-- 库存预占记录
CREATE TABLE IF NOT EXISTS inventory_hold (
    `hold_id`    VARCHAR(64) NOT NULL COMMENT '预占记录 ID',
    `sku_id`     VARCHAR(64) NOT NULL COMMENT 'SKU ID',
    `quantity`   INT         NOT NULL COMMENT '预占数量',
    `order_no`   VARCHAR(64) NOT NULL COMMENT '订单号',
    `status`     VARCHAR(32) NOT NULL DEFAULT 'HOLD' COMMENT 'HOLD/DEDUCTED/RELEASED',
    `gmt_create` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`hold_id`),
    KEY `idx_sku` (`sku_id`),
    KEY `idx_order` (`order_no`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占记录';

-- 库存流水表（审计对账用途，ADR-043 §7.4）
CREATE TABLE IF NOT EXISTS inventory_transaction (
    `transaction_no`  VARCHAR(32)   NOT NULL COMMENT '流水号',
    `hold_id`         VARCHAR(64)   DEFAULT NULL COMMENT '关联预占 ID',
    `request_id`      VARCHAR(64)   DEFAULT NULL COMMENT '幂等请求 ID',
    `sku_id`          VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
    `order_no`        VARCHAR(64)   DEFAULT NULL COMMENT '关联订单号',
    `channel_code`    VARCHAR(16)   DEFAULT NULL COMMENT '渠道编码',
    `operation_type`  VARCHAR(16)   NOT NULL COMMENT 'RESERVE/CONFIRM/RELEASE/UNDO_DEDUCT/FREEZE/UNFREEZE/ADJUST',
    `quantity`        INT           NOT NULL COMMENT '变动数量',
    `before_qty`      INT           NOT NULL COMMENT '变动前可用库存',
    `after_qty`       INT           NOT NULL COMMENT '变动后可用库存',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`transaction_no`),
    KEY `idx_sku` (`sku_id`),
    KEY `idx_order` (`order_no`),
    KEY `idx_hold` (`hold_id`),
    KEY `idx_operation_type` (`operation_type`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表（审计）';

-- 履约单
CREATE TABLE IF NOT EXISTS fulfillment_order (
    `fulfill_no` VARCHAR(64)  NOT NULL COMMENT '履约单号',
    `order_no`   VARCHAR(64)  NOT NULL COMMENT '订单号',
    `type`       VARCHAR(32)  NOT NULL COMMENT '类型 SHIP/REFUND/RETURN',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SHIPPED/DELIVERED',
    `logistics_no` VARCHAR(128) DEFAULT NULL COMMENT '物流单号',
    `logistics_company` VARCHAR(64) DEFAULT NULL COMMENT '物流公司',
    `version`    BIGINT       NOT NULL DEFAULT 0,
    `gmt_create` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`fulfill_no`),
    KEY `idx_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='履约单';

-- ============================================================================

-- 7. oms_channel_adapter — 渠道适配器
CREATE DATABASE IF NOT EXISTS oms_channel_adapter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_channel_adapter;

-- 渠道原始订单
CREATE TABLE IF NOT EXISTS channel_raw_order (
    `raw_id`      VARCHAR(64)  NOT NULL COMMENT '原始记录 ID',
    `channel`     VARCHAR(32)  NOT NULL COMMENT '渠道 Tmall/JD/PDD/Douyin/Wechat/POS',
    `channel_order_no` VARCHAR(128) NOT NULL COMMENT '渠道订单号',
    `raw_data`    JSON         NOT NULL COMMENT '原始请求数据',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSED/FAILED',
    `order_no`    VARCHAR(64)  DEFAULT NULL COMMENT '标准化后订单号',
    `error_msg`   TEXT         DEFAULT NULL COMMENT '处理失败原因',
    `gmt_create`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`raw_id`),
    KEY `idx_channel` (`channel`, `channel_order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道原始订单';

-- 渠道状态同步日志
CREATE TABLE IF NOT EXISTS channel_sync_log (
    `sync_id`    VARCHAR(64)  NOT NULL COMMENT '同步记录 ID',
    `channel`    VARCHAR(32)  NOT NULL COMMENT '渠道',
    `order_no`   VARCHAR(64)  NOT NULL COMMENT '订单号',
    `action`     VARCHAR(64)  NOT NULL COMMENT '同步动作',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    `response`   JSON         DEFAULT NULL COMMENT '渠道返回',
    `gmt_create` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`sync_id`),
    KEY `idx_channel_order` (`channel`, `order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道状态同步日志';

-- ============================================================================

-- 8. oms_risk — 风控集成
CREATE DATABASE IF NOT EXISTS oms_risk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_risk;

-- 风控检查记录
CREATE TABLE IF NOT EXISTS risk_check_record (
    `record_id`         VARCHAR(64)  NOT NULL COMMENT '记录 ID',
    `check_type`        VARCHAR(32)  NOT NULL COMMENT '类型 PRE_CHECK/REFUND_CHECK',
    `buyer_id`          VARCHAR(64)  DEFAULT NULL COMMENT '买家 ID',
    `device_id`         VARCHAR(128) DEFAULT NULL COMMENT '设备 ID',
    `order_no`          VARCHAR(64)  DEFAULT NULL COMMENT '订单号',
    `decision`          VARCHAR(32)  NOT NULL COMMENT 'PASS/REVIEW/REJECT',
    `risk_level`        VARCHAR(32)  DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH',
    `score`             INT          DEFAULT NULL COMMENT '评分 0-100',
    `external_trace_id` VARCHAR(128) DEFAULT NULL COMMENT '外部平台追踪 ID',
    `reason`            VARCHAR(512) DEFAULT NULL COMMENT '检查原因/备注',
    `degradation_level` VARCHAR(8)   DEFAULT NULL COMMENT '降级等级 L0/L1/L2',
    `version`           BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`record_id`),
    KEY `idx_buyer` (`buyer_id`),
    KEY `idx_order` (`order_no`),
    KEY `idx_check_type` (`check_type`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控检查记录';

-- 风控审核记录（异步）
CREATE TABLE IF NOT EXISTS risk_review_record (
    `review_id`     VARCHAR(64)  NOT NULL COMMENT '审核 ID',
    `record_id`     VARCHAR(64)  NOT NULL COMMENT '关联检查记录 ID',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `reviewer`      VARCHAR(64)  DEFAULT NULL COMMENT '审核人',
    `review_comment` VARCHAR(512) DEFAULT NULL COMMENT '审核意见',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`review_id`),
    KEY `idx_record` (`record_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控异步审核记录';

-- 风控黑名单
CREATE TABLE IF NOT EXISTS risk_blacklist (
    `black_id`     VARCHAR(64)  NOT NULL COMMENT '黑名单 ID',
    `entity_type`  VARCHAR(32)  NOT NULL COMMENT '类型 USER/DEVICE/IP',
    `entity_value` VARCHAR(128) NOT NULL COMMENT '值',
    `reason`       VARCHAR(512) DEFAULT NULL COMMENT '原因',
    `created_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`black_id`),
    KEY `idx_entity` (`entity_type`, `entity_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控黑名单';

-- 风控白名单
CREATE TABLE IF NOT EXISTS risk_whitelist (
    `white_id`     VARCHAR(64)  NOT NULL COMMENT '白名单 ID',
    `entity_type`  VARCHAR(32)  NOT NULL COMMENT '类型 USER/DEVICE/IP',
    `entity_value` VARCHAR(128) NOT NULL COMMENT '值',
    `reason`       VARCHAR(512) DEFAULT NULL COMMENT '原因',
    `created_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`white_id`),
    KEY `idx_entity` (`entity_type`, `entity_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控白名单';

-- 售后记录（oms_aftersale，ADR-048）
CREATE DATABASE IF NOT EXISTS oms_aftersale DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_aftersale;

-- 售后单
CREATE TABLE IF NOT EXISTS aftersale_order (
    `after_no`    VARCHAR(64)   NOT NULL COMMENT '售后单号',
    `order_no`    VARCHAR(64)   NOT NULL COMMENT '原订单号',
    `buyer_id`    VARCHAR(64)   NOT NULL COMMENT '买家 ID',
    `type`        VARCHAR(32)   NOT NULL COMMENT '类型 REFUND_ONLY/RETURN_GOODS/EXCHANGE',
    `reason`      VARCHAR(512)  DEFAULT NULL COMMENT '售后原因',
    `amount`      DECIMAL(12,2) NOT NULL COMMENT '退款金额',
    `status`      VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '售后状态机状态',
    `version`     BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`after_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_buyer` (`buyer_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单';

-- 事务性发件箱（Transactional Outbox）
CREATE TABLE IF NOT EXISTS outbox_message (
    `id`          VARCHAR(64)   NOT NULL COMMENT '主键 UUID',
    `topic`       VARCHAR(128)  NOT NULL COMMENT 'RocketMQ Topic',
    `payload`     TEXT          COMMENT '消息体 JSON',
    `status`      VARCHAR(32)   DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `created_at`  DATETIME(3)   COMMENT '创建时间',
    `sent_at`     DATETIME(3)   COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务性发件箱（业务 DB 中的消息队列）';

-- ============================================================================
-- 迁移脚本（兼容已有数据库）
-- ============================================================================

-- 2026-06-25: order 表增加支付渠道和交易号
-- ALTER TABLE oms_trade.`order`
--     ADD COLUMN `pay_channel`    VARCHAR(32)  DEFAULT NULL COMMENT '支付渠道 (ALIPAY/WECHAT)' AFTER `channel_source`,
--     ADD COLUMN `transaction_id` VARCHAR(128) DEFAULT NULL COMMENT '渠道交易号' AFTER `pay_channel`;

-- 2026-06-26: order 表增加秒杀活动 ID 和批次
-- ALTER TABLE oms_trade.`order`
--     ADD COLUMN `seckill_activity_id` BIGINT     DEFAULT NULL COMMENT '秒杀活动 ID' AFTER `coupon_instance_id`,
--     ADD COLUMN `seckill_pipeline`    VARCHAR(16) DEFAULT NULL COMMENT '秒杀批次' AFTER `seckill_activity_id`;

-- ============================================================================

-- 9. oms_wms — 仓储管理服务
CREATE DATABASE IF NOT EXISTS oms_wms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oms_wms;

-- 仓库
CREATE TABLE IF NOT EXISTS wms_warehouse (
    `warehouse_code` VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `warehouse_name` VARCHAR(128) NOT NULL COMMENT '仓库名称',
    `type`           VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '类型 NORMAL/OVERSEA/VIRTUAL',
    `status`         VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/INACTIVE',
    `address`        VARCHAR(512) DEFAULT NULL COMMENT '地址',
    `contact`        VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
    `contact_phone`  VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`warehouse_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库';

-- 库区
CREATE TABLE IF NOT EXISTS wms_zone (
    `zone_code`      VARCHAR(32)  NOT NULL COMMENT '库区编码',
    `warehouse_code` VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `zone_type`      VARCHAR(16)  NOT NULL DEFAULT 'BULK' COMMENT '类型 BULK/PICK/TRANSIT/RECEIVING/QUARANTINE',
    `zone_name`      VARCHAR(128) DEFAULT NULL COMMENT '库区名称',
    `description`    VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`zone_code`),
    KEY `idx_warehouse` (`warehouse_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库区';

-- 库位
CREATE TABLE IF NOT EXISTS wms_location (
    `location_code`  VARCHAR(32)  NOT NULL COMMENT '库位编码 A-01-03-2-5',
    `zone_code`      VARCHAR(32)  NOT NULL COMMENT '所属库区',
    `warehouse_code` VARCHAR(32)  NOT NULL COMMENT '所属仓库',
    `location_type`  VARCHAR(16)  NOT NULL DEFAULT 'BULK' COMMENT '类型 PICK/BULK/RECEIVING/STAGING',
    `abc_class`      CHAR(1)      DEFAULT NULL COMMENT 'ABC 分类',
    `max_weight`     DECIMAL(10,2) DEFAULT NULL COMMENT '最大承重(kg)',
    `max_volume`     DECIMAL(10,2) DEFAULT NULL COMMENT '最大容积(m³)',
    `max_quantity`   INT          DEFAULT NULL COMMENT '最大容量(件)',
    `container_code` VARCHAR(64)  DEFAULT NULL COMMENT '容器编码(托盘/箱)',
    `status`         VARCHAR(8)   NOT NULL DEFAULT 'EMPTY' COMMENT '状态 EMPTY/OCCUPIED/LOCKED/FULL',
    `putaway_seq`    INT          DEFAULT 0 COMMENT '上架优先级(越小越优先)',
    `pick_seq`       INT          DEFAULT 0 COMMENT '拣货优先级(越小越优先)',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`location_code`),
    KEY `idx_warehouse` (`warehouse_code`),
    KEY `idx_zone` (`zone_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位';

-- 批次库存（多维库存核心表）
CREATE TABLE IF NOT EXISTS wms_inventory (
    `id`               BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `sku_id`           VARCHAR(64)  NOT NULL COMMENT 'SKU ID',
    `warehouse_code`   VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `location_code`    VARCHAR(32)  NOT NULL COMMENT '库位编码',
    `batch_no`         VARCHAR(64)  DEFAULT '' COMMENT '生产批次号',
    `owner_code`       VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT' COMMENT '货主编码(3PL)',
    `inventory_status` VARCHAR(16)  NOT NULL DEFAULT 'QUALIFIED' COMMENT '库存状态 QUALIFIED/DEFECTIVE/INSPECTING/FROZEN',
    `quantity`         INT          NOT NULL DEFAULT 0 COMMENT '数量',
    `lock_quantity`    INT          NOT NULL DEFAULT 0 COMMENT '锁定数量(出库分配锁定)',
    `inbound_date`     DATE         DEFAULT NULL COMMENT '入库日期',
    `expire_date`      DATE         DEFAULT NULL COMMENT '过期日期(FEFO出库依据)',
    `produce_date`     DATE         DEFAULT NULL COMMENT '生产日期',
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_loc_batch` (`sku_id`, `warehouse_code`, `location_code`, `batch_no`, `inventory_status`, `owner_code`),
    KEY `idx_warehouse` (`warehouse_code`),
    KEY `idx_location` (`location_code`),
    KEY `idx_expire` (`expire_date`),
    KEY `idx_owner` (`owner_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次库存（多维库存核心表）';

-- WMS 库存流水（审计对账）
CREATE TABLE IF NOT EXISTS wms_inventory_transaction (
    `id`               BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `transaction_no`   VARCHAR(32)  NOT NULL COMMENT '流水号 WMS_xxxxxxxxxxxxxxxx',
    `sku_id`           VARCHAR(64)  NOT NULL COMMENT 'SKU ID',
    `warehouse_code`   VARCHAR(32)  DEFAULT NULL COMMENT '仓库编码',
    `location_code`    VARCHAR(32)  DEFAULT NULL COMMENT '库位编码',
    `batch_no`         VARCHAR(64)  DEFAULT NULL COMMENT '批次号',
    `owner_code`       VARCHAR(32)  DEFAULT NULL COMMENT '货主',
    `ref_no`           VARCHAR(64)  DEFAULT NULL COMMENT '关联单号(ASN/出库单/盘点单)',
    `ref_type`         VARCHAR(16)  DEFAULT NULL COMMENT '关联类型 ASN/OUTBOUND/COUNT/MOVE/ADJUST',
    `op_type`          VARCHAR(16)  NOT NULL COMMENT '操作类型 RECEIVE/PUTAWAY/ALLOCATE/PICK/SHIP/MOVE/COUNT/ADJUST',
    `quantity`         INT          NOT NULL COMMENT '变动数量',
    `before_qty`       INT          DEFAULT NULL COMMENT '变动前数量',
    `after_qty`        INT          DEFAULT NULL COMMENT '变动后数量',
    `op_by`            VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `gmt_create`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transaction_no` (`transaction_no`),
    KEY `idx_sku` (`sku_id`),
    KEY `idx_location` (`location_code`),
    KEY `idx_ref` (`ref_no`, `ref_type`),
    KEY `idx_op_time` (`op_type`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WMS 库存流水（审计）';

-- ============================================================================
-- Phase 3: 出库流程（Allocation → Pick → Pack → Ship）
-- ============================================================================

-- 出库单主表
CREATE TABLE IF NOT EXISTS wms_outbound_order (
    `outbound_no`      VARCHAR(64)   NOT NULL COMMENT '出库单号 OB+yyyymmdd+序列',
    `order_no`         VARCHAR(64)   DEFAULT NULL COMMENT '业务订单号',
    `warehouse_code`   VARCHAR(32)   NOT NULL COMMENT '仓库编码',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'NEW' COMMENT '状态 NEW/ALLOCATING/ALLOCATED/PICKING/PICKED/PACKING/PACKED/SHIPPING/SHIPPED/CANCELLED',
    `priority`         INT           NOT NULL DEFAULT 5 COMMENT '优先级 1-10',
    `delivery_method`  VARCHAR(32)   DEFAULT NULL COMMENT '配送方式 EXPRESS/EMS/AIR/LAND',
    `receiver_name`    VARCHAR(64)   DEFAULT NULL COMMENT '收货人姓名',
    `receiver_phone`   VARCHAR(32)   DEFAULT NULL COMMENT '收货人电话',
    `receiver_address` VARCHAR(512)  DEFAULT NULL COMMENT '收货地址',
    `logistics_company` VARCHAR(64)  DEFAULT NULL COMMENT '物流公司',
    `logistics_no`     VARCHAR(128)  DEFAULT NULL COMMENT '物流单号',
    `total_sku_count`  INT           DEFAULT 0 COMMENT 'SKU 种类数',
    `total_quantity`   INT           DEFAULT 0 COMMENT '商品总数量',
    `expected_at`      DATETIME      DEFAULT NULL COMMENT '期望发货时间',
    `shipped_at`       DATETIME      DEFAULT NULL COMMENT '实际发货时间',
    `cancel_reason`    VARCHAR(256)  DEFAULT NULL COMMENT '取消原因',
    `version`          BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`outbound_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_warehouse` (`warehouse_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单主表';

-- 出库单明细
CREATE TABLE IF NOT EXISTS wms_outbound_item (
    `id`             BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `outbound_no`    VARCHAR(64)  NOT NULL COMMENT '出库单号',
    `sku_id`         VARCHAR(64)  NOT NULL COMMENT 'SKU ID',
    `sku_name`       VARCHAR(256) DEFAULT NULL COMMENT 'SKU 名称（快照）',
    `expected_qty`   INT          NOT NULL COMMENT '期望数量',
    `allocated_qty`  INT          DEFAULT 0 COMMENT '已分配数量',
    `picked_qty`     INT          DEFAULT 0 COMMENT '已拣货数量',
    `shipped_qty`    INT          DEFAULT 0 COMMENT '已发货数量',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_outbound` (`outbound_no`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单明细';

-- 分配记录（FEFO 分配结果）
CREATE TABLE IF NOT EXISTS wms_allocation (
    `id`              BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `allocation_no`   VARCHAR(32)  NOT NULL COMMENT '分配号 ALC+yyyymmdd+序列',
    `outbound_no`     VARCHAR(64)  NOT NULL COMMENT '出库单号',
    `item_id`         BIGINT       DEFAULT NULL COMMENT '出库单明细 ID',
    `sku_id`          VARCHAR(64)  NOT NULL COMMENT 'SKU ID',
    `warehouse_code`  VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `location_code`   VARCHAR(32)  NOT NULL COMMENT '库位编码',
    `batch_no`        VARCHAR(64)  DEFAULT NULL COMMENT '批次号',
    `owner_code`      VARCHAR(32)  DEFAULT NULL COMMENT '货主编码',
    `inventory_id`    BIGINT       NOT NULL COMMENT 'wms_inventory.id',
    `allocated_qty`   INT          NOT NULL COMMENT '分配数量',
    `picked_qty`      INT          DEFAULT 0 COMMENT '已拣货数量',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ALLOCATED' COMMENT '状态 ALLOCATED/PICKING/PICKED/SHIPPED/CANCELLED',
    `version`         BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_allocation_no` (`allocation_no`),
    KEY `idx_outbound` (`outbound_no`),
    KEY `idx_inventory` (`inventory_id`),
    KEY `idx_location` (`location_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分配记录（FEFO 分配结果）';

-- 波次
CREATE TABLE IF NOT EXISTS wms_wave (
    `wave_no`          VARCHAR(32)  NOT NULL COMMENT '波次号 WV+yyyymmdd+序列',
    `warehouse_code`   VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '状态 OPEN/ALLOCATING/ALLOCATED/PICKING/COMPLETED/CANCELLED',
    `type`             VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '类型 MANUAL/AUTO',
    `total_order_count` INT         DEFAULT 0 COMMENT '包含出库单数',
    `total_sku_count`  INT          DEFAULT 0 COMMENT 'SKU 种类数',
    `total_quantity`   INT          DEFAULT 0 COMMENT '商品总数量',
    `wave_at`          DATETIME     DEFAULT NULL COMMENT '波次生成时间',
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`wave_no`),
    KEY `idx_warehouse` (`warehouse_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='波次';

-- 波次与出库单关联
CREATE TABLE IF NOT EXISTS wms_wave_order (
    `id`           BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `wave_no`      VARCHAR(32)  NOT NULL COMMENT '波次号',
    `outbound_no`  VARCHAR(64)  NOT NULL COMMENT '出库单号',
    `version`      BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wave` (`wave_no`),
    KEY `idx_outbound` (`outbound_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='波次与出库单关联';

-- 拣货任务
CREATE TABLE IF NOT EXISTS wms_picking_task (
    `task_no`          VARCHAR(32)  NOT NULL COMMENT '拣货任务号 PK+yyyymmdd+序列',
    `wave_no`          VARCHAR(32)  DEFAULT NULL COMMENT '关联波次号',
    `warehouse_code`   VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    `zone_code`        VARCHAR(32)  DEFAULT NULL COMMENT '拣货库区',
    `assignee`         VARCHAR(64)  DEFAULT NULL COMMENT '拣货员',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT '状态 NEW/PICKING/PARTIALLY_PICKED/COMPLETED/CANCELLED',
    `type`             VARCHAR(16)  NOT NULL DEFAULT 'PICKING' COMMENT '类型 PICKING/REPLENISHMENT',
    `total_locations`  INT          DEFAULT 0 COMMENT '总库位数',
    `total_items`      INT          DEFAULT 0 COMMENT '总行数',
    `total_quantity`   INT          DEFAULT 0 COMMENT '总数量',
    `picked_locations` INT          DEFAULT 0 COMMENT '已拣库位数',
    `picked_items`     INT          DEFAULT 0 COMMENT '已拣行数',
    `picked_quantity`  INT          DEFAULT 0 COMMENT '已拣数量',
    `started_at`       DATETIME     DEFAULT NULL COMMENT '开始时间',
    `completed_at`     DATETIME     DEFAULT NULL COMMENT '完成时间',
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`task_no`),
    KEY `idx_wave` (`wave_no`),
    KEY `idx_warehouse` (`warehouse_code`),
    KEY `idx_status_assignee` (`status`, `assignee`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拣货任务';

-- 拣货任务明细
CREATE TABLE IF NOT EXISTS wms_picking_item (
    `id`             BIGINT       AUTO_INCREMENT COMMENT '自增主键',
    `task_no`        VARCHAR(32)  NOT NULL COMMENT '拣货任务号',
    `allocation_id`  BIGINT       NOT NULL COMMENT '分配记录 ID',
    `sku_id`         VARCHAR(64)  NOT NULL COMMENT 'SKU ID',
    `location_code`  VARCHAR(32)  NOT NULL COMMENT '库位编码',
    `batch_no`       VARCHAR(64)  DEFAULT NULL COMMENT '批次号',
    `expected_qty`   INT          NOT NULL COMMENT '应拣数量',
    `picked_qty`     INT          DEFAULT 0 COMMENT '已拣数量',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/PICKED/SKIPPED',
    `picked_at`      DATETIME     DEFAULT NULL COMMENT '拣货时间',
    `picked_by`      VARCHAR(64)  DEFAULT NULL COMMENT '拣货员',
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_no`),
    KEY `idx_allocation` (`allocation_id`),
    KEY `idx_location` (`location_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拣货任务明细';

-- 打包记录
CREATE TABLE IF NOT EXISTS wms_packing (
    `id`             BIGINT        AUTO_INCREMENT COMMENT '自增主键',
    `outbound_no`    VARCHAR(64)   NOT NULL COMMENT '出库单号',
    `task_no`        VARCHAR(32)   DEFAULT NULL COMMENT '关联拣货任务号',
    `package_no`     VARCHAR(64)   DEFAULT NULL COMMENT '包裹号/箱号',
    `sku_id`         VARCHAR(64)   DEFAULT NULL COMMENT '商品 SKU',
    `sku_name`       VARCHAR(256)  DEFAULT NULL COMMENT '商品名称',
    `quantity`       INT           NOT NULL COMMENT '数量',
    `weight`         DECIMAL(10,2) DEFAULT NULL COMMENT '重量(kg)',
    `length`         DECIMAL(10,2) DEFAULT NULL COMMENT '长(cm)',
    `width`          DECIMAL(10,2) DEFAULT NULL COMMENT '宽(cm)',
    `height`         DECIMAL(10,2) DEFAULT NULL COMMENT '高(cm)',
    `package_type`   VARCHAR(16)   NOT NULL DEFAULT 'BOX' COMMENT '包装类型 BOX/BAG/PALLET',
    `operator`       VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
    `packed_at`      DATETIME      DEFAULT NULL COMMENT '打包时间',
    `version`        BIGINT        NOT NULL DEFAULT 0,
    `gmt_create`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_outbound` (`outbound_no`),
    KEY `idx_task` (`task_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打包记录';

-- ============================================================================
-- ADR-017: 多业务线数据物理隔离 — 新表结构
-- ============================================================================

-- 1. 现有 order 表增加 business_type 字段（存量兼容）
ALTER TABLE `order` ADD COLUMN IF NOT EXISTS `business_type` VARCHAR(16) NOT NULL DEFAULT 'ecommerce' COMMENT '业务线 ecommerce/locallife/b2b' AFTER `shop_id`;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS `business_type` VARCHAR(16) NOT NULL DEFAULT 'ecommerce' COMMENT '业务线(冗余路由用)' AFTER `order_no`;

-- 2. 电商订单表（物理分表模板，ShardingSphere 路由到 order_ecommerce_0~63）
--   每个 oms_trade_ecommerce_{0..7} 数据库中需创建 order_ecommerce_{0..7}
--   order_items_{0..7} order_ecommerce_ext_{0..7}
--   以下为各库各表的完整 DDL 模板
--
--   实际建库建表脚由 deploy/shard/create-shard-tables.sh 或 ShardingSphere 自动建表
--   此处列出的 DDL 用于参考和手工初始化
--
-- 电商订单基础表模板
-- DDL for: oms_trade_ecommerce_{0..7}.order_ecommerce_{0..7}
-- CREATE TABLE `order_ecommerce_X` (
--     `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号',
--     `parent_order_no` VARCHAR(64)   DEFAULT NULL COMMENT '父订单号',
--     `buyer_id`        VARCHAR(64)   NOT NULL COMMENT '买家 ID',
--     `shop_id`         VARCHAR(64)   NOT NULL COMMENT '店铺 ID',
--     `business_type`   VARCHAR(16)   NOT NULL DEFAULT 'ecommerce' COMMENT '业务线',
--     `status`          VARCHAR(32)   NOT NULL COMMENT '订单状态',
--     `previous_status` VARCHAR(32)   DEFAULT NULL COMMENT '上一状态',
--     `total_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `pay_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `freight_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `address_id`      VARCHAR(64)   DEFAULT NULL,
--     `remark`          VARCHAR(512)  DEFAULT NULL,
--     `channel_source`  VARCHAR(32)   DEFAULT NULL,
--     `coupon_instance_id` VARCHAR(64) DEFAULT NULL,
--     `pay_channel`     VARCHAR(32)   DEFAULT NULL,
--     `transaction_id`  VARCHAR(128)  DEFAULT NULL,
--     `hold_reason`     VARCHAR(256)  DEFAULT NULL,
--     `frozen_reason`   VARCHAR(256)  DEFAULT NULL,
--     `status_changed_at` DATETIME    DEFAULT NULL,
--     `status_expires_at` DATETIME    DEFAULT NULL,
--     `version`         BIGINT        NOT NULL DEFAULT 0,
--     `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     `gmt_modified`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     `deleted`         TINYINT       NOT NULL DEFAULT 0,
--     PRIMARY KEY (`order_no`),
--     KEY `idx_buyer` (`buyer_id`, `status`),
--     KEY `idx_shop` (`shop_id`, `status`),
--     KEY `idx_status_expire` (`status`, `status_expires_at`),
--     KEY `idx_create` (`gmt_create`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商订单分表';

-- 电商订单扩展表模板
-- CREATE TABLE `order_ecommerce_ext_X` (
--     `order_no`            VARCHAR(64)   NOT NULL COMMENT '订单号',
--     `seckill_activity_id` BIGINT        DEFAULT NULL COMMENT '秒杀活动 ID',
--     `seckill_pipeline`    VARCHAR(16)   DEFAULT NULL COMMENT '秒杀批次',
--     `pre_sale_id`         BIGINT        DEFAULT NULL COMMENT '预售活动 ID',
--     `pre_sale_stage`      VARCHAR(16)   DEFAULT NULL COMMENT '预售阶段(定金/尾款)',
--     `pre_sale_deposit`    DECIMAL(12,2) DEFAULT NULL COMMENT '定金金额',
--     `coupon_id`           BIGINT        DEFAULT NULL COMMENT '优惠券 ID',
--     `coupon_split_amount` DECIMAL(12,2) DEFAULT NULL COMMENT '优惠券分摊金额',
--     `promotion_id`        VARCHAR(64)   DEFAULT NULL COMMENT '营销活动 ID',
--     `delivery_type`       VARCHAR(16)   DEFAULT NULL COMMENT '配送方式',
--     `expected_delivery_time` DATETIME   DEFAULT NULL COMMENT '预计送达时间',
--     `sign_time`           DATETIME      DEFAULT NULL COMMENT '签收时间',
--     PRIMARY KEY (`order_no`),
--     KEY `idx_seckill` (`seckill_activity_id`, `seckill_pipeline`),
--     KEY `idx_pre_sale` (`pre_sale_id`, `pre_sale_stage`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商订单扩展分表';

-- 订单商品行分表模板
-- CREATE TABLE `order_items_X` (
--     `item_id`         BIGINT        AUTO_INCREMENT COMMENT '行 ID',
--     `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号',
--     `business_type`   VARCHAR(16)   NOT NULL DEFAULT 'ecommerce' COMMENT '业务线',
--     `sku_id`          VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
--     `sku_name`        VARCHAR(256)  NOT NULL COMMENT 'SKU 名称（快照）',
--     `sku_spec`        VARCHAR(256)  DEFAULT NULL COMMENT 'SKU 规格',
--     `image_url`       VARCHAR(512)  DEFAULT NULL COMMENT '商品图片',
--     `quantity`        INT           NOT NULL DEFAULT 1,
--     `unit_price`      DECIMAL(12,2) NOT NULL,
--     `total_amount`    DECIMAL(12,2) NOT NULL,
--     `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `pay_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
--     `category_id`     VARCHAR(64)   DEFAULT NULL,
--     `line_type`       VARCHAR(32)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/GIFT/EXCHANGE',
--     `status`          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
--     `promotion_info`  JSON          DEFAULT NULL,
--     `version`         BIGINT        NOT NULL DEFAULT 0,
--     `gmt_create`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     `gmt_modified`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     `deleted`         TINYINT       NOT NULL DEFAULT 0,
--     PRIMARY KEY (`item_id`),
--     KEY `idx_order` (`order_no`),
--     KEY `idx_sku` (`sku_id`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品行分表';

-- 3. 本地生活订单表（预留）
-- DDL for: oms_trade_locallife_{0..1}.order_locallife_{0..7}
-- 结构同 order_ecommerce（减去电商特有字段，增加本地生活字段）

-- 3. 本地生活数据库
CREATE DATABASE IF NOT EXISTS oms_trade_locallife_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS oms_trade_locallife_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 本地生活订单基础表
-- CREATE TABLE `oms_trade_locallife_0`.`order_locallife_0` ( ... ) 等共 2 库 × 8 表

-- 本地生活扩展表
-- CREATE TABLE `order_locallife_ext_X` (
--     `order_no`             VARCHAR(64)   NOT NULL COMMENT '订单号',
--     `verification_code`    VARCHAR(32)   DEFAULT NULL COMMENT '核销码',
--     `verification_status`  VARCHAR(16)   DEFAULT NULL COMMENT '核销状态 UNUSED/USED/EXPIRED',
--     `verification_time`    DATETIME      DEFAULT NULL COMMENT '核销时间',
--     `verifier_id`          VARCHAR(64)   DEFAULT NULL COMMENT '核销人 ID',
--     `store_id`             VARCHAR(64)   DEFAULT NULL COMMENT '门店 ID',
--     `store_name`           VARCHAR(128)  DEFAULT NULL COMMENT '门店名称',
--     `service_time`         DATETIME      DEFAULT NULL COMMENT '预约服务时间',
--     `service_duration`     INT           DEFAULT NULL COMMENT '服务时长(分钟)',
--     `service_address`      VARCHAR(256)  DEFAULT NULL COMMENT '服务地址',
--     `technician_id`        VARCHAR(64)   DEFAULT NULL COMMENT '技师 ID',
--     PRIMARY KEY (`order_no`),
--     KEY `idx_store` (`store_id`),
--     KEY `idx_verification` (`verification_code`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地生活订单扩展分表';

-- 4. B2B 数据库
CREATE DATABASE IF NOT EXISTS oms_trade_b2b_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- B2B 订单基础表
-- CREATE TABLE `oms_trade_b2b_0`.`order_b2b_0` ( ... ) 等共 1 库 × 4 表

-- B2B 扩展表
-- CREATE TABLE `order_b2b_ext_X` (
--     `order_no`              VARCHAR(64)   NOT NULL COMMENT '订单号',
--     `approval_flow_id`      VARCHAR(64)   DEFAULT NULL COMMENT '审批流 ID',
--     `approval_status`       VARCHAR(16)   DEFAULT NULL COMMENT '审批状态 PENDING/APPROVED/REJECTED',
--     `approval_node`         VARCHAR(64)   DEFAULT NULL COMMENT '当前审批节点',
--     `contract_no`           VARCHAR(64)   DEFAULT NULL COMMENT '合同编号',
--     `installment_plan_id`   VARCHAR(64)   DEFAULT NULL COMMENT '分期方案 ID',
--     `installment_count`     INT           DEFAULT NULL COMMENT '分期期数',
--     `company_id`            VARCHAR(64)   DEFAULT NULL COMMENT '企业 ID',
--     `invoice_type`          VARCHAR(16)   DEFAULT NULL COMMENT '发票类型 FULL/SPECIAL/ELECTRONIC',
--     `invoice_title`         VARCHAR(128)  DEFAULT NULL COMMENT '发票抬头',
--     `tax_id`                VARCHAR(64)   DEFAULT NULL COMMENT '税号',
--     `purchase_order_no`     VARCHAR(64)   DEFAULT NULL COMMENT '采购单号',
--     PRIMARY KEY (`order_no`),
--     KEY `idx_company` (`company_id`),
--     KEY `idx_approval` (`approval_flow_id`, `approval_status`),
--     KEY `idx_contract` (`contract_no`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B2B 订单扩展分表';

