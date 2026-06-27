package com.omplatform.cart.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.cart.repository.entity.CartSyncOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车同步发件箱 Mapper。
 */
@Mapper
public interface CartSyncOutboxMapper extends BaseMapper<CartSyncOutboxEntity> {
}
