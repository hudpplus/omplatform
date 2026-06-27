package com.omplatform.trade.saga;

import com.omplatform.api.inventory.InventoryService;
import com.omplatform.api.marketing.MarketingService;
import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.api.payment.PaymentService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.exception.BizException;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.service.atomic.OrderCreateService;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 下单 Saga 定义 + 步骤执行器注册（ADR-020 §4.1）。
 * <p>
 * 负责两件事：
 * <ol>
 *   <li>定义 Saga 步骤元数据（步骤名、顺序、重试策略）— 通过 {@link @Bean}</li>
 *   <li>注册步骤执行器到 {@link SagaExecutor} — 通过 {@link @PostConstruct}</li>
 * </ol>
 * <p>
 * Saga 步骤：
 * <ol>
 *   <li>createOrder — 创建订单（oms-trade 本地）</li>
 *   <li>deductInventory — 预占库存（oms-fulfillment）</li>
 *   <li>chargePayment — 发起支付（oms-finance）</li>
 *   <li>confirmOrder — 确认订单完成（oms-trade 本地）</li>
 * </ol>
 */
@Slf4j
@Configuration
public class CreateOrderSagaDefinition {

    // === 本地服务 ===
    @Autowired
    private SagaExecutor sagaExecutor;
    @Autowired
    private OrderCreateService orderCreateService;
    @Autowired
    private OrderRepository orderRepository;

    // === Dubbo 远程服务 ===
    @DubboReference
    private InventoryService inventoryService;
    @DubboReference
    private PaymentService paymentService;
    @DubboReference
    private MarketingService marketingService;

    // =================================================================
    // Saga 定义 @Bean — 步骤元数据 + 执行器注册写在一起
    // =================================================================

    @Bean
    public SagaDefinition createOrderSaga() {
        SagaDefinition def = new SagaDefinition();
        def.setSagaName("createOrder");
        def.setGlobalTimeout(Duration.ofMinutes(5));

        SagaDefinition.RetryPolicy retryPolicy = new SagaDefinition.RetryPolicy();
        retryPolicy.setMaxRetries(3);
        retryPolicy.setBackoffIntervals(Arrays.asList(
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)));
        def.setRetryPolicy(retryPolicy);

        // ===== Step 1: createOrder =====
        SagaStep step1 = new SagaStep();
        step1.setStepName("createOrder");
        step1.setOrder(1);
        step1.setMandatory(true);

        sagaExecutor.registerForwardStep("createOrder", ctx -> {
            CreateOrderRequest req = ctx.getStepArg("createRequest");
            String orderNo = ctx.getBusinessKey();

            // 创建订单（本地事务）
            TransitionContext tctx = TransitionContext.systemContext("创建订单");
            tctx.getExtras().put("sagaId", ctx.getSagaId());
            tctx.getExtras().put("stepName", "createOrder");
            tctx.getExtras().put("participantId", "order-service");
            tctx.getExtras().put("createRequest", req);
            orderCreateService.execute(orderNo, tctx);

            // 秒杀订单：使用秒杀价，跳过营销计价和优惠券
            if (req.getSeckillActivityId() != null) {
                if (req.getSeckillPrice() != null) {
                    OrderEntity entity = orderRepository.getById(orderNo);
                    if (entity != null) {
                        entity.setTotalAmount(req.getSeckillPrice().multiply(
                                BigDecimal.valueOf(req.getItems().stream()
                                        .mapToInt(com.omplatform.api.order.dto.CreateOrderRequest.OrderItemRequest::getQuantity)
                                        .sum())));
                        entity.setPayAmount(entity.getTotalAmount());
                        entity.setDiscountAmount(BigDecimal.ZERO);
                        entity.setFreightAmount(BigDecimal.ZERO);
                        orderRepository.updateById(entity);
                    }
                }
                log.info("秒杀订单跳过营销计价和优惠券: orderNo={}, activityId={}",
                        orderNo, req.getSeckillActivityId());
            } else {
                // 调用营销服务计算价格（含优惠券/会员折扣）
                try {
                    List<MarketingService.PriceRequest.ItemLine> itemLines = req.getItems().stream()
                            .map(item -> new MarketingService.PriceRequest.ItemLine(
                                    item.getSkuId(), item.getQuantity(), item.getUnitPrice(), null))
                            .toList();
                    MarketingService.PriceRequest priceReq = new MarketingService.PriceRequest(
                            req.getBuyerId(), req.getShopId(), itemLines,
                            req.getCouponInstanceId(), req.getAddressId());
                    ApiResult<MarketingService.PriceResult> priceResult = marketingService.calculatePrice(priceReq);
                    if (priceResult.isSuccess() && priceResult.getData() != null) {
                        OrderEntity entity = orderRepository.getById(orderNo);
                        if (entity != null) {
                            entity.setTotalAmount(priceResult.getData().originalTotal());
                            entity.setPayAmount(priceResult.getData().finalTotal());
                            entity.setDiscountAmount(priceResult.getData().memberDiscount()
                                    .add(priceResult.getData().promotionDiscount())
                                    .add(priceResult.getData().couponDiscount()));
                            entity.setFreightAmount(priceResult.getData().shippingFee());
                            orderRepository.updateById(entity);
                        }
                    }
                } catch (Exception e) {
                    log.warn("营销计价调用失败，使用默认价格: {}", e.getMessage());
                }

                // 锁定优惠券
                if (req.getCouponInstanceId() != null && !req.getCouponInstanceId().isBlank()) {
                    try {
                        marketingService.lockCoupon(req.getCouponInstanceId(), orderNo);
                    } catch (Exception e) {
                        log.warn("优惠券锁定失败: {}", e.getMessage());
                    }
                }
            }

            return true;
        });

