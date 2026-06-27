package com.omplatform.trade.controller.distributed_transaction.tcc;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * TCC 下单服务 —— 使用 TCC 创建订单（简化示例）。
 */
@Slf4j
@Service
public class TccCreateOrderService {

    @Autowired
    private List<TccParticipant> participants;
    @Autowired
    private com.omplatform.trade.repository.TccTransactionRepository txRepository;
    @Autowired
    private com.omplatform.trade.repository.TccParticipantStateRepository participantStateRepository;

    /* public TccCreateOrderService(List<TccParticipant> participants,
                                 com.omplatform.trade.repository.TccTransactionRepository txRepository,
                                 com.omplatform.trade.repository.TccParticipantStateRepository participantStateRepository) {
        this.participants = participants;
        this.txRepository = txRepository;
        this.participantStateRepository = participantStateRepository;
    } */

    public ApiResult<String> createOrder(CreateOrderRequest request) {
        // 先生成 orderNo，再用 orderNo 生成 txId（保证可由 orderNo 推导 txId）
        String orderNo = generateOrderNo();
        String txId = "TCC-" + orderNo;

        // 构建事务上下文（避免复杂的类型推断，使用显式循环）
        List<TccTransactionContext.ItemLine> items = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest it : request.getItems()) {
            TccTransactionContext.ItemLine line = new TccTransactionContext.ItemLine(
                    it.getSkuId(), it.getQuantity(), it.getUnitPrice());
            items.add(line);
            if (line.getUnitPrice() != null && line.getQuantity() != null) {
                total = total.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            }
        }

        // 直接使用 Builder 类型以避免 IDE/编译器对 builder() 的解析问题
        TccTransactionContext ctx = new TccTransactionContext.Builder()
                .txId(txId)
                .orderNo(orderNo)
                .buyerId(request.getBuyerId())
                .items(items)
                .totalAmount(total)
                .build();

        // 调用 Coordinator 执行（同步展示），传入 repository 以持久化事务状态
        TccCoordinator coordinator = new TccCoordinator(participants, ctx,
                txRepository, participantStateRepository);
        boolean success = coordinator.execute();

        if (!success) {
            log.error("[TCC] 下单失败: txId={}, orderNo={}", txId, orderNo);
            return ApiResult.error("TCC_FAILED", "下单失败");
        }

        log.info("[TCC] 下单预留成功: txId={}, orderNo={}, 等待支付确认", txId, orderNo);
        return ApiResult.success(orderNo);
    }

    public ApiResult<Void> confirmPayment(String orderNo) {
        log.info("[TCC] 支付确认: orderNo={}", orderNo);
        TccTransactionContext ctx = buildConfirmContext(orderNo);
        for (TccParticipant p : participants) {
            p.confirmAction(ctx);
        }
        return ApiResult.success();
    }

    private String generateOrderNo() {
        long ts = System.currentTimeMillis();
        int rnd = new Random().nextInt(9000) + 1000;
        return String.valueOf(ts) + rnd;
    }

    /**
     * 基于 orderNo 构建 Confirm 时的上下文（简化：txId = "TCC-" + orderNo）。
     * 在真实项目中应从持久化记录中读取完整事务上下文。
     */
    private TccTransactionContext buildConfirmContext(String orderNo) {
        String txId = "TCC-" + orderNo;
        return new TccTransactionContext.Builder()
                .txId(txId)
                .orderNo(orderNo)
                .buyerId(null)
                .items(Collections.emptyList())
                .totalAmount(BigDecimal.ZERO)
                .build();
    }
}

