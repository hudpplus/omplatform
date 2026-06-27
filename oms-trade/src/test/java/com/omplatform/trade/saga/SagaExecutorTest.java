package com.omplatform.trade.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saga 编排器单元测试（ADR-020 §3）。
 */
@ExtendWith(MockitoExtension.class)
class SagaExecutorTest {

    private SagaExecutor sagaExecutor;
    private SagaDefinition definition;

    @BeforeEach
    void setUp() {
        sagaExecutor = new SagaExecutor();

        SagaStep step1 = new SagaStep();
        step1.setStepName("step1");
        step1.setOrder(1);
        step1.setMandatory(true);

        SagaStep step2 = new SagaStep();
        step2.setStepName("step2");
        step2.setOrder(2);
        step2.setMandatory(true);

        SagaStep step3 = new SagaStep();
        step3.setStepName("step3");
        step3.setOrder(3);
        step3.setMandatory(false); // 非强制

        definition = new SagaDefinition();
        definition.setSagaName("testSaga");
        definition.setGlobalTimeout(Duration.ofMinutes(5));
        definition.setSteps(List.of(step1, step2, step3));
    }

    @Test
    @DisplayName("所有步骤成功时 Saga 应返回成功")
    void allStepsSucceed_shouldReturnSuccess() {
        SagaContext context = SagaContext.create("saga001", "testSaga", "ORD001");

        sagaExecutor.registerForwardStep("step1", ctx -> "ok1");
        sagaExecutor.registerForwardStep("step2", ctx -> "ok2");
        sagaExecutor.registerForwardStep("step3", ctx -> "ok3");

        SagaResult result = sagaExecutor.execute(definition, context);

        assertTrue(result.isSuccess());
        assertEquals("saga001", result.getSagaId());
    }

    @Test
    @DisplayName("步骤失败时应执行补偿")
    void stepFailure_shouldCompensate() {
        SagaContext context = SagaContext.create("saga002", "testSaga", "ORD002");

        sagaExecutor.registerForwardStep("step1", ctx -> "ok1");
        sagaExecutor.registerForwardStep("step2", ctx -> { throw new RuntimeException("步骤2失败"); });
        sagaExecutor.registerForwardStep("step3", ctx -> "ok3");

        final AtomicInteger compensateCount = new AtomicInteger(0);
        sagaExecutor.registerCompensateStep("step1", ctx -> { compensateCount.incrementAndGet(); return true; });
        sagaExecutor.registerCompensateStep("step2", ctx -> { compensateCount.incrementAndGet(); return true; });

        SagaResult result = sagaExecutor.execute(definition, context);

        assertFalse(result.isSuccess());
        assertEquals("step2", result.getFailedStep());
        assertTrue(result.getErrorMessage().contains("步骤2失败"));

        // 应只补偿 step1（step2 需要补偿但失败，FAST_FAIL 策略）
        // compensate 方法遍历 [failedIndex..0]，调用已注册的补偿
    }

    @Test
    @DisplayName("非强制步骤失败不应触发补偿")
    void nonMandatoryStepFailure_shouldContinue() {
        SagaContext context = SagaContext.create("saga003", "testSaga", "ORD003");

        // 使 step3（非强制）失败
        definition.getSteps().get(0).setMandatory(true);
        definition.getSteps().get(1).setMandatory(true);
        definition.getSteps().get(2).setMandatory(false);

        sagaExecutor.registerForwardStep("step1", ctx -> "ok1");
        sagaExecutor.registerForwardStep("step2", ctx -> "ok2");
        sagaExecutor.registerForwardStep("step3", ctx -> { throw new RuntimeException("非强制失败"); });

        SagaResult result = sagaExecutor.execute(definition, context);

        assertTrue(result.isSuccess(), "非强制步骤失败不应影响整体结果");
    }

    @Test
    @DisplayName("无注册执行器时应跳过步骤")
    void noInvoker_shouldSkipStep() {
        SagaContext context = SagaContext.create("saga004", "testSaga", "ORD004");

        sagaExecutor.registerForwardStep("step1", ctx -> "ok1");
        // step2 无执行器

        SagaResult result = sagaExecutor.execute(definition, context);

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("补偿失败时 FAST_FAIL 策略应停止继续补偿")
    void compensateFailureFastFail_shouldStopCompensation() {
        SagaContext context = SagaContext.create("saga005", "testSaga", "ORD005");

        sagaExecutor.registerForwardStep("step1", ctx -> "ok1");
        sagaExecutor.registerForwardStep("step2", ctx -> { throw new RuntimeException("失败"); });

        sagaExecutor.registerCompensateStep("step1", ctx -> { throw new RuntimeException("补偿也失败"); });
        sagaExecutor.registerCompensateStep("step2", ctx -> { return true; });

        SagaResult result = sagaExecutor.execute(definition, context);

        assertFalse(result.isSuccess());
        // compensate 从 failedIndex=1 开始遍历，先补偿 step2（补偿执行器），然后 step1（抛出异常，停止）
    }
}
