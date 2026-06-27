package com.omplatform.risk.service;

import com.omplatform.risk.repository.entity.RiskCheckRecordEntity;
import com.omplatform.risk.repository.mapper.RiskCheckRecordMapper;
import com.omplatform.risk.repository.redis.RiskCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 风控检查服务（ADR-047）。
 * <p>
 * 三级降级策略：
 * <ul>
 *   <li>L0（全量）：外部平台 + 黑白名单缓存 + 异步审核</li>
 *   <li>L1（降级）：仅黑白名单缓存（跳过外部平台）</li>
 *   <li>L2（跳过）：跳过所有风控检查，仅记录日志</li>
 * </ul>
 */
@Slf4j
@Service
public class RiskCheckService {

    /** 当前降级等级（由 Apollo 配置驱动）。 */
    private volatile DegradationLevel currentLevel = DegradationLevel.L0;

    @Autowired
    private RiskCacheRepository riskCache;
    @Autowired
    private RiskCheckRecordMapper recordMapper;

    /*public RiskCheckService(RiskCacheRepository riskCache, RiskCheckRecordMapper recordMapper) {
        this.riskCache = riskCache;
        this.recordMapper = recordMapper;
    }*/

    // ========== 下单预检查 ==========

    /**
     * 下单前风控预检查（同步）。
     * <p>
     * 流程：黑名单(REJECT) → 白名单(PASS) → 外部风控(PASS/REVIEW/REJECT)
     */
    public RiskResult preCheck(String buyerId, String deviceId, String orderNo) {
        log.debug("风控预检查: buyerId={}, orderNo={}, level={}", buyerId, orderNo, currentLevel);

        RiskResult result = switch (currentLevel) {
            case L0 -> fullCheck(buyerId, deviceId, orderNo);
            case L1 -> localOnlyCheck(buyerId, deviceId);
            case L2 -> RiskResult.pass("降级跳过(L2)");
        };

        // 保存检查记录
        saveCheckRecord("PRE_CHECK", buyerId, deviceId, orderNo, result);
        return result;
    }

    /**
     * 全量检查（L0）：黑白名单 + 外部风控平台。
     */
    private RiskResult fullCheck(String buyerId, String deviceId, String orderNo) {
        // 1. 黑名单检查
        if (isInBlacklist(buyerId, deviceId)) {
            return RiskResult.reject("黑名单拦截");
        }

        // 2. 白名单检查（命中则跳过外部风控）
        if (isInWhitelist(buyerId)) {
            return RiskResult.pass("白名单");
        }

        // 3. 外部风控平台调用（Dubbo 同步，500ms 超时 + Sentinel 熔断）
        try {
            return callExternalRiskPlatform(buyerId, orderNo);
        } catch (Exception e) {
            log.warn("外部风控不可用，降级到 L1: {}", e.getMessage());
            return localOnlyCheck(buyerId, deviceId);
        }
    }

    /**
     * 仅本地检查（L1）。
     */
    private RiskResult localOnlyCheck(String buyerId, String deviceId) {
        if (isInBlacklist(buyerId, deviceId)) {
            return RiskResult.reject("黑名单拦截");
        }
        return RiskResult.pass("本地检查通过");
    }

    // ========== 退款风控评分 ==========

    /**
     * 退款风控评分（ADR-042 §4.3）。
     * <p>
     * 根据金额、频次、历史行为计算风险评分（0-100）。
     * 阈值：> 80 触发人工审核，> 95 拒绝。
     */
    public RiskResult evaluateRefundRisk(String buyerId, String orderNo, BigDecimal refundAmount) {
        log.debug("退款风控评分: buyerId={}, orderNo={}, amount={}", buyerId, orderNo, refundAmount);

        int score = 0;

        // 1. 金额因子：大额退款加分
        if (refundAmount.compareTo(BigDecimal.valueOf(5000)) > 0) score += 30;
        else if (refundAmount.compareTo(BigDecimal.valueOf(1000)) > 0) score += 15;
        else score += 5;

        // 2. 频次因子：近期退款次数（简化，实际需查询 DB）
        int recentRefundCount = countRecentRefunds(buyerId);
        score += Math.min(recentRefundCount * 10, 30);

        // 3. 黑名单因子
        if (riskCache.isUserInBlacklist(buyerId)) score += 50;

        // 判定
        String riskLevel;
        String decision;
        if (score > 80) {
            riskLevel = "HIGH";
            decision = score > 95 ? "REJECT" : "REVIEW";
        } else if (score > 50) {
            riskLevel = "MEDIUM";
            decision = "REVIEW";
        } else {
            riskLevel = "LOW";
            decision = "PASS";
        }

        RiskResult result = new RiskResult("PASS".equals(decision), score, decision, riskLevel,
                "退款风控评分: " + score);
        saveCheckRecord("REFUND_CHECK", buyerId, null, orderNo, result);
        return result;
    }

    /**
     * 获取近期退款次数。
     * 查询 risk_check_record 表，统计近 30 天退款检查记录。
     */
    private int countRecentRefunds(String buyerId) {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(30);
            Long count = recordMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskCheckRecordEntity>()
                            .eq(RiskCheckRecordEntity::getBuyerId, buyerId)
                            .eq(RiskCheckRecordEntity::getCheckType, "REFUND_CHECK")
                            .ge(RiskCheckRecordEntity::getGmtCreate, since));
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("查询近30天退款次数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ========== 黑白名单检查 ==========

    private boolean isInWhitelist(String userId) {
        return riskCache.isUserInWhitelist(userId);
    }

    private boolean isInBlacklist(String buyerId, String deviceId) {
        return riskCache.isUserInBlacklist(buyerId)
                || (deviceId != null && riskCache.isDeviceInBlacklist(deviceId));
    }

    // ========== 外部风控平台 ==========

    private RiskResult callExternalRiskPlatform(String buyerId, String orderNo) {
        log.debug("调用外部风控: buyerId={}, orderNo={}", buyerId, orderNo);
        // 实际：通过 Dubbo/HTTP 调用外部风控服务
        // 当前返回 PASS（方便开发调试）
        return RiskResult.pass("外部风控通过");
    }

    // ========== 降级控制 ==========

    public void setDegradationLevel(DegradationLevel level) {
        log.warn("风控降级: {} → {}", currentLevel, level);
        this.currentLevel = level;
    }

    public DegradationLevel getCurrentLevel() {
        return currentLevel;
    }

    // ========== 记录持久化 ==========

    private void saveCheckRecord(String checkType, String buyerId, String deviceId,
                                  String orderNo, RiskResult result) {
        RiskCheckRecordEntity entity = new RiskCheckRecordEntity(
                UUID.randomUUID().toString().replace("-", ""),
                checkType, buyerId, deviceId, orderNo,
                result.decision(), result.riskLevel(), result.score(),
                null, result.reason(), currentLevel.name()
        );
        recordMapper.insert(entity);
    }

    // ========== 类型定义 ==========

    public enum DegradationLevel {
        L0("全量检查"),
        L1("仅本地缓存"),
        L2("跳过检查");

        private final String description;

        DegradationLevel(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 风控检查结果。
     */
    public record RiskResult(
            boolean passed,
            int score,
            String decision,
            String riskLevel,
            String reason
    ) {
        public static RiskResult pass(String reason) {
            return new RiskResult(true, 0, "PASS", "LOW", reason);
        }

        public static RiskResult reject(String reason) {
            return new RiskResult(false, 100, "REJECT", "HIGH", reason);
        }

        public static RiskResult review(String reason) {
            return new RiskResult(true, 60, "REVIEW", "MEDIUM", reason);
        }
    }
}
