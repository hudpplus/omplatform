package com.omplatform.trade.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.common.api.ApiResult;
import com.omplatform.saga.entity.SagaStepLogEntity;
import com.omplatform.saga.repository.IdempotentRecordRepository;
import com.omplatform.saga.repository.SagaRepository;
import com.omplatform.saga.repository.SagaStepRepository;
import com.omplatform.trade.saga.SagaStep.CompensatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga 编排器核心引擎（ADR-020 §3）。
 * <p>
 * 步骤执行优先级：
 * <ol>
 *   <li><b>Lambda 注册</b> — {@link #registerForwardStep}/{@link #registerCompensateStep}，
 *       适合需要组合逻辑的步骤</li>
 *   <li><b>Dubbo 泛化调用</b> — 当 {@link SagaStep#getForwardInterface()} 非空时，
 *       通过 Dubbo GenericService 自动调用，步骤定义可序列化</li>
 * </ol>
 */
@Slf4j
@Component
public class SagaExecutor {

    /** Lambda 注册的正向步骤执行器（优先级最高） */
    private final Map<String, StepInvoker> forwardInvokers = new ConcurrentHashMap<>();
    /** Lambda 注册的补偿步骤执行器 */
    private final Map<String, StepInvoker> compensateInvokers = new ConcurrentHashMap<>();
    /** Dubbo GenericService 缓存（interface → GenericService） */
    private final Map<String, GenericService> genericServiceCache = new ConcurrentHashMap<>();

    @Autowired
    private SagaRepository sagaRepository;

    @Autowired
    private SagaStepRepository sagaStepRepository;

    @Autowired
    private IdempotentRecordRepository idempotentRecordRepository;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 执行器注册 ==========

    public void registerForwardStep(String stepName, StepInvoker invoker) {
        forwardInvokers.put(stepName, invoker);
    }

    public void registerCompensateStep(String stepName, StepInvoker invoker) {
        compensateInvokers.put(stepName, invoker);
    }

    // ========== Saga 执行 ==========

    public SagaResult execute(SagaDefinition definition, SagaContext context) {
        String sagaId = context.getSagaId();
        log.info("Saga 开始: sagaId={}, name={}, businessKey={}",
                sagaId, definition.getSagaName(), context.getBusinessKey());
        try {
            sagaRepository.createInstance(sagaId, definition.getSagaName(), context.getBusinessKey());
        } catch (Exception e) {
            log.warn("创建 saga_instance 失败，继续执行: {}", e.getMessage());
        }

        List<SagaStep> steps = definition.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);

            // 超时检查
            if (context.getStartedAt() != null
                    && Duration.between(context.getStartedAt(), LocalDateTime.now())
                        .compareTo(definition.getGlobalTimeout()) > 0) {
                log.error("Saga 全局超时: sagaId={}, timeout={}", sagaId, definition.getGlobalTimeout());
                compensate(definition, context, i - 1);
                return SagaResult.failed(sagaId, step.getStepName(), "全局超时");
            }

            log.info("  Step[{}/{}]: {} → {}.{}", i + 1, steps.size(),
                    step.getStepName(), step.getForwardInterface() != null
                            ? step.getForwardInterface() : step.getForwardService(),
                    step.getForwardMethod());

            boolean success = false;
            Exception lastError = null;
            int retries = 0;
            int maxRetries = step.getRetryPolicy() != null
                    ? step.getRetryPolicy().getMaxRetries() : 0;

            // 跳过已成功的步骤
            try {
                SagaStepLogEntity existing = sagaStepRepository.findStep(sagaId, step.getStepName());
                if (existing != null && "SUCCEEDED".equals(existing.getStatus())) {
                    log.info("  Step {} 已在 DB 中标记为 SUCCEEDED，跳过", step.getStepName());
                    continue;
                }
            } catch (Exception e) {
                log.warn("检查 saga_step_log 失败，继续执行: {}", e.getMessage());
            }

            while (!success && retries <= maxRetries) {
                try {
                    // 幂等检查
                    String idempotentKey = sagaId + ":" + step.getStepName();
                    boolean acquired = false;
                    try {
                        acquired = idempotentRecordRepository.tryAcquire(idempotentKey, sagaId, step.getStepName());
                    } catch (Exception ex) {
                        log.warn("幂等记录尝试插入失败，继续执行: {}", ex.getMessage());
                        acquired = true;
                    }

                    if (!acquired) {
                        Object prev = readIdempotentResult(idempotentKey, step, sagaId, context);
                        if (prev != null) {
                            context.setStepResult(step.getStepName(), prev);
                            success = true;
                            break;
                        }
                    }

                    persistStepExecuting(sagaId, step);

                    // === 执行步骤 — 双路径 ===
                    Object result = invokeStep(step, context);

                    context.setStepResult(step.getStepName(), result);
                    persistStepSuccess(sagaId, step, idempotentKey, result);

                    success = true;
                    log.info("  Step[{}] 成功: {}", i + 1, step.getStepName());

                } catch (Exception e) {
                    lastError = e;
                    retries++;
                    if (retries <= maxRetries) {
                        Duration backoff = step.getRetryPolicy() != null
                                ? step.getRetryPolicy().getBackoffInterval(retries - 1)
                                : Duration.ofSeconds(1);
                        log.warn("  Step[{}] 重试 {}/{}: {}, 间隔 {}ms",
                                i + 1, retries, maxRetries, e.getMessage(), backoff.toMillis());
                        try { Thread.sleep(backoff.toMillis()); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (!success) {
                log.error("  Step[{}] 失败: {}, error={}", i + 1, step.getStepName(),
                        lastError != null ? lastError.getMessage() : "未知错误");

                if (!step.isMandatory()) {
                    log.warn("  非强制步骤失败，继续执行");
                    continue;
                }

                compensate(definition, context, i);
                try {
                    SagaStepLogEntity failed = sagaStepRepository.findStep(sagaId, step.getStepName());
                    if (failed != null) {
                        failed.setStatus("FAILED");
                        failed.setErrorMessage(lastError != null ? lastError.getMessage() : null);
                        failed.setCompletedAt(LocalDateTime.now());
                        sagaStepRepository.updateById(failed);
                    }
                    sagaRepository.updateStatus(sagaId, "COMPENSATING");
                } catch (Exception ex) {
                    log.warn("记录失败步骤状态失败: {}", ex.getMessage());
                }
                return SagaResult.failed(sagaId, step.getStepName(),
                        lastError != null ? lastError.getMessage() : "未知错误");
            }
        }

        log.info("Saga 完成: sagaId={}", sagaId);
        try { sagaRepository.updateStatus(sagaId, "COMPLETED"); } catch (Exception e) { /* ignore */ }
        return SagaResult.success(sagaId);
    }

    /**
     * 执行步骤 — 双路径路由：
     * <ol>
     *   <li>如果 {@link #forwardInvokers} 中注册了 lambda → 优先使用</li>
     *   <li>如果步骤定义了 {@link SagaStep#getForwardInterface()} → Dubbo 泛化调用</li>
     *   <li>否则跳过，记 warning</li>
     * </ol>
     */
    private Object invokeStep(SagaStep step, SagaContext context) throws Exception {
        // 路径 1: Lambda 注册的执行器
        StepInvoker invoker = forwardInvokers.get(step.getStepName());
        if (invoker != null) {
            return invoker.invoke(context);
        }

        // 路径 2: Dubbo 泛化调用
        if (step.getForwardInterface() != null && step.getForwardMethod() != null) {
            return invokeDubboGeneric(step, context);
        }

        log.warn("  步骤 {} 无 Lambda 执行器，也无 forwardInterface，跳过", step.getStepName());
        return null;
    }

    /**
     * Dubbo 泛化调用 — 通过 {@link GenericService#$invoke} 反射调用远程服务。
     * 参数值从 {@link SagaContext#getStepArg(String)} 按 {@link SagaStep#getForwardParameterKeys()} 读取。
     */
    private Object invokeDubboGeneric(SagaStep step, SagaContext context) {
        GenericService svc = getGenericService(step.getForwardInterface());
        if (svc == null) {
            throw new RuntimeException("无法创建 Dubbo GenericService: " + step.getForwardInterface());
        }

        // 从 context 解析参数值
        List<String> paramKeys = step.getForwardParameterKeys();
        List<String> paramTypes = step.getForwardParameterClassNames();

        Object[] paramValues;
        String[] paramTypesArr;

        if (paramKeys != null && !paramKeys.isEmpty()) {
            paramValues = paramKeys.stream()
                    .map(key -> {
                        Object val = context.getStepArg(key);
                        if (val == null) {
                            // fallback: 尝试用 stepName.key 格式
                            val = context.getStepArg(step.getStepName() + "." + key);
                        }
                        return val;
                    })
                    .toArray();

            paramTypesArr = (paramTypes != null && paramTypes.size() == paramKeys.size())
                    ? paramTypes.toArray(new String[0])
                    : inferParameterTypes(paramValues);
        } else {
            paramValues = new Object[0];
            paramTypesArr = new String[0];
        }

        log.debug("  Dubbo generic invoke: interface={}, method={}, paramTypes={}, values={}",
                step.getForwardInterface(), step.getForwardMethod(),
                Arrays.toString(paramTypesArr), Arrays.toString(paramValues));

        return svc.$invoke(step.getForwardMethod(), paramTypesArr, paramValues);
    }

    /**
     * 获取或创建 Dubbo GenericService。
     */
    private GenericService getGenericService(String interfaceName) {
        return genericServiceCache.computeIfAbsent(interfaceName, name -> {
            ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
            reference.setInterface(name);
            reference.setGeneric("true");
            reference.setTimeout(10_000);
            reference.setRetries(0);
            try {
                GenericService svc = reference.get();
                log.info("Dubbo GenericService 创建成功: interface={}", name);
                return svc;
            } catch (Exception e) {
                log.error("创建 Dubbo GenericService 失败: interface={}, err={}", name, e.getMessage());
                return null;
            }
        });
    }

    /**
     * 当未显式声明参数类型时，从参数值推断类型类名。
     */
    private static String[] inferParameterTypes(Object[] values) {
        return Arrays.stream(values)
                .map(v -> v != null ? v.getClass().getName() : "java.lang.Object")
                .toArray(String[]::new);
    }

    // ========== 补偿 ==========

    /**
     * 逆序执行补偿。
     */
    private void compensate(SagaDefinition definition, SagaContext context, int failedIndex) {
        log.warn("Saga 补偿开始: sagaId={}, failedStep={}", context.getSagaId(), failedIndex);
        context.setCompensating(true);

        List<SagaStep> steps = definition.getSteps();
        for (int i = failedIndex; i >= 0; i--) {
            SagaStep step = steps.get(i);
            CompensatePolicy policy = step.getCompensatePolicy() != null
                    ? step.getCompensatePolicy() : CompensatePolicy.FAIL_FAST;

            try {
                // 尝试 Lambda 注册的补偿执行器
                StepInvoker invoker = compensateInvokers.get(step.getStepName());
                if (invoker != null) {
                    Object res = invokeCompensateWithIdempotent(step, context, invoker);
                    log.info("  补偿 Step[{}] 成功: {}", i + 1, step.getStepName());
                } else if (step.getCompensateInterface() != null && step.getCompensateMethod() != null) {
                    // Dubbo 泛化补偿
                    Object res = invokeDubboCompensateGeneric(step, context);
                    log.info("  补偿 Step[{}] 成功 (generic): {}", i + 1, step.getStepName());
                } else {
                    log.warn("  补偿 Step {} 无补偿执行器，跳过", step.getStepName());
                }
            } catch (Exception e) {
                log.error("  补偿 Step[{}] 失败: {}, error={}", i + 1, step.getStepName(), e.getMessage());

                try {
                    SagaStepLogEntity s = sagaStepRepository.findStep(context.getSagaId(), step.getStepName());
                    if (s != null) {
                        s.setCompensateStatus("COMPENSATE_FAILED");
                        sagaStepRepository.updateById(s);
                    }
                } catch (Exception ex) { /* ignore */ }

                sendToDlq(context, step, e);

                if (policy == CompensatePolicy.FAIL_FAST) {
                    log.error("FAST_FAIL 策略，停止后续补偿");
                    break;
                }
            }
        }
        log.warn("Saga 补偿结束: sagaId={}", context.getSagaId());
    }

    private Object invokeCompensateWithIdempotent(SagaStep step, SagaContext context,
                                                   StepInvoker invoker) throws Exception {
        String compKey = context.getSagaId() + ":compensate:" + step.getStepName();
        boolean acquired = false;
        try {
            acquired = idempotentRecordRepository.tryAcquire(compKey, context.getSagaId(), step.getStepName());
        } catch (Exception ex) {
            acquired = true;
        }

        if (!acquired) {
            try {
                Object prev = idempotentRecordRepository.getPreviousResult(compKey, Object.class);
                log.info("  补偿 Step {} 已由幂等记录处理，跳过", step.getStepName());
                SagaStepLogEntity s = sagaStepRepository.findStep(context.getSagaId(), step.getStepName());
                if (s != null) {
                    s.setCompensateStatus("COMPENSATED");
                    sagaStepRepository.updateById(s);
                }
                return prev;
            } catch (Exception ex) {
                acquired = true;
            }
        }

        Object res = invoker.invoke(context);
        try {
            idempotentRecordRepository.complete(compKey, res);
        } catch (Exception ex) {
            log.warn("更新补偿幂等记录失败: {}", ex.getMessage());
        }
        try {
            SagaStepLogEntity s = sagaStepRepository.findStep(context.getSagaId(), step.getStepName());
            if (s != null) {
                s.setCompensateStatus("COMPENSATED");
                sagaStepRepository.updateById(s);
            }
        } catch (Exception ex) {
            log.warn("写入补偿日志失败: {}", ex.getMessage());
        }
        return res;
    }

    private Object invokeDubboCompensateGeneric(SagaStep step, SagaContext context) {
        GenericService svc = getGenericService(step.getCompensateInterface());
        if (svc == null) {
            throw new RuntimeException("无法创建 Dubbo GenericService: " + step.getCompensateInterface());
        }

        List<String> paramKeys = step.getCompensateParameterKeys();
        List<String> paramTypes = step.getCompensateParameterClassNames();

        Object[] paramValues = (paramKeys != null && !paramKeys.isEmpty())
                ? paramKeys.stream().map(context::getStepArg).toArray()
                : new Object[0];
        String[] paramTypesArr = (paramTypes != null && paramTypes.size() == (paramKeys != null ? paramKeys.size() : 0))
                ? paramTypes.toArray(new String[0])
                : inferParameterTypes(paramValues);

        return svc.$invoke(step.getCompensateMethod(), paramTypesArr, paramValues);
    }

    // ========== 内部 ==========

    /**
     * 读取幂等记录结果，如果存在并有效则返回，否则返回 null。
     */
    private Object readIdempotentResult(String idempotentKey, SagaStep step,
                                         String sagaId, SagaContext context) {
        try {
            Object prev = idempotentRecordRepository.getPreviousResult(idempotentKey, Object.class);
            context.setStepResult(step.getStepName(), prev);
            try {
                SagaStepLogEntity updated = sagaStepRepository.findStep(sagaId, step.getStepName());
                if (updated != null) {
                    updated.setStatus("SUCCEEDED");
                    updated.setCompletedAt(LocalDateTime.now());
                    sagaStepRepository.updateById(updated);
                }
            } catch (Exception ex) { /* ignore */ }
            log.info("  Step {} 已由幂等记录处理，跳过执行", step.getStepName());
            return prev;
        } catch (Exception ex) {
            return null;
        }
    }

    private void persistStepExecuting(String sagaId, SagaStep step) {
        try {
            SagaStepLogEntity s = new SagaStepLogEntity();
            s.setSagaId(sagaId);
            s.setStepName(step.getStepName());
            s.setStepOrder(step.getOrder());
            s.setStatus("EXECUTING");
            s.setStartedAt(LocalDateTime.now());
            sagaStepRepository.save(s);
        } catch (Exception ex) {
            log.warn("写入 saga_step_log 失败: {}", ex.getMessage());
        }
    }

    private void persistStepSuccess(String sagaId, SagaStep step,
                                     String idempotentKey, Object result) {
        try {
            SagaStepLogEntity updated = sagaStepRepository.findStep(sagaId, step.getStepName());
            if (updated != null) {
                updated.setStatus("SUCCEEDED");
                updated.setResponsePayload(result == null ? null : result.toString());
                updated.setCompletedAt(LocalDateTime.now());
                sagaStepRepository.updateById(updated);
            }
        } catch (Exception ex) {
            log.warn("更新 saga_step_log 为 SUCCEEDED 失败: {}", ex.getMessage());
        }

        try {
            idempotentRecordRepository.complete(idempotentKey, result);
        } catch (Exception ex) {
            log.warn("更新幂等记录为 SUCCEEDED 失败: {}", ex.getMessage());
        }
    }

    private void sendToDlq(SagaContext context, SagaStep step, Exception e) {
        try {
            if (rocketMQTemplate != null) {
                Map<String, Object> dlq = new java.util.HashMap<>();
                dlq.put("sagaId", context.getSagaId());
                dlq.put("stepName", step.getStepName());
                dlq.put("error", e.getMessage());
                String payload = objectMapper.writeValueAsString(dlq);
                rocketMQTemplate.syncSend("SAGA_COMPENSATE_DLQ", payload);
            }
        } catch (Exception ex) {
            log.warn("投递 SAGA_COMPENSATE_DLQ 失败: {}", ex.getMessage());
        }
    }

    // ========== 函数式接口 ==========

    @FunctionalInterface
    public interface StepInvoker {
        Object invoke(SagaContext context) throws Exception;
    }
}
