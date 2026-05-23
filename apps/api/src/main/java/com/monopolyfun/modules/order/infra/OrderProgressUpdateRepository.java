package com.monopolyfun.modules.order.infra;

import com.monopolyfun.modules.order.domain.OrderProgressUpdateEntity;

import java.util.List;

public interface OrderProgressUpdateRepository {
    List<OrderProgressUpdateEntity> findByOrderId(String orderId);

    OrderProgressUpdateEntity save(OrderProgressUpdateEntity update);
}
