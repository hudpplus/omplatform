package com.omplatform.cart.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.omplatform.cart.repository.entity.CartEntity;
import com.omplatform.cart.repository.mapper.CartMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车 ID 布隆过滤器（缓存穿透防护）。
 * <p>
 * 启动时加载所有 ACTIVE 购物车 ID，判断 "一定不存在" 时直接拦截，
 * 避免不存在的 cartId 穿透到 Redis 乃至 DB。
 * <p>
 * 误判率 ~1%，配合 Redis 空值标记（{@code cart:{id}:null}）形成双层防护。
 */
@Slf4j
@Component
public class CartIdBloomFilter {

    private final BloomFilter<String> bloomFilter;
    private final CartMapper cartMapper;

    public CartIdBloomFilter(CartMapper cartMapper) {
        this.bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.01);
        this.cartMapper = cartMapper;
    }

    /**
     * 启动时从 DB 加载全部有效购物车 ID 到布隆过滤器。
     * <p>
     * 使用 {@code @PostConstruct} 确保 Mapper 注入完成后才查 DB。
     */
    @PostConstruct
    public void init() {
        List<CartEntity> carts = cartMapper.selectList(
                new LambdaQueryWrapper<CartEntity>()
                        .eq(CartEntity::getStatus, "ACTIVE")
                        .select(CartEntity::getCartId));
        for (CartEntity cart : carts) {
            bloomFilter.put(cart.getCartId());
        }
        log.info("BloomFilter 初始化完成，加载 {} 条有效购物车，预计内存 ~2MB", carts.size());
    }

    /**
     * 判断 cartId 是否可能存在于 DB。
     *
     * @return false = 一定不存在；true = 可能存在（有 ~1% 误判）
     */
    public boolean mightExist(String cartId) {
        return bloomFilter.mightContain(cartId);
    }

    /** 新创建购物车时加入过滤器。 */
    public void put(String cartId) {
        bloomFilter.put(cartId);
    }
}
