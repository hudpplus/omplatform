package com.omplatform.saga.idempotent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Simple AOP-based idempotent interceptor.
 * Expects methods to be called with a ThreadLocal-propagated SagaContext or with first arg Map extras containing sagaId/stepName.
 */
@Aspect
@Component
public class IdempotentInterceptor {

    @Autowired
    private com.omplatform.saga.repository.IdempotentRecordRepository idempotentRecordRepository;

    @Around("execution(* com.omplatform..*Service.*(..))")
    public Object aroundService(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        String sagaId = null, stepName = null;
        if (args != null && args.length > 0) {
            for (Object a : args) {
                if (a instanceof java.util.Map) {
                    java.util.Map m = (java.util.Map) a;
                    if (m.containsKey("sagaId")) sagaId = String.valueOf(m.get("sagaId"));
                    if (m.containsKey("stepName")) stepName = String.valueOf(m.get("stepName"));
                } else if (a != null) {
                    // Avoid compile-time dependency on other modules: use reflection to detect
                    // TransitionContext-like objects with getExtras()
                    try {
                        Class<?> clazz = a.getClass();
                        String cn = clazz.getName();
                        if (cn.endsWith(".TransitionContext")) {
                            try {
                                java.lang.reflect.Method m = clazz.getMethod("getExtras");
                                Object extras = m.invoke(a);
                                if (extras instanceof java.util.Map) {
                                    java.util.Map em = (java.util.Map) extras;
                                    if (em.containsKey("sagaId")) sagaId = String.valueOf(em.get("sagaId"));
                                    if (em.containsKey("stepName")) stepName = String.valueOf(em.get("stepName"));
                                }
                            } catch (NoSuchMethodException ignore) {}
                        } else if (cn.endsWith(".SagaContext")) {
                            try {
                                java.lang.reflect.Method m2 = clazz.getMethod("getSagaId");
                                Object sid = m2.invoke(a);
                                if (sid != null) sagaId = String.valueOf(sid);
                            } catch (NoSuchMethodException ignore) {}
                        }
                    } catch (Exception ex) {
                        // swallow reflection errors — interceptor must not break business logic
                    }
                }
            }
        }
        if (sagaId != null && stepName != null) {
            String key = sagaId + ":" + stepName;
            boolean acquired = idempotentRecordRepository.tryAcquire(key, sagaId, stepName);
            if (!acquired) {
                Object prev = idempotentRecordRepository.getPreviousResult(key, Object.class);
                return prev;
            }
            Object res = pjp.proceed();
            idempotentRecordRepository.complete(key, res);
            return res;
        }

        return pjp.proceed();
    }
}

