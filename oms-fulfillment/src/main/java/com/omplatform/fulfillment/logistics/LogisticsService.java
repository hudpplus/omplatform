package com.omplatform.fulfillment.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 物流服务。
 * <p>
 * 对接三方物流平台（顺丰/菜鸟）。
 */
@Slf4j
@Service
public class LogisticsService {

    /**
     * 创建物流单。
     *
     * @param orderNo        订单号
     * @param logisticsCompany 物流公司
     * @return 物流单号
     */
    public String createShipment(String orderNo, String logisticsCompany) {
        log.info("创建物流单: orderNo={}, company={}", orderNo, logisticsCompany);
        return "SF" + System.currentTimeMillis();
    }

    /**
     * 查询物流轨迹。
     */
    public String queryTracking(String logisticsNo) {
        log.debug("查询物流: no={}", logisticsNo);
        return "[已揽收, 运输中, 已签收]";
    }
}
