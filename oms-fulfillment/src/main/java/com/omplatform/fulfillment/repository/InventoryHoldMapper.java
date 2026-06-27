package com.omplatform.fulfillment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.fulfillment.repository.entity.InventoryHoldEntity;

/**
 * 库存预占记录 Mapper（ADR-043 §7.2）。
 */
public interface InventoryHoldMapper extends BaseMapper<InventoryHoldEntity> {
}
