package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.omplatform.common.constant.OrderStatus.*;

/**
 * N×N 订单状态转换矩阵（ADR-039 §1.2）。
 * <p>
 * 定义 13 态之间的合法转换边。行=当前状态，列=目标状态。
 * 终态（COMPLETED / CANCELLED / CLOSED / REFUNDED）无出边。
 */
@Component
public class OrderStateTransitionMatrix {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PENDING_PAY 的出边
        TRANSITIONS.put(PENDING_PAY, Set.of(PAID, CANCELLED, CLOSED, HOLD, FROZEN));
        // PAID 的出边
        TRANSITIONS.put(PAID, Set.of(TO_SHIP, CANCELLED, REFUNDING, HOLD, FROZEN));
        // TO_SHIP 的出边
        TRANSITIONS.put(TO_SHIP, Set.of(SHIPPED, REFUNDING, RETURNING, FROZEN));
        // SHIPPED 的出边
        TRANSITIONS.put(SHIPPED, Set.of(DELIVERED, RETURNING, FROZEN));
        // DELIVERED 的出边
        TRANSITIONS.put(DELIVERED, Set.of(COMPLETED, RETURNING, FROZEN));
        // COMPLETED 的出边
        TRANSITIONS.put(COMPLETED, Set.of(RETURNING, FROZEN));
        // 终态无出边
        TRANSITIONS.put(CANCELLED, Set.of());
        TRANSITIONS.put(CLOSED, Set.of());
        // REFUNDING 的出边
        TRANSITIONS.put(REFUNDING, Set.of(REFUNDED, FROZEN));
        // RETURNING 的出边
        TRANSITIONS.put(RETURNING, Set.of(REFUNDED, FROZEN));
        TRANSITIONS.put(REFUNDED, Set.of());
        // HOLD 的出边
        TRANSITIONS.put(HOLD, Set.of(PENDING_PAY, PAID, CANCELLED, FROZEN));
        // FROZEN 的出边
        TRANSITIONS.put(FROZEN, Set.of(PENDING_PAY, PAID, TO_SHIP, SHIPPED, DELIVERED,
                CANCELLED, HOLD, REFUNDING, RETURNING));
    }

    /**
     * 校验转换是否合法。
     */
    public boolean isValid(OrderStatus current, OrderStatus target) {
        return TRANSITIONS.getOrDefault(current, Collections.emptySet()).contains(target);
    }

    /**
     * 获取某状态的合法出边列表。
     */
    public Set<OrderStatus> getAllowedTargets(OrderStatus current) {
        return TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}
