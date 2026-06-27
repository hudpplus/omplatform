package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.trade.repository.entity.SagaStepLogEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SagaStepRepository extends ServiceImpl<SagaStepLogMapper, SagaStepLogEntity> {

    public SagaStepLogEntity findStep(String sagaId, String stepName) {
        LambdaQueryWrapper<SagaStepLogEntity> q = new LambdaQueryWrapper<>();
        q.eq(SagaStepLogEntity::getSagaId, sagaId).eq(SagaStepLogEntity::getStepName, stepName)
                .orderByDesc(SagaStepLogEntity::getId).last("LIMIT 1");
        return getBaseMapper().selectOne(q);
    }

    public List<SagaStepLogEntity> findSteps(String sagaId) {
        LambdaQueryWrapper<SagaStepLogEntity> q = new LambdaQueryWrapper<>();
        q.eq(SagaStepLogEntity::getSagaId, sagaId).orderByAsc(SagaStepLogEntity::getStepOrder);
        return list(q);
    }
}

