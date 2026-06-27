package com.omplatform.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体。
 * <p>
 * 映射 seckill_activity 表，管理活动时间窗口、库存、秒杀价。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivityEntity extends BaseEntity {

    /** 活动 ID（自增 PK） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称 */
    private String activityName;

    /** 秒杀 SKU */
    private String skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 总库存 */
    private Integer totalStock;

    /** 当前可用库存 */
    private Integer availableStock;

    /** 每人限购数量 */
    private Integer limitPerUser;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态: DRAFT / ACTIVE / PAUSED / ENDED */
    private String status;
}
