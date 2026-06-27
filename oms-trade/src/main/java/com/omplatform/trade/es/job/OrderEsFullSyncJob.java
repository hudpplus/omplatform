package com.omplatform.trade.es.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omplatform.trade.es.consumer.OrderEsSyncService;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ES 全量同步 XXL-Job — 将 MySQL 现有订单数据批量同步到 ES。
 * <p>
 * 在 xxl-job-admin 管理界面添加任务：
 * <ul>
 *   <li>执行器：oms-trade</li>
 *   <li>任务描述：ES 全量同步</li>
 *   <li>Cron：0 0 3 * * ?（每天凌晨 3 点）或手动执行一次</li>
 *   <li>Java 类型：{@code com.omplatform.trade.es.job.OrderEsFullSyncJob.execute}</li>
 * </ul>
 * <p>
 * 首次部署时建议手动执行一次全量同步，之后通过 RocketMQ 事件消费者
 * {@code OrderEsSyncConsumer} 增量同步。
 */
@Slf4j
@Component
public class OrderEsFullSyncJob {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEsSyncService syncService;

    @Value("${omplatform.es.full-sync.batch-size:500}")
    private int batchSize;

    /**
     * XXL-Job 任务：ES 全量同步。
     * <p>
     * 在 xxl-job-admin 中配置调度策略（Cron 或手动触发）。
     */
    @XxlJob("esFullSync")
    public void execute() {
        long pageNo = 1;
        long total = 0;
        long startTime = System.currentTimeMillis();

        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getDeleted, 0);
        wrapper.orderByAsc(OrderEntity::getGmtCreate);

        while (true) {
            Page<OrderEntity> page = orderRepository.page(
                    new Page<>(pageNo, batchSize), wrapper);

            if (page.getRecords().isEmpty()) break;

            syncService.syncOrdersBulk(page.getRecords());
            total += page.getRecords().size();
            log.debug("全量同步进度: {}/{}", total, page.getTotal());

            if (pageNo >= page.getPages()) break;
            pageNo++;
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("全量同步结束: 共 {} 条, 耗时 {}ms", total, cost);
        XxlJobHelper.handleSuccess("同步 " + total + " 条, 耗时 " + cost + "ms");
    }
}
