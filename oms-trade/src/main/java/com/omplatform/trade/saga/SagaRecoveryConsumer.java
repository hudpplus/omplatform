package com.omplatform.trade.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Saga 恢复消息的消费者。
 * <p>
 * 接收由 `oms-saga` 模块发出的 `SAGA_RECOVERY_REQUEST` outbox 消息，
 * 解析出 sagaId 与 sagaName，定位对应的 `SagaDefinition` 并调用 `SagaExecutor` 的
 * 执行方法以恢复或继续执行该 Saga。
 */
@Component
@RocketMQMessageListener(topic = "SAGA_RECOVERY_REQUEST", consumerGroup = "oms-trade-saga-recovery")
public class SagaRecoveryConsumer implements RocketMQListener<String> {

    @Autowired
    private SagaExecutor sagaExecutor;

    @Autowired
    private com.omplatform.saga.repository.SagaRepository sagaRepository;

    @Autowired(required = false)
    private List<SagaDefinition> sagaDefinitions; // Spring 中所有的 SagaDefinition Bean 列表

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(String message) {
        try {
            Map payload = objectMapper.readValue(message, Map.class);
            String sagaId = payload.get("sagaId") == null ? null : String.valueOf(payload.get("sagaId"));
            String sagaName = payload.get("sagaName") == null ? null : String.valueOf(payload.get("sagaName"));

            // 如果消息中没有 sagaName，则从 DB 中读取实例信息
            com.omplatform.saga.entity.SagaInstanceEntity instance = null;
            if (sagaId != null) {
                try { instance = sagaRepository.getById(sagaId); } catch (Exception ignore) {}
                if (sagaName == null && instance != null) sagaName = instance.getSagaName();
            }

            if (sagaId == null || sagaName == null) {
                // 无效消息，忽略
                return;
            }

            // 在已注册的 SagaDefinition 中找到与 sagaName 匹配的定义
            SagaDefinition def = findDefinitionBySagaName(sagaName);
            if (def == null) {
                // 没有找到对应的定义，可能该服务未注册此 Saga，记录日志并忽略
                System.err.println("SagaRecoveryConsumer: no SagaDefinition for sagaName=" + sagaName);
                return;
            }

            // 构造 SagaContext 并恢复执行（execute 方法会跳过已完成的步骤）
            SagaContext ctx = SagaContext.create(sagaId, sagaName, instance == null ? null : instance.getOrderNo());
            if (instance != null) ctx.setStartedAt(instance.getStartedAt());

            // 调用执行器继续执行/恢复 Saga
            sagaExecutor.execute(def, ctx);

        } catch (Exception e) {
            System.err.println("SagaRecoveryConsumer failed to process message: " + e.getMessage());
        }
    }

    // 根据 sagaName 在 Spring 注入的 SagaDefinition 列表中查找匹配的定义
    private SagaDefinition findDefinitionBySagaName(String sagaName) {
        if (sagaDefinitions == null) return null;
        for (SagaDefinition d : sagaDefinitions) {
            try {
                if (sagaName.equals(d.getSagaName())) return d;
            } catch (Exception ignore) {}
        }
        return null;
    }
}

