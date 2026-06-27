package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsOutboundOrderEntity;
import com.omplatform.wms.entity.WmsPackingEntity;
import com.omplatform.wms.mapper.WmsOutboundOrderMapper;
import com.omplatform.wms.mapper.WmsPackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打包管理服务。
 * <p>
 * 拣货完成后，将商品打包并记录包裹信息（箱号/重量/尺寸）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsPackingService {

    private final WmsPackingMapper packingMapper;
    private final WmsOutboundOrderMapper outboundOrderMapper;

    /**
     * 记录一个包裹的打包信息。
     *
     * @param outboundNo  出库单号
     * @param taskNo      拣货任务号（可选）
     * @param packageNo   包裹号/箱号
     * @param skuId       商品 SKU
     * @param skuName     商品名称
     * @param quantity    数量
     * @param weight      重量(kg)
     * @param packageType 包装类型 BOX/BAG/PALLET
     * @param operator    操作人
     * @return 打包记录
     */
    @Transactional
    public WmsPackingEntity pack(String outboundNo, String taskNo, String packageNo,
                                  String skuId, String skuName, int quantity,
                                  BigDecimal weight, BigDecimal length,
                                  BigDecimal width, BigDecimal height,
                                  String packageType, String operator) {
        WmsPackingEntity packing = new WmsPackingEntity();
        packing.setOutboundNo(outboundNo);
        packing.setTaskNo(taskNo);
        packing.setPackageNo(packageNo);
        packing.setSkuId(skuId);
        packing.setSkuName(skuName);
        packing.setQuantity(quantity);
        packing.setWeight(weight);
        packing.setLength(length);
        packing.setWidth(width);
        packing.setHeight(height);
        packing.setPackageType(packageType != null ? packageType : "BOX");
        packing.setOperator(operator);
        packing.setPackedAt(LocalDateTime.now());
        packingMapper.insert(packing);

        // 更新出库单状态
        outboundOrderMapper.update(null,
                Wrappers.<WmsOutboundOrderEntity>lambdaUpdate()
                        .eq(WmsOutboundOrderEntity::getOutboundNo, outboundNo)
                        .set(WmsOutboundOrderEntity::getStatus, "PACKED"));

        log.info("打包记录: outbound={}, pkg={}, sku={}, qty={}, op={}",
                outboundNo, packageNo, skuId, quantity, operator);
        return packing;
    }

    /**
     * 查询出库单的所有包裹。
     */
    public List<WmsPackingEntity> getPackingByOutbound(String outboundNo) {
        return packingMapper.findByOutboundNo(outboundNo);
    }
}
