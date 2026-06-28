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
import java.util.List;

/**
 * ES 增量补偿同步 XXL-Job — 补偿 RocketMQ 增量同步可能遗漏的订单变更。
 * <p>
 * 每天凌晨按业务线（ecommerce / locallife / b2b）分别扫描最近 24h 内
 * {@code gmt_modified} 有变更的订单，确保 ES 与 MySQL 最终一致。
 * RocketMQ 增量同步是主路径，此 Job 仅作为兜底补偿。
 * <p>
 * 在 xxl-job-admin 管理界面添加任务：
 * <ul>
 *   <li>执行器：oms-trade</li>
 *   <li>任务描述：ES 增量补偿同步</li>
 *   <li>Cron：0 0 3 * * ?（每天凌晨 3 点）</li>
 *   <li>Java 类型：{@code com.omplatform.trade.es.job.OrderEsFullSyncJob.execute}</li>
 * </ul>
 * <p>
 * 首次部署或字段升级后，手动调用 {@code EsReindexByBusinessJob} 做一次全量重建。
 * XXL-Job 任务参数示例：{"businessType": "ecommerce", "dateFrom": "2026-01-01T00:00:00"}
 */
@Slf4j
@Component
public class OrderEsFullSyncJob {

    private static final List<String> BUSINESS_TYPES = List.of("ecommerce", "locallife", "b2b");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEsSyncService syncService;

    @Value("${omplatform.es.full-sync.batch-size:500}")
    private int batchSize;

    /**
     * XXL-Job 任务：ES 增量补偿同步。
     * <p>
     * 在 xxl-job-admin 中配置调度策略（Cron 或手动触发）。
     */
    @XxlJob("esFullSync")
    public void execute() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long totalAll = 0;
        long startTime = System.currentTimeMillis();

        for (String businessType : BUSINESS_TYPES) {
            long total = syncBusiness(businessType, since);
            totalAll += total;
            log.info("增量补偿同步完成: businessType={}, 共 {} 条", businessType, total);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("增量补偿同步结束: 三条业务线共 {} 条, 耗时 {}ms", totalAll, cost);
        XxlJobHelper.handleSuccess("增量补偿 " + totalAll + " 条, 耗时 " + cost + "ms");
    }

    private long syncBusiness(String businessType, LocalDateTime since) {
        long pageNo = 1;
        long total = 0;

        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getDeleted, 0)
               .ge(OrderEntity::getGmtModified, since)
               .orderByAsc(OrderEntity::getGmtCreate);

        while (true) {
            // 设置业务线上下文 → DynamicTableName 改写 order_ecommerce → order_{businessType}
            BusinessContext.setAll(businessType, null, null);
            try {
                Page<OrderEntity> page = orderRepository.page(
                        new Page<>(pageNo, batchSize), wrapper);

                if (page.getRecords().isEmpty()) break;

                syncService.syncOrdersBulk(page.getRecords());
                total += page.getRecords().size();

                if (pageNo >= page.getPages()) break;
                pageNo++;
            } finally {
                BusinessContext.clear();
            }
        }
        return total;
    }
}
