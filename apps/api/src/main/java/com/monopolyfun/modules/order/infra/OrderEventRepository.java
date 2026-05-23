package com.monopolyfun.modules.order.infra;

import com.monopolyfun.modules.order.domain.OrderEventEntity;

import java.util.List;

public interface OrderEventRepository {
    List<OrderEventEntity> findByOrderId(String orderId);

    OrderEventEntity save(OrderEventEntity event);
}
