package com.omplatform.cart.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.cart.repository.entity.CartEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车主表 Mapper。
 */
@Mapper
public interface CartMapper extends BaseMapper<CartEntity> {
}
