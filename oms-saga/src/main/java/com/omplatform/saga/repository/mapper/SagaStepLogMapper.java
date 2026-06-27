package com.omplatform.saga.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.omplatform.saga.entity.SagaStepLogEntity;

@Mapper
public interface SagaStepLogMapper extends BaseMapper<SagaStepLogEntity> {
}

