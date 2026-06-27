package com.omplatform.trade.service;

import com.omplatform.trade.controller.distributed_transaction.tcc.TccParticipant;
import com.omplatform.trade.controller.distributed_transaction.tcc.TccTransactionContext;
import org.springframework.beans.factory.annotation.Autowired;
import com.omplatform.trade.repository.TccParticipantStateRepository;
import com.omplatform.trade.repository.TccTransactionRepository;
import com.omplatform.trade.repository.entity.TccParticipantStateEntity;
import com.omplatform.trade.repository.entity.TccTransactionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简单的 TCC 恢复器：定期扫描 TRIED 状态并尝试执行 Cancel。
 * 注意：这是一个简单示例，生产环境需要更完善的重试/告警策略。
 */
@Slf4j
@Service
public class TccRecoveryService {

    @Autowired
    private TccTransactionRepository txRepository;
    @Autowired
    private TccParticipantStateRepository participantStateRepository;
    @Autowired
    private List<TccParticipant> participants;

    /* public TccRecoveryService(TccTransactionRepository txRepository,
                              TccParticipantStateRepository participantStateRepository,
                              List<TccParticipant> participants) {
        this.txRepository = txRepository;
        this.participantStateRepository = participantStateRepository;
        this.participants = participants;
    } */

    @Scheduled(fixedDelayString = "60000") // every 60s
    public void recover() {
        List<TccParticipantStateEntity> list = participantStateRepository.lambdaQuery()
                .eq(TccParticipantStateEntity::getStatus, "TRIED")
                .list();
        for (TccParticipantStateEntity s : list) {
            try {
                TccTransactionEntity tx = txRepository.getById(s.getTxId());
                if (tx == null) continue;
                // build minimal context
                TccTransactionContext ctx = new TccTransactionContext.Builder()
                        .txId(s.getTxId())
                        .orderNo(s.getTryData())
                        .buyerId(null)
                        .items(java.util.Collections.emptyList())
                        .totalAmount(java.math.BigDecimal.ZERO)
                        .build();

                // find participant bean
                for (TccParticipant p : participants) {
                    if (p.getParticipantId().equals(s.getParticipantId())) {
                        log.info("[TCC-Recover] Cancel retry for txId={}, participant={}", s.getTxId(), s.getParticipantId());
                        boolean ok = p.cancelAction(ctx);
                        if (ok) {
                            s.setStatus("CANCELLED");
                            participantStateRepository.updateById(s);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("[TCC-Recover] recover failed for id={}", s.getId(), e);
            }
        }
    }
}

