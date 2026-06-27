package com.omplatform.cart.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.cart.repository.entity.CartItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车行表 Mapper。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItemEntity> {
}
