package com.monopolyfun.modules.order;

import com.monopolyfun.modules.order.domain.OrderAction;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.service.workflow.OrderWorkflowCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderWorkflowCatalogTest {
    private final OrderWorkflowCatalog catalog = new OrderWorkflowCatalog();

    @Test
    void requiresKnownWorkflowTransition() {
        var transition = catalog.require(OrderAction.SUBMIT_PROOF, OrderStatus.CLAIMED, OrderStatus.DELIVERED);

        assertEquals("fulfiller", transition.actorPolicy());
        assertEquals("proof_with_acceptance_criteria", transition.sideEffect());
    }

    @Test
    void rejectsUnknownWorkflowTransition() {
        assertThrows(ResponseStatusException.class,
                () -> catalog.require(OrderAction.SUBMIT_PROOF, OrderStatus.DELIVERED, OrderStatus.CLAIMED));
    }
}
