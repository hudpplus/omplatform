package com.omplatform.saga.recovery;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.omplatform.saga.repository.SagaRepository;
import com.omplatform.saga.repository.SagaStepRepository;
import java.util.List;

@Component
public class SagaRecoveryJob {

    @Autowired
    private SagaRepository sagaRepository;

    @Autowired
    private SagaStepRepository sagaStepRepository;
    
    @Autowired(required = false)
    private com.omplatform.saga.outbox.OutboxRepository outboxRepository;

    @Scheduled(fixedDelay = 30000)
    public void recover() {
        try {
            int olderThanMinutes = 10;
            java.util.List<com.omplatform.saga.entity.SagaInstanceEntity> stuck = sagaRepository.findStuck(olderThanMinutes, 100);
            if (stuck == null || stuck.isEmpty()) return;
            System.out.println("SagaRecoveryJob: found stuck saga instances=" + stuck.size());
                for (com.omplatform.saga.entity.SagaInstanceEntity s : stuck) {
                    // 标记用于恢复：发一条 outbox 消息（包含 sagaId 和 sagaName），由具体编排器所在服务消费并触发恢复
                    if (outboxRepository != null) {
                        com.omplatform.saga.outbox.OutboxEntity m = new com.omplatform.saga.outbox.OutboxEntity();
                        m.setId(java.util.UUID.randomUUID().toString());
                        m.setTopic("SAGA_RECOVERY_REQUEST");
                        // 包含 sagaName 以便消费端能够定位对应的 SagaDefinition
                        String payload = String.format("{\"sagaId\":\"%s\",\"sagaName\":\"%s\"}",
                                s.getSagaId(), s.getSagaName());
                        m.setPayload(payload);
                        m.setStatus("PENDING");
                        m.setCreatedAt(java.time.LocalDateTime.now());
                        outboxRepository.save(m);
                    }
                }
        } catch (Exception e) {
            System.err.println("SagaRecoveryJob failed: " + e.getMessage());
        }
    }
}

