package com.omplatform.cart.worker;

import com.omplatform.cart.domain.CartManager;
import com.omplatform.cart.repository.CartSyncOutboxRepository;
import com.omplatform.cart.repository.entity.CartSyncOutboxEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车 Redis 同步 Worker（Transactional Outbox 消费者）。
 * <p>
 * 轮询 cart_sync_outbox 表中 PENDING 记录，从 DB 全量刷新 Redis 购物车数据。
 * 与业务写入操作在同一 DB 事务中写入 outbox，保证最终一致性。
 * <p>
 * 轮询间隔 2 秒，每次最多处理 50 条。
 */
@Slf4j
@Component
public class CartSyncWorker {

    private static final int BATCH_SIZE = 50;
    private static final int CLEANUP_DAYS = 1;

    @Autowired
    private CartSyncOutboxRepository outboxRepository;
    @Autowired
    private CartManager cartManager;

    /**
     * 每 2 秒轮询一次待同步记录，从 DB 全量刷新 Redis。
     */
    @Scheduled(fixedDelay = 2000)
    public void syncPending() {
        List<CartSyncOutboxEntity> pending = outboxRepository.findPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox Worker: 发现 {} 条待同步记录", pending.size());

        for (CartSyncOutboxEntity record : pending) {
            try {
                cartManager.repairFromDb(record.getCartId());
                outboxRepository.markDone(record.getId());
                log.debug("Outbox sync done: cartId={}, recordId={}",
                        record.getCartId(), record.getId());
            } catch (Exception e) {
                log.error("Outbox sync 失败: cartId={}, recordId={}, retryCount={}, error={}",
                        record.getCartId(), record.getId(), record.getRetryCount(), e.getMessage(), e);
                boolean isFailed = outboxRepository.markFailed(record.getId(), record.getRetryCount());
                if (isFailed) {
                    log.warn("Outbox sync 已达最大重试次数，标记 FAILED: cartId={}, recordId={}",
                            record.getCartId(), record.getId());
                }
            }
        }
    }

    /**
     * 每小时清理已完成超过 1 天的同步记录。
     */
    @Scheduled(fixedDelay = 3600_000)
    public void cleanDone() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(CLEANUP_DAYS);
        int removed = outboxRepository.cleanDoneBefore(cutoff);
        if (removed > 0) {
            log.info("Outbox cleanup: 清理 {} 条 DONE 记录（< {}）", removed, cutoff);
        }
    }
}
