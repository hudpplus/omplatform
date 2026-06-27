package com.omplatform.common.exception;

import java.io.Serial;

/**
 * 乐观锁冲突异常 —— 状态转换或数据更新时版本不一致。
 * <p>
 * 由调用方捕获后决定重试策略（指数退避最多 3 次）。
 */
public class OptimisticLockException extends BizException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OptimisticLockException(String orderId, Object expected, Object actual) {
        super("OPTIMISTIC_LOCK",
                String.format("乐观锁冲突: order=%s, 期望=%s, 实际=%s", orderId, expected, actual));
    }
}
