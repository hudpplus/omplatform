package com.omplatform.trade.es.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omplatform.trade.es.consumer.OrderEsSyncService;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.sharding.BusinessContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ES 按业务线重建索引 XXL-Job — 将指定业务线的 MySQL 数据全量同步到 ES。
 * <p>
 * ADR-017 Phase 2：支持按 business_type 分批重建 ES 索引。
 * <p>
 * <h3>参数格式（XXL-Job 任务参数，JSON）</h3>
 * <pre>
 *   {"businessType": "ecommerce", "dateFrom": "2026-01-01T00:00:00", "dateTo": null}
 * </pre>
 * <ul>
 *   <li><b>businessType</b>（必填）：ecommerce / locallife / b2b</li>
 *   <li><b>dateFrom</b>（可选）：起始创建时间，默认 null（不限）</li>
 *   <li><b>dateTo</b>（可选）：截止创建时间，默认 null（不限）</li>
 * </ul>
 * <p>
 * 在 xxl-job-admin 管理界面添加任务：
 * <ul>
 *   <li>执行器：oms-trade</li>
 *   <li>任务描述：ES 按业务线重建索引</li>
 *   <li>Java 类型：{@code com.omplatform.trade.es.job.EsReindexByBusinessJob.execute}</li>
 * </ul>
 */
@Slf4j
@Component
public class EsReindexByBusinessJob {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEsSyncService syncService;

    @Value("${omplatform.es.full-sync.batch-size:500}")
    private int batchSize;

    @XxlJob("esReindexByBusiness")
    public void execute() {
        // 解析参数
        String param = XxlJobHelper.getJobParam();
        ReindexParam reindexParam = parseParam(param);
        if (reindexParam == null) {
            XxlJobHelper.handleFail("参数解析失败: " + param);
            return;
        }

        String businessType = reindexParam.businessType;
        log.info("开始按业务线重建 ES 索引: businessType={}, dateFrom={}, dateTo={}",
                businessType, reindexParam.dateFrom, reindexParam.dateTo);

        long pageNo = 1;
        long total = 0;
        long startTime = System.currentTimeMillis();

        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getBusinessType, businessType);
        wrapper.eq(OrderEntity::getDeleted, 0);

        if (reindexParam.dateFrom != null) {
            wrapper.ge(OrderEntity::getGmtCreate, reindexParam.dateFrom);
        }
        if (reindexParam.dateTo != null) {
            wrapper.le(OrderEntity::getGmtCreate, reindexParam.dateTo);
        }
        wrapper.orderByAsc(OrderEntity::getGmtCreate);

        while (true) {
            BusinessContext.setAll(businessType, null, null);
            try {
                Page<OrderEntity> page = orderRepository.page(
                        new Page<>(pageNo, batchSize), wrapper);

                if (page.getRecords().isEmpty()) break;

                syncService.syncOrdersBulk(page.getRecords());
                total += page.getRecords().size();
                log.info("重建进度: businessType={}, {}/{}",
                        businessType, total, page.getTotal());

                if (pageNo >= page.getPages()) break;
                pageNo++;
            } finally {
                BusinessContext.clear();
            }
        }

        long cost = System.currentTimeMillis() - startTime;
        String result = String.format("业务线 %s 重建完成: %d 条, 耗时 %dms",
                businessType, total, cost);
        log.info(result);
        XxlJobHelper.handleSuccess(result);
    }

    /**
     * 解析 JSON 格式的任务参数。
     */
    private ReindexParam parseParam(String param) {
        if (param == null || param.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(param);

            String businessType = node.has("businessType")
                    ? node.get("businessType").asText() : null;
            if (businessType == null || businessType.isBlank()) return null;

            LocalDateTime dateFrom = node.has("dateFrom") && !node.get("dateFrom").isNull()
                    ? LocalDateTime.parse(node.get("dateFrom").asText()) : null;
            LocalDateTime dateTo = node.has("dateTo") && !node.get("dateTo").isNull()
                    ? LocalDateTime.parse(node.get("dateTo").asText()) : null;

            return new ReindexParam(businessType, dateFrom, dateTo);
        } catch (Exception e) {
            log.warn("解析 ReindexJob 参数失败: {}", e.getMessage());
            return null;
        }
    }

    private record ReindexParam(String businessType,
                                LocalDateTime dateFrom,
                                LocalDateTime dateTo) {
    }
}
