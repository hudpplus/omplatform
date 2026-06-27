--
-- ------------------------------------------------------

--
-- Current Database: `oms_trade_ecommerce_0`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_0` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_0`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_1`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_1` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_1`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_2`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_2` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_2`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_3`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_3` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_3`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_4`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_4` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_4`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_5`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_5` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_5`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_6`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_6` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_6`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_ecommerce_7`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_ecommerce_7` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_ecommerce_7`;

--
-- Table structure for table `order_ecommerce_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_ecommerce_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_ecommerce_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `seckill_activity_id` bigint DEFAULT NULL,
  `seckill_pipeline` varchar(16) DEFAULT NULL,
  `pre_sale_id` bigint DEFAULT NULL,
  `pre_sale_stage` varchar(16) DEFAULT NULL,
  `pre_sale_deposit` decimal(12,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_split_amount` decimal(12,2) DEFAULT NULL,
  `promotion_id` varchar(64) DEFAULT NULL,
  `delivery_type` varchar(16) DEFAULT NULL,
  `expected_delivery_time` datetime DEFAULT NULL,
  `sign_time` datetime DEFAULT NULL,
  PRIMARY KEY (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_0`
--

CREATE TABLE IF NOT EXISTS `order_items_0` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_1`
--

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_2`
--

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_3`
--

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_4`
--

CREATE TABLE IF NOT EXISTS `order_items_4` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_5`
--

CREATE TABLE IF NOT EXISTS `order_items_5` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_6`
--

CREATE TABLE IF NOT EXISTS `order_items_6` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_items_7`
--

CREATE TABLE IF NOT EXISTS `order_items_7` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'ecommerce',
  `sku_id` varchar(64) NOT NULL,
  `sku_name` varchar(256) NOT NULL,
  `sku_spec` varchar(256) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `category_id` varchar(64) DEFAULT NULL,
  `line_type` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `promotion_info` json DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_locallife_0`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_locallife_0` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_locallife_0`;

--
-- Table structure for table `order_locallife_0`
--

CREATE TABLE IF NOT EXISTS `order_locallife_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_1`
--

CREATE TABLE IF NOT EXISTS `order_locallife_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_2`
--

CREATE TABLE IF NOT EXISTS `order_locallife_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_3`
--

CREATE TABLE IF NOT EXISTS `order_locallife_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_4`
--

CREATE TABLE IF NOT EXISTS `order_locallife_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_5`
--

CREATE TABLE IF NOT EXISTS `order_locallife_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_6`
--

CREATE TABLE IF NOT EXISTS `order_locallife_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_7`
--

CREATE TABLE IF NOT EXISTS `order_locallife_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_locallife_1`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_locallife_1` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_locallife_1`;

--
-- Table structure for table `order_locallife_0`
--

CREATE TABLE IF NOT EXISTS `order_locallife_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_1`
--

CREATE TABLE IF NOT EXISTS `order_locallife_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_2`
--

CREATE TABLE IF NOT EXISTS `order_locallife_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_3`
--

CREATE TABLE IF NOT EXISTS `order_locallife_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_4`
--

CREATE TABLE IF NOT EXISTS `order_locallife_4` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_5`
--

CREATE TABLE IF NOT EXISTS `order_locallife_5` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_6`
--

CREATE TABLE IF NOT EXISTS `order_locallife_6` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_7`
--

CREATE TABLE IF NOT EXISTS `order_locallife_7` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'locallife',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_4`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_4` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_5`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_5` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_6`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_6` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_locallife_ext_7`
--

CREATE TABLE IF NOT EXISTS `order_locallife_ext_7` (
  `order_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) DEFAULT NULL,
  `verification_status` varchar(16) DEFAULT NULL,
  `verification_time` datetime DEFAULT NULL,
  `verifier_id` varchar(64) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `store_name` varchar(128) DEFAULT NULL,
  `service_time` datetime DEFAULT NULL,
  `service_duration` int DEFAULT NULL,
  `service_address` varchar(256) DEFAULT NULL,
  `technician_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_store` (`store_id`),
  KEY `idx_verification` (`verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Current Database: `oms_trade_b2b_0`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `oms_trade_b2b_0` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `oms_trade_b2b_0`;

--
-- Table structure for table `order_b2b_0`
--

CREATE TABLE IF NOT EXISTS `order_b2b_0` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `company_id` varchar(64) DEFAULT NULL COMMENT '企业 ID',
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'b2b',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_1`
--

CREATE TABLE IF NOT EXISTS `order_b2b_1` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `company_id` varchar(64) DEFAULT NULL COMMENT '企业 ID',
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'b2b',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_2`
--

CREATE TABLE IF NOT EXISTS `order_b2b_2` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `company_id` varchar(64) DEFAULT NULL COMMENT '企业 ID',
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'b2b',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_3`
--

CREATE TABLE IF NOT EXISTS `order_b2b_3` (
  `order_no` varchar(64) NOT NULL,
  `parent_order_no` varchar(64) DEFAULT NULL,
  `buyer_id` varchar(64) NOT NULL,
  `company_id` varchar(64) DEFAULT NULL COMMENT '企业 ID',
  `shop_id` varchar(64) NOT NULL,
  `business_type` varchar(16) NOT NULL DEFAULT 'b2b',
  `status` varchar(32) NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `address_id` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `channel_source` varchar(32) DEFAULT NULL,
  `coupon_instance_id` varchar(64) DEFAULT NULL,
  `pay_channel` varchar(32) DEFAULT NULL,
  `transaction_id` varchar(128) DEFAULT NULL,
  `hold_reason` varchar(256) DEFAULT NULL,
  `frozen_reason` varchar(256) DEFAULT NULL,
  `status_changed_at` datetime DEFAULT NULL,
  `status_expires_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_no`),
  KEY `idx_buyer` (`buyer_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_ext_0`
--

CREATE TABLE IF NOT EXISTS `order_b2b_ext_0` (
  `order_no` varchar(64) NOT NULL,
  `approval_flow_id` varchar(64) DEFAULT NULL,
  `approval_status` varchar(16) DEFAULT NULL,
  `approval_node` varchar(64) DEFAULT NULL,
  `contract_no` varchar(64) DEFAULT NULL,
  `installment_plan_id` varchar(64) DEFAULT NULL,
  `installment_count` int DEFAULT NULL,
  `company_id` varchar(64) DEFAULT NULL,
  `invoice_type` varchar(16) DEFAULT NULL,
  `invoice_title` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `purchase_order_no` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_company` (`company_id`),
  KEY `idx_approval` (`approval_flow_id`,`approval_status`),
  KEY `idx_contract` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_ext_1`
--

CREATE TABLE IF NOT EXISTS `order_b2b_ext_1` (
  `order_no` varchar(64) NOT NULL,
  `approval_flow_id` varchar(64) DEFAULT NULL,
  `approval_status` varchar(16) DEFAULT NULL,
  `approval_node` varchar(64) DEFAULT NULL,
  `contract_no` varchar(64) DEFAULT NULL,
  `installment_plan_id` varchar(64) DEFAULT NULL,
  `installment_count` int DEFAULT NULL,
  `company_id` varchar(64) DEFAULT NULL,
  `invoice_type` varchar(16) DEFAULT NULL,
  `invoice_title` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `purchase_order_no` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_company` (`company_id`),
  KEY `idx_approval` (`approval_flow_id`,`approval_status`),
  KEY `idx_contract` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_ext_2`
--

CREATE TABLE IF NOT EXISTS `order_b2b_ext_2` (
  `order_no` varchar(64) NOT NULL,
  `approval_flow_id` varchar(64) DEFAULT NULL,
  `approval_status` varchar(16) DEFAULT NULL,
  `approval_node` varchar(64) DEFAULT NULL,
  `contract_no` varchar(64) DEFAULT NULL,
  `installment_plan_id` varchar(64) DEFAULT NULL,
  `installment_count` int DEFAULT NULL,
  `company_id` varchar(64) DEFAULT NULL,
  `invoice_type` varchar(16) DEFAULT NULL,
  `invoice_title` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `purchase_order_no` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_company` (`company_id`),
  KEY `idx_approval` (`approval_flow_id`,`approval_status`),
  KEY `idx_contract` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `order_b2b_ext_3`
--

CREATE TABLE IF NOT EXISTS `order_b2b_ext_3` (
  `order_no` varchar(64) NOT NULL,
  `approval_flow_id` varchar(64) DEFAULT NULL,
  `approval_status` varchar(16) DEFAULT NULL,
  `approval_node` varchar(64) DEFAULT NULL,
  `contract_no` varchar(64) DEFAULT NULL,
  `installment_plan_id` varchar(64) DEFAULT NULL,
  `installment_count` int DEFAULT NULL,
  `company_id` varchar(64) DEFAULT NULL,
  `invoice_type` varchar(16) DEFAULT NULL,
  `invoice_title` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `purchase_order_no` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`order_no`),
  KEY `idx_company` (`company_id`),
  KEY `idx_approval` (`approval_flow_id`,`approval_status`),
  KEY `idx_contract` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

