package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.trade.repository.entity.SagaInstanceEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Saga 实例仓储（oms-trade 本地覆盖）。
 * <p>
 * 与 oms-saga 的 SagaRepository 共存，扩展了 {@link #updateFailedStep} 等方法。
 */
@Repository
public class SagaRepository extends ServiceImpl<SagaInstanceMapper, SagaInstanceEntity> {

    public void createInstance(String sagaId, String sagaName, String orderNo) {
        SagaInstanceEntity e = new SagaInstanceEntity();
        e.setSagaId(sagaId);
        e.setSagaName(sagaName);
        e.setOrderNo(orderNo);
        e.setStatus("INITIATED");
        e.setCurrentStep(0);
        e.setStepCount(0);
        e.setStartedAt(LocalDateTime.now());
        e.setVersion(0L);
        save(e);
    }

    public void updateStatus(String sagaId, String status) {
        SagaInstanceEntity e = baseMapper.selectById(sagaId);
        if (e != null) {
            e.setStatus(status);
            if ("COMPLETED".equals(status) || "COMPENSATED".equals(status)) {
                e.setCompletedAt(LocalDateTime.now());
            }
            baseMapper.updateById(e);
        }
    }

    public void updateFailedStep(String sagaId, String failedStep, String errorMessage) {
        SagaInstanceEntity e = baseMapper.selectById(sagaId);
        if (e != null) {
            e.setFailedStep(failedStep);
            e.setErrorMessage(errorMessage);
            baseMapper.updateById(e);
        }
    }
}
