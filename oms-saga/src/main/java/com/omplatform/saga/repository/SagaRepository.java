package com.omplatform.saga.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.saga.entity.SagaInstanceEntity;
import com.omplatform.saga.repository.mapper.SagaInstanceMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saga 实例仓储（oms-saga 共享库）。
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

    /**
     * 查找卡住的 Saga 实例。
     */
    public List<SagaInstanceEntity> findStuck(int olderThanMinutes, int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(olderThanMinutes);
        return list(lambdaQuery()
                .in(SagaInstanceEntity::getStatus, "INITIATED", "IN_PROGRESS", "COMPENSATING")
                .lt(SagaInstanceEntity::getGmtModified, cutoff)
                .last("LIMIT " + limit)
        );
    }
}
