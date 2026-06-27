package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsWarehouseEntity;
import com.omplatform.wms.entity.WmsZoneEntity;
import com.omplatform.wms.mapper.WmsWarehouseMapper;
import com.omplatform.wms.mapper.WmsZoneMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仓库/库区管理服务。
 * <p>
 * 提供仓库和库区的 CRUD 操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsWarehouseService {

    private final WmsWarehouseMapper warehouseMapper;
    private final WmsZoneMapper zoneMapper;

    // ========== 仓库 CRUD ==========

    public List<WmsWarehouseEntity> listWarehouses() {
        return warehouseMapper.selectList(
                Wrappers.<WmsWarehouseEntity>lambdaQuery()
                        .eq(WmsWarehouseEntity::getDeleted, 0)
                        .orderByAsc(WmsWarehouseEntity::getGmtCreate));
    }

    public WmsWarehouseEntity getWarehouse(String warehouseCode) {
        return warehouseMapper.selectById(warehouseCode);
    }

    @Transactional
    public void createWarehouse(WmsWarehouseEntity entity) {
        warehouseMapper.insert(entity);
        log.info("创建仓库: code={}, name={}", entity.getWarehouseCode(), entity.getWarehouseName());
    }

    @Transactional
    public void updateWarehouse(WmsWarehouseEntity entity) {
        warehouseMapper.updateById(entity);
        log.info("更新仓库: code={}", entity.getWarehouseCode());
    }

    // ========== 库区 CRUD ==========

    public List<WmsZoneEntity> listZones(String warehouseCode) {
        return zoneMapper.selectList(
                Wrappers.<WmsZoneEntity>lambdaQuery()
                        .eq(WmsZoneEntity::getWarehouseCode, warehouseCode)
                        .eq(WmsZoneEntity::getDeleted, 0)
                        .orderByAsc(WmsZoneEntity::getZoneCode));
    }

    @Transactional
    public void createZone(WmsZoneEntity entity) {
        zoneMapper.insert(entity);
        log.info("创建库区: code={}, name={}, warehouse={}",
                entity.getZoneCode(), entity.getZoneName(), entity.getWarehouseCode());
    }

    @Transactional
    public void deleteZone(String zoneCode) {
        zoneMapper.deleteById(zoneCode);
        log.info("删除库区: code={}", zoneCode);
    }
}
