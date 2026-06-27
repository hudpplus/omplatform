package com.omplatform.saga.outbox;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxRepository extends ServiceImpl<OutboxMapper, OutboxEntity> {

    public List<OutboxEntity> findPending(int limit) {
        return list(lambdaQuery().eq(OutboxEntity::getStatus, "PENDING").last("LIMIT " + limit));
    }
}

