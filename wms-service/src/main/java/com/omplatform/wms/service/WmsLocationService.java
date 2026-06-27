package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsLocationEntity;
import com.omplatform.wms.mapper.WmsLocationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库位管理服务。
 * <p>
 * 提供库位 CRUD 和智能分配策略（上架推荐、拣货推荐）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsLocationService {

    private final WmsLocationMapper locationMapper;

    // ========== 库位 CRUD ==========

    public List<WmsLocationEntity> listLocations(String warehouseCode, String zoneCode) {
        var wrapper = Wrappers.<WmsLocationEntity>lambdaQuery()
                .eq(WmsLocationEntity::getDeleted, 0);
        if (warehouseCode != null) {
            wrapper.eq(WmsLocationEntity::getWarehouseCode, warehouseCode);
        }
        if (zoneCode != null) {
            wrapper.eq(WmsLocationEntity::getZoneCode, zoneCode);
        }
        wrapper.orderByAsc(WmsLocationEntity::getPutawaySeq);
        return locationMapper.selectList(wrapper);
    }

    public WmsLocationEntity getLocation(String locationCode) {
        return locationMapper.selectById(locationCode);
    }

    @Transactional
    public void createLocation(WmsLocationEntity entity) {
        entity.setStatus("EMPTY");
        locationMapper.insert(entity);
        log.info("创建库位: code={}, zone={}, warehouse={}, type={}",
                entity.getLocationCode(), entity.getZoneCode(),
                entity.getWarehouseCode(), entity.getLocationType());
    }

    @Transactional
    public void createLocationBatch(List<WmsLocationEntity> locations) {
        for (WmsLocationEntity loc : locations) {
            loc.setStatus("EMPTY");
        }
        // MyBatis-Plus 没有批量 insert，逐条插入
        for (WmsLocationEntity loc : locations) {
            locationMapper.insert(loc);
        }
        log.info("批量创建库位: count={}", locations.size());
    }

    @Transactional
    public void updateLocationStatus(String locationCode, String status) {
        locationMapper.update(null,
                Wrappers.<WmsLocationEntity>lambdaUpdate()
                        .eq(WmsLocationEntity::getLocationCode, locationCode)
                        .set(WmsLocationEntity::getStatus, status));
    }

    // ========== 库位分配策略 ==========

    /**
     * 推荐上架库位（按 putaway_seq 优先级）。
     * <p>
     * 优先推荐同一 SKU 已存在的拣货位（补货），其次推荐空库位。
     */
    public List<WmsLocationEntity> suggestPutawayLocations(
            String warehouseCode, String skuId, String locationType, int limit) {
        // 先找已有该 SKU 的库位（允许混放）
        List<WmsLocationEntity> existing = locationMapper.findPickLocationsBySku(warehouseCode, skuId);
        if (!existing.isEmpty()) {
            return existing.subList(0, Math.min(existing.size(), limit));
        }
        // 找不到则找空库位
        return locationMapper.findAvailableLocations(warehouseCode, locationType, limit);
    }

    /**
     * 查询拣货库位（按 pick_seq + FEFO 排序）。
     */
    public List<WmsLocationEntity> suggestPickLocations(String warehouseCode, String skuId) {
        return locationMapper.findPickLocationsBySku(warehouseCode, skuId);
    }
}
