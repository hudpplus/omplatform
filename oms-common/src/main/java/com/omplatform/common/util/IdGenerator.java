package com.omplatform.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式 ID 生成器（Leaf Snowflake 算法）。
 * <p>
 * 默认使用 Hutool Snowflake，workderId / datacenterId 由 Apollo 配置注入。
 * 生产环境可通过 Nacos 注册中心自动分配 workerId。
 * <p>
 * 参考：ADR-041 唯一 ID 生成
 */
@Slf4j
public class IdGenerator {

    private final Snowflake snowflake;

    /**
     * @param workerId      机器 ID（0-31）
     * @param datacenterId  数据中心 ID（0-31）
     */
    public IdGenerator(long workerId, long datacenterId) {
        this.snowflake = IdUtil.getSnowflake(workerId, datacenterId);
        log.info("IdGenerator initialized: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 生成全局唯一 ID（19 位数字）。
     * 适用于 order_no、payment_no 等主键。
     */
    public long nextId() {
        return snowflake.nextId();
    }

    /**
     * 生成字符串形式 ID。
     */
    public String nextIdStr() {
        return String.valueOf(snowflake.nextId());
    }
}
