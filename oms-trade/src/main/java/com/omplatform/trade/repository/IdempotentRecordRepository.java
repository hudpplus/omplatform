package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.trade.repository.entity.IdempotentRecordEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class IdempotentRecordRepository extends ServiceImpl<IdempotentRecordMapper, IdempotentRecordEntity> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean tryAcquire(String key, String sagaId, String stepName) {
        IdempotentRecordEntity e = new IdempotentRecordEntity();
        e.setIdempotentKey(key);
        e.setSagaId(sagaId);
        e.setStepName(stepName);
        e.setStatus("EXECUTING");
        e.setCreatedAt(LocalDateTime.now());
        e.setExpireAt(LocalDateTime.now().plusDays(30));
        try {
            return getBaseMapper().insert(e) == 1;
        } catch (Exception ex) {
            // MyBatis/Spring will throw DataAccessException for duplicate PK; normalize to DuplicateKeyException
            if (ex instanceof DuplicateKeyException || ex.getMessage() != null && ex.getMessage().contains("Duplicate entry")) {
                return false;
            }
            throw ex;
        }
    }

    public void complete(String key, Object result) {
        try {
            String json = result == null ? null : objectMapper.writeValueAsString(result);
            IdempotentRecordEntity e = getBaseMapper().selectById(key);
            if (e != null) {
                e.setStatus("SUCCEEDED");
                e.setResultPayload(json);
                getBaseMapper().updateById(e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Object getPreviousResult(String key, Class<?> clazz) {
        try {
            IdempotentRecordEntity e = getBaseMapper().selectById(key);
            if (e == null || e.getResultPayload() == null) return null;
            return objectMapper.readValue(e.getResultPayload(), clazz);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void remove(String key) {
        getBaseMapper().deleteById(key);
    }

    public boolean exists(String key) {
        return getBaseMapper().selectById(key) != null;
    }
}

