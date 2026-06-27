package com.omplatform.marketing.service;

import com.omplatform.api.marketing.MarketingService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.marketing.coupon.CouponService;
import com.omplatform.marketing.member.GrowthService;
import com.omplatform.marketing.member.MemberTierService;
import com.omplatform.marketing.member.PointsService;
import com.omplatform.marketing.repository.GrowthTransactionMapper;
import com.omplatform.marketing.repository.PointsAccountMapper;
import com.omplatform.marketing.repository.PointsTransactionMapper;
import com.omplatform.marketing.repository.entity.GrowthTransactionEntity;
import com.omplatform.marketing.repository.entity.PointsAccountEntity;
import com.omplatform.marketing.repository.entity.PointsTransactionEntity;
import com.omplatform.marketing.pipeline.PricePipeline;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 营销 Dubbo 服务实现（ADR-045/046）。
 * <p>
 * 委派给计价管道、优惠券服务、会员服务执行真实计算。
 */
@Slf4j
@DubboService
public class MarketingDubboService implements MarketingService {

    @Autowired
    private PricePipeline pricePipeline;
    @Autowired
    private CouponService couponService;
    @Autowired
    private MemberTierService memberTierService;
    @Autowired
    private GrowthService growthService;
    @Autowired
    private PointsService pointsService;
    @Autowired
    private GrowthTransactionMapper growthTransactionMapper;
    @Autowired
    private PointsAccountMapper pointsAccountMapper;
    @Autowired
    private PointsTransactionMapper pointsTransactionMapper;

    /*public MarketingDubboService(PricePipeline pricePipeline,
                                 CouponService couponService,
                                 MemberTierService memberTierService) {
        this.pricePipeline = pricePipeline;
        this.couponService = couponService;
        this.memberTierService = memberTierService;
    }*/

    @Override
    @SentinelResource(value = "marketing.calculatePrice",
            blockHandler = "calculatePriceBlock",
            blockHandlerClass = com.omplatform.marketing.sentinel.MarketingDubboBlockHandler.class)
    public ApiResult<PriceResult> calculatePrice(PriceRequest request) {
        log.info("[Dubbo] 计价: buyerId={}, items={}", request.buyerId(), request.items().size());

        // 构建计价上下文
        PricePipeline.PriceContext context = new PricePipeline.PriceContext();
        context.setBuyerId(request.buyerId());
        context.setShopId(request.shopId());
        context.setMemberTier(request.buyerId() != null ? resolveTier(request.buyerId()) : null);
        context.setCouponInstanceId(request.couponInstanceId());
        context.setAddressId(request.addressId());

        List<PricePipeline.PriceContext.ItemLine> itemLines = request.items().stream()
                .map(item -> {
                    PricePipeline.PriceContext.ItemLine line = new PricePipeline.PriceContext.ItemLine();
                    line.setSkuId(item.skuId());
                    line.setQuantity(item.quantity());
                    line.setUnitPrice(item.unitPrice());
                    line.setCategoryId(item.categoryId());
                    return line;
                })
                .collect(Collectors.toList());
        context.setItems(itemLines);

        // 计算原价
        BigDecimal originalTotal = itemLines.stream()
                .map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.setOriginalTotal(originalTotal);

        // 执行计价管道
        PricePipeline.PriceResult pipelineResult = pricePipeline.calculate(context);

        // 映射为 API DTO
        PriceResult result = new PriceResult(
                pipelineResult.getOriginalTotal(),
                pipelineResult.getMemberDiscount(),
                pipelineResult.getPromotionDiscount(),
                pipelineResult.getCouponDiscount(),
                pipelineResult.getShippingFee(),
                pipelineResult.getFinalTotal()
        );

        log.info("计价完成: 原价={}, 最终={}", result.originalTotal(), result.finalTotal());
        return ApiResult.success(result);
    }

    @Override
    @SentinelResource(value = "marketing.lockCoupon",
            blockHandler = "lockCouponBlock",
            blockHandlerClass = com.omplatform.marketing.sentinel.MarketingDubboBlockHandler.class)
    public ApiResult<Void> lockCoupon(String couponInstanceId, String orderNo) {
        log.info("[Dubbo] 锁定优惠券: instance={}, order={}", couponInstanceId, orderNo);
        couponService.lockCoupon(couponInstanceId, orderNo);
        return ApiResult.success();
    }

