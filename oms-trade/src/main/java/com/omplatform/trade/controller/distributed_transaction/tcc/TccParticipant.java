package com.omplatform.trade.controller.distributed_transaction.tcc;

/**
 * TCC 参与者接口 —— 每个需要事务保护的资源实现此接口。
 */
public interface TccParticipant {
    /** 全局唯一参与者 ID */
    String getParticipantId();

    /** Phase 1: 预留资源（try） */
    boolean tryAction(TccTransactionContext ctx);

    /** Phase 2-成功: 确认使用资源 */
    boolean confirmAction(TccTransactionContext ctx);

    /** Phase 2-失败: 释放预留资源 */
    boolean cancelAction(TccTransactionContext ctx);
}

