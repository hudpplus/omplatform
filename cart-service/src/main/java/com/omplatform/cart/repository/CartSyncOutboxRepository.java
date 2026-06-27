package com.omplatform.cart.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.cart.repository.entity.CartSyncOutboxEntity;
import com.omplatform.cart.repository.mapper.CartSyncOutboxMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车同步发件箱 Repository。
 */
@Repository
public class CartSyncOutboxRepository
        extends ServiceImpl<CartSyncOutboxMapper, CartSyncOutboxEntity> {

    private static final int MAX_RETRY = 3;

    /**
     * 查询待同步的记录，按创建时间升序，最多 {@code limit} 条。
     */
    public List<CartSyncOutboxEntity> findPending(int limit) {
        return lambdaQuery()
                .eq(CartSyncOutboxEntity::getStatus, "PENDING")
                .orderByAsc(CartSyncOutboxEntity::getCreatedAt)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 标记为同步成功。
     */
    public void markDone(String id) {
        lambdaUpdate()
                .eq(CartSyncOutboxEntity::getId, id)
                .set(CartSyncOutboxEntity::getStatus, "DONE")
                .set(CartSyncOutboxEntity::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    /**
     * 标记为同步失败，超过最大重试次数则标记 FAILED 不再重试。
     *
     * @return true 如果已标记为 FAILED（不再重试），false 仍可继续重试
     */
    public boolean markFailed(String id, int currentRetryCount) {
        int nextRetry = currentRetryCount + 1;
        if (nextRetry >= MAX_RETRY) {
            lambdaUpdate()
                    .eq(CartSyncOutboxEntity::getId, id)
                    .set(CartSyncOutboxEntity::getStatus, "FAILED")
                    .set(CartSyncOutboxEntity::getRetryCount, nextRetry)
                    .set(CartSyncOutboxEntity::getUpdatedAt, LocalDateTime.now())
                    .update();
            return true; // 不再重试
        }
        lambdaUpdate()
                .eq(CartSyncOutboxEntity::getId, id)
                .set(CartSyncOutboxEntity::getRetryCount, nextRetry)
                .set(CartSyncOutboxEntity::getUpdatedAt, LocalDateTime.now())
                .update();
        return false; // 下次继续重试
    }

    /**
     * 清理已完成的旧记录（保留 N 天内的 DONE 记录）。
     *
     * @return 删除的记录数
     */
    public int cleanDoneBefore(LocalDateTime cutoff) {
        return getBaseMapper().delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartSyncOutboxEntity>()
                        .eq(CartSyncOutboxEntity::getStatus, "DONE")
                        .lt(CartSyncOutboxEntity::getCreatedAt, cutoff));
    }
}