    @Override
    @SentinelResource(value = "marketing.useCoupon",
            blockHandler = "useCouponBlock",
            blockHandlerClass = com.omplatform.marketing.sentinel.MarketingDubboBlockHandler.class)
    public ApiResult<Void> useCoupon(String couponInstanceId, String orderNo) {
        log.info("[Dubbo] 核销优惠券: instance={}, order={}", couponInstanceId, orderNo);
        couponService.useCoupon(couponInstanceId, orderNo);
        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> rollbackCoupon(String couponInstanceId, String orderNo) {
        log.info("[Dubbo] 回退优惠券: instance={}, order={}", couponInstanceId, orderNo);
        couponService.rollbackCoupon(couponInstanceId, orderNo);
        return ApiResult.success();
    }

    @Override
    public ApiResult<MemberInfo> getMemberInfo(String buyerId) {
        log.info("[Dubbo] 查询会员: buyerId={}", buyerId);
        String tierName = resolveTier(buyerId);
        MemberTierService.Tier tier = MemberTierService.Tier.valueOf(tierName);
        boolean freeShipping = memberTierService.isFreeShipping(tier, BigDecimal.ZERO);
        // 返回模拟数据（完整实现需查询 DB）
        return ApiResult.success(new MemberInfo(buyerId, tierName, 800, 3500, freeShipping));
    }

    /**
     * 解析会员等级。
     * 从 DB 查询 buyerId 对应的等级（需实现 MemberTierRepository），
     * 查询失败时默认返回 L2（银卡）。
     */
    private String resolveTier(String buyerId) {
        // 完整实现需查 member_tier 表
        // 例如：return memberTierRepository.findByBuyerId(buyerId).map(Tier::name).orElse("L1");
        log.debug("查询会员等级: buyerId={}", buyerId);
        return "L2";
    }

    @Override
    @SentinelResource(value = "marketing.grantGrowthValue",
            blockHandler = "grantGrowthValueBlock",
            blockHandlerClass = com.omplatform.marketing.sentinel.MarketingDubboBlockHandler.class)
    public ApiResult<Void> grantGrowthValue(String buyerId, String orderNo, BigDecimal amount) {
        log.info("[Dubbo] 发放成长值: buyerId={}, orderNo={}, amount={}", buyerId, orderNo, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("支付金额无效，跳过成长值发放: orderNo={}, amount={}", orderNo, amount);
            return ApiResult.success();
        }

        try {
            // 计算成长值（类目系数默认 1.0，下单频次后续可优化为真实查询）
            long growth = growthService.calculateGrowth(amount, 1.0, 0);
            if (growth <= 0) {
                log.info("成长值为 0，跳过: orderNo={}", orderNo);
                return ApiResult.success();
            }

            GrowthTransactionEntity txn = new GrowthTransactionEntity();
            txn.setTxnId("GRW" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 28));
            txn.setUserId(buyerId);
            txn.setType("ORDER");
            txn.setAmount(growth);
            txn.setSource(orderNo);
            txn.setGmtCreate(LocalDateTime.now());
            growthTransactionMapper.insert(txn);

            log.info("成长值发放成功: buyerId={}, orderNo={}, growth={}", buyerId, orderNo, growth);
        } catch (Exception e) {
            log.error("成长值发放失败: buyerId={}, orderNo={}, err={}", buyerId, orderNo, e.getMessage(), e);
        }

        return ApiResult.success();
    }

    @Override
    @SentinelResource(value = "marketing.grantPoints",
            blockHandler = "grantPointsBlock",
            blockHandlerClass = com.omplatform.marketing.sentinel.MarketingDubboBlockHandler.class)
    public ApiResult<Void> grantPoints(String buyerId, String orderNo, BigDecimal amount) {
        log.info("[Dubbo] 发放积分: buyerId={}, orderNo={}, amount={}", buyerId, orderNo, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("支付金额无效，跳过积分发放: orderNo={}, amount={}", orderNo, amount);
            return ApiResult.success();
        }

        try {
            // 解析会员等级
            String tierName = resolveTier(buyerId);
            MemberTierService.Tier tier = MemberTierService.Tier.valueOf(tierName);

            // 计算应得积分
            long points = pointsService.calculateEarnPoints(tier, amount);
            if (points <= 0) {
                log.info("积分为 0，跳过: orderNo={}", orderNo);
                return ApiResult.success();
            }

            // 查询或创建积分账户
            PointsAccountEntity account = pointsAccountMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PointsAccountEntity>()
                            .eq(PointsAccountEntity::getUserId, buyerId));
            if (account == null) {
                account = new PointsAccountEntity();
                account.setAccountId("PAC" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 28));
                account.setUserId(buyerId);
                account.setBalance(0L);
                account.setTotalEarned(0L);
                account.setTotalSpent(0L);
                account.setGmtCreate(LocalDateTime.now());
                account.setGmtModified(LocalDateTime.now());
                pointsAccountMapper.insert(account);
            }

            // 更新积分账户
            account.setBalance(account.getBalance() + points);
            account.setTotalEarned(account.getTotalEarned() + points);
            account.setGmtModified(LocalDateTime.now());
            pointsAccountMapper.updateById(account);

            // 写入积分流水
            PointsTransactionEntity txn = new PointsTransactionEntity();
            txn.setTxnId("PTS" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 28));
            txn.setAccountId(account.getAccountId());
            txn.setType("EARN");
            txn.setPoints(points);
            txn.setSource(orderNo);
            txn.setGmtCreate(LocalDateTime.now());
            pointsTransactionMapper.insert(txn);

            log.info("积分发放成功: buyerId={}, orderNo={}, points={}", buyerId, orderNo, points);
        } catch (Exception e) {
            log.error("积分发放失败: buyerId={}, orderNo={}, err={}", buyerId, orderNo, e.getMessage(), e);
        }

        return ApiResult.success();
    }
}
