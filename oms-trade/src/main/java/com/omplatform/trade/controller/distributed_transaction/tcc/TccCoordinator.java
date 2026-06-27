package com.omplatform.trade.controller.distributed_transaction.tcc;

import com.omplatform.trade.repository.TccParticipantStateRepository;
import com.omplatform.trade.repository.TccTransactionRepository;
import com.omplatform.trade.repository.entity.TccParticipantStateEntity;
import com.omplatform.trade.repository.entity.TccTransactionEntity;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC 执行器 —— 协调所有参与者的 Try → Confirm / Cancel。
 */
@Slf4j
public class TccCoordinator {

    /** 参与者列表（按序执行） */
    private final List<TccParticipant> participants;

    /** 事务上下文（跨参与者共享） */
    private final TccTransactionContext txContext;

    /** 每个参与者的执行状态 */
    private final Map<String, TccStatus> participantStatus = new ConcurrentHashMap<>();

    /** 执行结果 */
    public enum TccStatus { INIT, TRIED, CONFIRMED, CANCELLED }

    private final TccTransactionRepository txRepository;
    private final TccParticipantStateRepository participantStateRepository;

    /**
     * 如果你传入 repository，将在数据库中记录事务和参与者状态；
     * 否则仍以内存为准（兼容示例/测试场景）。
     */
    public TccCoordinator(List<TccParticipant> participants,
                          TccTransactionContext txContext,
                          TccTransactionRepository txRepository,
                          TccParticipantStateRepository participantStateRepository) {
        this.participants = participants;
        this.txContext = txContext;
        this.txRepository = txRepository;
        this.participantStateRepository = participantStateRepository;
        participants.forEach(p -> participantStatus.put(p.getParticipantId(), TccStatus.INIT));

        // persist initial transaction + participant states if repositories provided
        if (this.txRepository != null) {
            TccTransactionEntity tx = new TccTransactionEntity();
            tx.setTxId(txContext.getTxId());
            tx.setOrderNo(txContext.getOrderNo());
            tx.setStatus("INIT");
            tx.setGmtCreate(LocalDateTime.now());
            this.txRepository.save(tx);
        }
        if (this.participantStateRepository != null) {
            for (TccParticipant p : participants) {
                TccParticipantStateEntity s = new TccParticipantStateEntity();
                s.setTxId(txContext.getTxId());
                s.setParticipantId(p.getParticipantId());
                s.setStatus("INIT");
                s.setLastAttempt(LocalDateTime.now());
                this.participantStateRepository.save(s);
            }
        }
    }

    /**
     * 执行 TCC 事务。
     *
     * @return true=全部成功, false=已回滚
     */
    public boolean execute() {
        log.info("[TCC] 开始执行, participants={}", participants.size());

        // Phase 1: Try —— 按序预留资源
        int triedCount = 0;
        for (TccParticipant p : participants) {
            try {
                log.info("[TCC] Try: participant={}", p.getParticipantId());
                boolean ok = p.tryAction(txContext);
                if (!ok) {
                    log.warn("[TCC] Try 失败: participant={}", p.getParticipantId());
                    break; // 不继续 Try，进入 Cancel 阶段
                }
                participantStatus.put(p.getParticipantId(), TccStatus.TRIED);
                // persist participant tried state
                if (txRepository != null && participantStateRepository != null) {
                    TccParticipantStateEntity s = new TccParticipantStateEntity();
                    s.setTxId(txContext.getTxId());
                    s.setParticipantId(p.getParticipantId());
                    s.setStatus("TRIED");
                    s.setTryData(txContext.getOrderNo());
                    s.setLastAttempt(LocalDateTime.now());
                    participantStateRepository.upsertState(s);
                }
                triedCount++;
            } catch (Exception e) {
                log.error("[TCC] Try 异常: participant={}", p.getParticipantId(), e);
                break; // Try 异常 → 进入 Cancel
            }
        }

        // 判断 Try 阶段是否全部成功
        if (triedCount == participants.size()) {
            // Phase 2: Confirm —— 全部确认
            log.info("[TCC] Try 全部成功, 开始 Confirm");
            for (TccParticipant p : participants) {
                try {
                    p.confirmAction(txContext);
                    participantStatus.put(p.getParticipantId(), TccStatus.CONFIRMED);
                    log.info("[TCC] Confirmed: participant={}", p.getParticipantId());
                    if (txRepository != null && participantStateRepository != null) {
                        // update participant state to CONFIRMED (upsert)
                        TccParticipantStateEntity q = new TccParticipantStateEntity();
                        q.setTxId(txContext.getTxId());
                        q.setParticipantId(p.getParticipantId());
                        q.setStatus("CONFIRMED");
                        q.setLastAttempt(LocalDateTime.now());
                        participantStateRepository.upsertState(q);
                    }
                } catch (Exception e) {
                    log.error("[TCC] Confirm 失败需要人工处理: participant={}",
                            p.getParticipantId(), e);
                }
            }
            if (txRepository != null) {
                TccTransactionEntity tx = new TccTransactionEntity();
                tx.setTxId(txContext.getTxId());
                tx.setStatus("CONFIRMED");
                txRepository.updateById(tx);
            }
            log.info("[TCC] 全部 Confirm 完成");
            return true;
        } else {
            // Phase 2 (alternative): Cancel —— 逆序取消已 Try 的参与者
            log.info("[TCC] Try 部分失败, 开始 Cancel");
            for (int i = triedCount - 1; i >= 0; i--) {
                TccParticipant p = participants.get(i);
                try {
                    p.cancelAction(txContext);
                    participantStatus.put(p.getParticipantId(), TccStatus.CANCELLED);
                    log.info("[TCC] Cancelled: participant={}", p.getParticipantId());
                    if (txRepository != null && participantStateRepository != null) {
                        TccParticipantStateEntity q = new TccParticipantStateEntity();
                        q.setTxId(txContext.getTxId());
                        q.setParticipantId(p.getParticipantId());
                        q.setStatus("CANCELLED");
                        q.setLastAttempt(LocalDateTime.now());
                        participantStateRepository.upsertState(q);
                    }
                } catch (Exception e) {
                    log.error("[TCC] Cancel 失败需要重试: participant={}",
                            p.getParticipantId(), e);
                }
            }
            if (txRepository != null) {
                TccTransactionEntity tx = new TccTransactionEntity();
                tx.setTxId(txContext.getTxId());
                tx.setStatus("CANCELLED");
                txRepository.updateById(tx);
            }
            log.info("[TCC] 全部 Cancel 完成");
            return false;
        }
    }

    /** 获取参与者状态快照（用于监控/恢复） */
    public Map<String, TccStatus> getStatusSnapshot() {
        return Map.copyOf(participantStatus);
    }
}
