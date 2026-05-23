package com.monopolyfun.modules.order;

import com.monopolyfun.modules.order.domain.OrderAction;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.domain.OrderTransitionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTransitionPolicyTest {
    @Test
    void allowsInstantFulfillmentToFinalizeFromClaimed() {
        assertDoesNotThrow(() -> OrderTransitionPolicy.requireAllowed(OrderStatus.CLAIMED, OrderStatus.FINAL_ACCEPTED, OrderAction.ACCEPT));
    }

    @Test
    void blocksSkippingBackFromFinalStatus() {
        assertThrows(ResponseStatusException.class,
                () -> OrderTransitionPolicy.requireAllowed(OrderStatus.FINAL_ACCEPTED, OrderStatus.CLAIMED, OrderAction.CLAIM));
    }
}
