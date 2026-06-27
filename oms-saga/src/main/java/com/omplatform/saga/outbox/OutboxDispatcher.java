package com.omplatform.saga.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import java.util.List;

@Component
public class OutboxDispatcher {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 5000)
    public void dispatch() {
        List<OutboxEntity> pending = outboxRepository.findPending(100);
        for (OutboxEntity m : pending) {
            try {
                rocketMQTemplate.convertAndSend(m.getTopic(), m.getPayload());
                m.setStatus("SENT");
                outboxRepository.updateById(m);
            } catch (Exception e) {
                m.setStatus("FAILED");
                outboxRepository.updateById(m);
            }
        }
    }
}

