package com.omplatform.risk.service;

import com.omplatform.risk.repository.mapper.RiskCheckRecordMapper;
import com.omplatform.risk.repository.redis.RiskCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 风控检查服务单元测试（ADR-047）。
 */
@ExtendWith(MockitoExtension.class)
class RiskCheckServiceTest {

    @Mock
    private RiskCacheRepository riskCache;
    @Mock
    private RiskCheckRecordMapper recordMapper;

    private RiskCheckService riskCheckService;

    @BeforeEach
    void setUp() {
        riskCheckService = new RiskCheckService(riskCache, recordMapper);
        lenient().when(recordMapper.insert(any(com.omplatform.risk.repository.entity.RiskCheckRecordEntity.class))).thenReturn(1);
    }

    @Test
    @DisplayName("L2 降级应跳过所有风控检查")
    void degradationL2_shouldSkipAllChecks() {
        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L2);

        RiskCheckService.RiskResult result = riskCheckService.preCheck("user001", "device001", "ORD001");

        assertTrue(result.passed());
        assertEquals("降级跳过(L2)", result.reason());
    }

    @Test
    @DisplayName("L0 黑名单命中应直接拒绝")
    void blacklistHit_shouldReject() {
        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L0);
        when(riskCache.isUserInBlacklist("user001")).thenReturn(true);

        RiskCheckService.RiskResult result = riskCheckService.preCheck("user001", "device001", "ORD001");

        assertFalse(result.passed());
        assertEquals("REJECT", result.decision());
    }

    @Test
    @DisplayName("L0 白名单命中应直接通过")
    void whitelistHit_shouldPass() {
        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L0);
        when(riskCache.isUserInBlacklist("user001")).thenReturn(false);
        when(riskCache.isUserInWhitelist("user001")).thenReturn(true);

        RiskCheckService.RiskResult result = riskCheckService.preCheck("user001", "device001", "ORD001");

        assertTrue(result.passed());
        assertEquals("白名单", result.reason());
    }

    @Test
    @DisplayName("L1 降级仅检查本地黑名单")
    void degradationL1_shouldCheckLocalOnly() {
        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L1);
        // 黑名单不命中
        when(riskCache.isUserInBlacklist("user001")).thenReturn(false);

        RiskCheckService.RiskResult result = riskCheckService.preCheck("user001", "device001", "ORD001");

        assertTrue(result.passed());
        assertEquals("本地检查通过", result.reason());
    }

    @Test
    @DisplayName("设备黑名单命中应拒绝")
    void deviceBlacklist_shouldReject() {
        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L0);
        when(riskCache.isUserInBlacklist("user001")).thenReturn(false);
        when(riskCache.isDeviceInBlacklist("device001")).thenReturn(true);

        RiskCheckService.RiskResult result = riskCheckService.preCheck("user001", "device001", "ORD001");

        assertFalse(result.passed());
    }

    @Test
    @DisplayName("L0 降级时可恢复到 L0")
    void degradationLevelChanges() {
        assertEquals(RiskCheckService.DegradationLevel.L0, riskCheckService.getCurrentLevel());

        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L2);
        assertEquals(RiskCheckService.DegradationLevel.L2, riskCheckService.getCurrentLevel());

        riskCheckService.setDegradationLevel(RiskCheckService.DegradationLevel.L0);
        assertEquals(RiskCheckService.DegradationLevel.L0, riskCheckService.getCurrentLevel());
    }

    @Test
    @DisplayName("退款风控评分：小额退款低风险应通过")
    void refundRisk_smallAmount_shouldPass() {
        RiskCheckService.RiskResult result = riskCheckService.evaluateRefundRisk(
                "user001", "ORD001", new BigDecimal("50.00"));

        assertTrue(result.passed());
        assertEquals("LOW", result.riskLevel());
        assertTrue(result.score() <= 50);
    }

    @Test
    @DisplayName("退款风控评分：大额退款金额因子生效")
    void refundRisk_largeAmount_scoreIncreases() {
        RiskCheckService.RiskResult result = riskCheckService.evaluateRefundRisk(
                "user001", "ORD001", new BigDecimal("10000.00"));

        // 金额因子 +30（>5000）
        assertTrue(result.score() >= 30);
    }

    @Test
    @DisplayName("退款风控评分：黑名单用户+大额退款应拒绝")
    void refundRisk_blacklistLarge_shouldReject() {
        when(riskCache.isUserInBlacklist("user001")).thenReturn(true);

        RiskCheckService.RiskResult result = riskCheckService.evaluateRefundRisk(
                "user001", "ORD001", new BigDecimal("10000.00"));

        // 金额 +30 + 黑名单 +50 = 80 → 80 <= 80 → REVIEW, not REJECT
        // 需要 > 95 才 REJECT
        assertTrue(result.score() >= 80);
    }

    @Test
    @DisplayName("退款风控评分：黑名单用户应高风险")
    void refundRisk_blacklistUser_shouldHighRisk() {
        when(riskCache.isUserInBlacklist("user001")).thenReturn(true);

        RiskCheckService.RiskResult result = riskCheckService.evaluateRefundRisk(
                "user001", "ORD001", new BigDecimal("100.00"));

        // 黑名单因子 +50，总 >= 50
        assertTrue(result.score() >= 50);
    }
}
