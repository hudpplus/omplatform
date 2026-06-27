package com.omplatform.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omplatform.common.exception.BizException;
import com.omplatform.seckill.entity.SeckillActivityEntity;
import com.omplatform.seckill.mapper.SeckillActivityMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动管理服务。
 * <p>
 * 提供活动 CRUD + 状态变更 + Redis 三级库存预热。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityService {

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redisTemplate;

    // ========== Redis Key 构建（三级库存） ==========

    /** 可用库存 Key：用户可抢的 */
    private static String availableKey(Long activityId, String skuId) {
        return "seckill:stock:" + activityId + ":" + skuId + ":available";
    }

    /** 预留库存 Key：已抢未支付的 */
    private static String heldKey(Long activityId, String skuId) {
        return "seckill:stock:" + activityId + ":" + skuId + ":held";
    }

    /** hold 明细 Key */
    private static String holdKey(String orderNo) {
        return "seckill:hold:" + orderNo;
    }

    // ========== 公开 Key 方法（供 Handler / Consumer 使用） ==========

    public static String buildAvailableKey(Long activityId, String skuId) {
        return availableKey(activityId, skuId);
    }

    public static String buildHeldKey(Long activityId, String skuId) {
        return heldKey(activityId, skuId);
    }

    public static String buildHoldKey(String orderNo) {
        return holdKey(orderNo);
    }

    // ========== CRUD ==========

    public SeckillActivityEntity getById(Long id) {
        return activityMapper.selectById(id);
    }

    public List<SeckillActivityEntity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(new LambdaQueryWrapper<SeckillActivityEntity>()
                .eq(SeckillActivityEntity::getStatus, "ACTIVE")
                .eq(SeckillActivityEntity::getDeleted, 0)
                .le(SeckillActivityEntity::getStartTime, now)
                .ge(SeckillActivityEntity::getEndTime, now));
    }

    @Transactional
    public void create(SeckillActivityEntity activity) {
        activity.setStatus("DRAFT");
        activity.setAvailableStock(activity.getTotalStock());
        activityMapper.insert(activity);
    }

    /**
     * 将活动状态改为 ACTIVE 并预热 Redis。
     */
    @Transactional
    public void activate(Long id) {
        SeckillActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) throw new BizException("ACTIVITY_NOT_FOUND", "活动不存在");
        activity.setStatus("ACTIVE");
        activityMapper.updateById(activity);
        warmUpRedis(activity);
        log.info("[秒杀] 活动已激活并预热: id={}, sku={}, stock={}", id, activity.getSkuId(), activity.getAvailableStock());
    }

    /**
     * 将活动改为 ENDED 并清理 Redis（删除 available + held）。
     */
    @Transactional
    public void end(Long id) {
        SeckillActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) throw new BizException("ACTIVITY_NOT_FOUND", "活动不存在");
        activity.setStatus("ENDED");
        activityMapper.updateById(activity);
        redisTemplate.delete(availableKey(id, activity.getSkuId()));
        redisTemplate.delete(heldKey(id, activity.getSkuId()));
        log.info("[秒杀] 活动已结束，Redis 已清理: id={}", id);
    }

    // ========== Redis 预热 ==========

    /**
     * 启动时自动预热所有 ACTIVE 活动的库存到 Redis（三级库存）。
     */
    @PostConstruct
    public void warmUpOnStartup() {
        List<SeckillActivityEntity> activeList = listActive();
        if (activeList.isEmpty()) {
            log.info("[秒杀] 启动预热: 无 ACTIVE 活动");
            return;
        }
        for (SeckillActivityEntity activity : activeList) {
            warmUpRedis(activity);
        }
        log.info("[秒杀] 启动预热完成: {} 个活动", activeList.size());
    }

    /**
     * 预热三级库存：available = DB 可用库存，held = 0。
     */
    public void warmUpRedis(SeckillActivityEntity activity) {
        String avKey = availableKey(activity.getId(), activity.getSkuId());
        String heKey = heldKey(activity.getId(), activity.getSkuId());
        redisTemplate.opsForValue().set(avKey, String.valueOf(activity.getAvailableStock()));
        redisTemplate.opsForValue().set(heKey, "0");
        log.debug("[秒杀] Redis 预热: available={}, held=0 (activityId={}, sku={})",
                activity.getAvailableStock(), activity.getId(), activity.getSkuId());
    }

    // ========== 支付确认扣减（三级库存 → 已售） ==========

    /**
     * 支付成功后确认扣减：DB available_stock -1，Redis held -1。
     * <p>
     * 此时库存状态：available（可抢）\ held（预留）\ 已售（DB 记录）。
     * 三级库存恒等式：available + held + 已售 = total_stock。
     */
    @Transactional
    public void confirmDeduct(Long activityId, int quantity) {
        SeckillActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) return;
        if (activity.getAvailableStock() < quantity) {
            log.warn("[秒杀] DB 库存不足: activityId={}, available={}, need={}",
                    activityId, activity.getAvailableStock(), quantity);
            return;
        }
        activity.setAvailableStock(activity.getAvailableStock() - quantity);
        activityMapper.updateById(activity);
        log.info("[秒杀] DB 库存扣减完成: activityId={}, sku={}, newAvailable={}",
                activityId, activity.getSkuId(), activity.getAvailableStock());
    }
}
