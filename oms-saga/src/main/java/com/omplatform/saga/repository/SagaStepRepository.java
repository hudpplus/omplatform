package com.omplatform.saga.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;
import com.omplatform.saga.entity.SagaStepLogEntity;
import com.omplatform.saga.repository.mapper.SagaStepLogMapper;

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