        sagaExecutor.registerCompensateStep("createOrder", ctx -> {
            String orderNo = ctx.getBusinessKey();
            TransitionContext tctx = TransitionContext.systemContext("Saga补偿-取消订单");
            tctx.getExtras().put("sagaId", ctx.getSagaId());
            tctx.getExtras().put("stepName", "createOrder");
            orderCreateService.compensate(orderNo, tctx);
            return true;
        });

        // ===== Step 2: deductInventory =====
        SagaDefinition.RetryPolicy inventoryRetry = new SagaDefinition.RetryPolicy();
        inventoryRetry.setMaxRetries(5);
        inventoryRetry.setBackoffIntervals(Arrays.asList(
                Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)));

        SagaStep step2 = new SagaStep();
        step2.setStepName("deductInventory");
        step2.setOrder(2);
        step2.setMandatory(true);
        step2.setCompensateRetry(inventoryRetry);

        sagaExecutor.registerForwardStep("deductInventory", ctx -> {
            CreateOrderRequest req = ctx.getStepArg("createRequest");
            String orderNo = ctx.getBusinessKey();

            // 秒杀订单：库存已在入口预占，跳过
            if (req.getSeckillActivityId() != null) {
                log.info("秒杀订单跳过库存预占: orderNo={}, activityId={}",
                        orderNo, req.getSeckillActivityId());
                return true;
            }

            List<InventoryService.SkuHoldRequest> skuRequests = req.getItems().stream()
                    .map(item -> new InventoryService.SkuHoldRequest(item.getSkuId(), item.getQuantity()))
                    .toList();
            ApiResult<Boolean> result = inventoryService.hold(skuRequests, orderNo);
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                throw new BizException("INVENTORY_SHORTAGE", "库存不足");
            }
            log.info("预占库存成功: orderNo={}", orderNo);
            return true;
        });

        sagaExecutor.registerCompensateStep("deductInventory", ctx -> {
            String orderNo = ctx.getBusinessKey();
            ApiResult<Boolean> result = inventoryService.releaseHold(orderNo);
            log.info("释放库存预占: orderNo={}, result={}", orderNo, result.isSuccess());
            return true;
        });

        // ===== Step 3: chargePayment =====
        SagaStep step3 = new SagaStep();
        step3.setStepName("chargePayment");
        step3.setOrder(3);
        step3.setMandatory(true);

        sagaExecutor.registerForwardStep("chargePayment", ctx -> {
            String orderNo = ctx.getBusinessKey();
            OrderEntity entity = orderRepository.getById(orderNo);
            BigDecimal amount = entity != null && entity.getPayAmount() != null
                    ? entity.getPayAmount() : BigDecimal.ZERO;
            TransitionContextDTO dto = new TransitionContextDTO();
            dto.setOperatorId("SYSTEM");
            dto.setOperatorType("SYSTEM");
            dto.setSource("SAGA");
            ApiResult<String> payResult = paymentService.requestPayment(orderNo, amount, "ALIPAY", dto);
            if (!payResult.isSuccess()) {
                throw new BizException("PAY_FAILED", "支付发起失败: " + payResult.getMessage());
            }
            log.info("发起支付成功: orderNo={}, payUrl={}", orderNo, payResult.getData());
            return true;
        });

        sagaExecutor.registerCompensateStep("chargePayment", ctx -> {
            String orderNo = ctx.getBusinessKey();
            OrderEntity entity = orderRepository.getById(orderNo);
            BigDecimal amount = entity != null && entity.getPayAmount() != null
                    ? entity.getPayAmount() : BigDecimal.ZERO;
            TransitionContextDTO dto = new TransitionContextDTO();
            dto.setOperatorId("SYSTEM");
            dto.setOperatorType("SYSTEM");
            dto.setSource("SAGA_COMPENSATE");
            paymentService.processRefund(orderNo, amount, "", dto);
            log.info("退款处理完成: orderNo={}", orderNo);
            return true;
        });

        // ===== Step 4: confirmOrder =====
        SagaStep step4 = new SagaStep();
        step4.setStepName("confirmOrder");
        step4.setOrder(4);
        step4.setMandatory(false);
        step4.setCompensatePolicy(SagaStep.CompensatePolicy.SKIP_ON_FAILURE);

        sagaExecutor.registerForwardStep("confirmOrder", ctx -> {
            log.info("订单已确认: orderNo={}", ctx.getBusinessKey());
            return true;
        });

        sagaExecutor.registerCompensateStep("confirmOrder", ctx -> true);

        def.setSteps(Arrays.asList(step1, step2, step3, step4));
        return def;
    }
}
