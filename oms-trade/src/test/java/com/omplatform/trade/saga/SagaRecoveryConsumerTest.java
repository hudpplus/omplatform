package com.omplatform.trade.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.saga.entity.SagaInstanceEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

public class SagaRecoveryConsumerTest {

    @Test
    public void testOnMessageTriggersExecutor() throws Exception {
        SagaRecoveryConsumer consumer = new SagaRecoveryConsumer();

        // Dummy saga repository that returns an instance with sagaName
        com.omplatform.saga.repository.SagaRepository repo = new com.omplatform.saga.repository.SagaRepository() {
            @Override
            public SagaInstanceEntity getById(java.io.Serializable id) {
                SagaInstanceEntity e = new SagaInstanceEntity();
                e.setSagaId(String.valueOf(id));
                e.setSagaName("createOrder");
                e.setBusinessKey("ORD-TEST");
                e.setStartedAt(LocalDateTime.now().minusMinutes(20));
                return e;
            }
        };

        // Dummy saga executor that records invocation
        final boolean[] called = {false};
        SagaExecutor executor = new SagaExecutor() {
            @Override
            public SagaResult execute(SagaDefinition definition, SagaContext context) {
                called[0] = true;
                return SagaResult.success(context.getSagaId());
            }
        };

        // Provide the standard createOrder SagaDefinition
        CreateOrderSagaDefinition cfg = new CreateOrderSagaDefinition();
        SagaDefinition def = cfg.createOrderSaga();

        // inject dependencies
        com.omplatform.trade.service.atomic.TestUtils.setField(consumer, "sagaRepository", repo);
        com.omplatform.trade.service.atomic.TestUtils.setField(consumer, "sagaExecutor", executor);
        com.omplatform.trade.service.atomic.TestUtils.setField(consumer, "sagaDefinitions", List.of(def));

        ObjectMapper om = new ObjectMapper();
        String msg = om.writeValueAsString(java.util.Map.of("sagaId", "saga-abc", "sagaName", "createOrder"));

        consumer.onMessage(msg);

        Assertions.assertTrue(called[0], "SagaExecutor should be invoked by recovery consumer");
    }
}


