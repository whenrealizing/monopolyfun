package com.monopolyfun.modules.order.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class OrderTransitionPolicy {
    private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.CLAIMED, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.FINAL_ACCEPTED, OrderStatus.FINAL_CLOSED));
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.CLAIMED, OrderStatus.ACCEPTED_OPEN, OrderStatus.DISPUTED, OrderStatus.FINAL_ACCEPTED));
        ALLOWED.put(OrderStatus.ACCEPTED_OPEN, EnumSet.of(OrderStatus.DISPUTED, OrderStatus.FINAL_ACCEPTED));
        ALLOWED.put(OrderStatus.DISPUTED, EnumSet.of(OrderStatus.CLAIMED, OrderStatus.DELIVERED, OrderStatus.ACCEPTED_OPEN, OrderStatus.FINAL_ACCEPTED, OrderStatus.FINAL_CLOSED));
    }

    private OrderTransitionPolicy() {
    }

    public static void requireAllowed(OrderStatus from, OrderStatus to, OrderAction action) {
        if (from == null && to == OrderStatus.CLAIMED) {
            return;
        }
        if (from != null && ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to)) {
            return;
        }
        // 中文注释：订单状态矩阵是唯一流转守卫，避免 command service 分散判断造成隐藏跳转。
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Illegal order transition: " + from + " -> " + to + " by " + action);
    }
}
