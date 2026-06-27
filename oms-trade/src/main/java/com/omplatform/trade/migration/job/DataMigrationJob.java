package com.omplatform.trade.migration.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.trade.migration.service.DataMigrationService;
import com.omplatform.trade.migration.service.DataMigrationService.MigrationResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 数据迁移 XXL-Job — 将旧 {@code order} 表中的数据迁移到 ADR-017 新分片表。
 * <p>
 * <h3>参数格式（JSON）</h3>
 * <pre>
 *   {"businessType": "ecommerce", "batchSize": 500, "maxPages": null}
 * </pre>
 * <ul>
 *   <li><b>businessType</b>（可选，默认 ecommerce）：业务线</li>
 *   <li><b>batchSize</b>（可选，默认 500）：每批行数</li>
 *   <li><b>maxPages</b>（可选，默认 null=无限制）：最大页数（用于测试）</li>
 * </ul>
 * <p>
 * 幂等：重复执行时跳过已存在的订单（按 order_no）。
 * <p>
 * xxl-job-admin 配置：
 * <ul>
 *   <li>执行器：oms-trade</li>
 *   <li>任务描述：数据迁移 — 旧 order → order_ecommerce</li>
 *   <li>Java 类型：{@code com.omplatform.trade.migration.job.DataMigrationJob.execute}</li>
 * </ul>
 */
@Slf4j
@Component
public class DataMigrationJob {

    @Autowired
    private DataMigrationService migrationService;

    @Autowired
    private ObjectMapper objectMapper;

    @XxlJob("dataMigration")
    public void execute() {
        String param = XxlJobHelper.getJobParam();
        JobParam jobParam = parseParam(param);

        String businessType = jobParam != null ? jobParam.businessType : "ecommerce";
        int batchSize = jobParam != null ? jobParam.batchSize : 500;
        Integer maxPages = jobParam != null ? jobParam.maxPages : null;

        XxlJobHelper.log("开始数据迁移: businessType={}, batchSize={}, maxPages={}",
                businessType, batchSize, maxPages);

        MigrationResult result = migrationService.migrateOrders(businessType, batchSize, maxPages);

        String summary = result.summary();
        XxlJobHelper.log(summary);

        if (result.getFailedOrders() > 0) {
            XxlJobHelper.handleSuccess(summary + "（有 " + result.getFailedOrders() + " 条失败）");
        } else {
            XxlJobHelper.handleSuccess(summary);
        }
    }

    private JobParam parseParam(String param) {
        if (param == null || param.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(param);
            String businessType = node.has("businessType") ? node.get("businessType").asText() : null;
            int batchSize = node.has("batchSize") ? node.get("batchSize").asInt() : 500;
            Integer maxPages = node.has("maxPages") && !node.get("maxPages").isNull()
                    ? node.get("maxPages").asInt() : null;
            return new JobParam(businessType, batchSize, maxPages);
        } catch (Exception e) {
            XxlJobHelper.log("参数解析失败: {}", e.getMessage());
            return null;
        }
    }

    private record JobParam(String businessType, int batchSize, Integer maxPages) {
    }
}
