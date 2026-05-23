package com.monopolyfun;

import com.monopolyfun.modules.delivery.api.DeliveryController;
import com.monopolyfun.modules.delivery.service.InstantFulfillmentService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retryUsesAuthenticatedAccountAsActor() {
        InstantFulfillmentService service = Mockito.mock(InstantFulfillmentService.class);
        DeliveryController controller = new DeliveryController(service, new CurrentAccountAccess());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-current", "@current", "Current"),
                null,
                List.of()));
        when(service.retry("MF260519ORD000001X", "acct-current")).thenReturn(order());

        var summary = controller.retryInstantFulfillment("MF260519ORD000001X");

        assertEquals("MF260519ORD000001X", summary.orderNo());
        assertEquals("payer", summary.currentAccountRole());
        verify(service).retry("MF260519ORD000001X", "acct-current");
    }

    @Test
    void retryRequiresAuthenticatedAccountBeforeCallingService() {
        InstantFulfillmentService service = Mockito.mock(InstantFulfillmentService.class);
        DeliveryController controller = new DeliveryController(service, new CurrentAccountAccess());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.retryInstantFulfillment("MF260519ORD000001X"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    private OrderEntity order() {
        return OrderEntity.claim(
                        "order-1",
                        "MF260519ORD000001X",
                        "market-1",
                        "listing-1",
                        ListingKind.WORK,
                        PostKind.OFFER,
                        "offer-1",
                        null,
                        "acct-current",
                        SettlementType.MONEY,
                        new BigDecimal("1.00"),
                        "nonce",
                        List.of("evidence"),
                        "proof",
                        "settlement",
                        Map.of("title", "Instant delivery"),
                        Map.of("currency", "USD"),
                        Map.of(
                                "buyerAccountId", "acct-current",
                                "sellerAccountId", "acct-seller",
                                "fulfillerAccountId", "acct-seller",
                                "deliveryMode", "instant_fulfillment"),
                        new LifecycleContext("acct-current", "trace-1", Instant.parse("2026-05-19T00:00:00Z"), Map.of()))
                .entity();
    }
}
