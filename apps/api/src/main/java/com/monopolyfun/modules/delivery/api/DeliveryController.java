package com.monopolyfun.modules.delivery.api;

import com.monopolyfun.modules.delivery.service.InstantFulfillmentService;
import com.monopolyfun.modules.order.service.mapper.OrderViewMapper;
import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work/orders")
public class DeliveryController {
    private final InstantFulfillmentService instantFulfillmentService;
    private final CurrentAccountAccess currentAccountAccess;

    public DeliveryController(InstantFulfillmentService instantFulfillmentService, CurrentAccountAccess currentAccountAccess) {
        this.instantFulfillmentService = instantFulfillmentService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @PostMapping("/{orderNo}/instant-fulfillment/retry")
    public OrderSummary retryInstantFulfillment(@PathVariable String orderNo) {
        // 中文注释：重试发货有真实 provider 副作用，actor 只能来自登录态，避免请求体伪造订单参与方。
        String actorAccountId = currentAccountAccess.requireAccountId();
        return OrderViewMapper.order(instantFulfillmentService.retry(orderNo, actorAccountId), actorAccountId);
    }
}
