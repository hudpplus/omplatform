package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.trade.repository.entity.TccTransactionEntity;
import org.springframework.stereotype.Repository;

@Repository
public class TccTransactionRepository extends ServiceImpl<TccTransactionMapper, TccTransactionEntity> {
    // convenience wrapper for MyBatis-Plus operations
}

