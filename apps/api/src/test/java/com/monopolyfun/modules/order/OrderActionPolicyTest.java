package com.monopolyfun;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.service.query.OrderActionPolicy;
import com.monopolyfun.modules.order.service.view.ActionView;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderActionPolicyTest {
    private final PaymentIntentRepository paymentIntentRepository = mock(PaymentIntentRepository.class);
    private final OrganizationAuthorityService organizationAuthorityService = mock(OrganizationAuthorityService.class);
    private final WorkRepository workRepository = mock(WorkRepository.class);
    private final OrderActionPolicy policy = new OrderActionPolicy(
            new CurrentAccountAccess(),
            organizationAuthorityService,
            paymentIntentRepository,
            workRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buyerCanPayClaimedMoneyOrderBeforeFulfillment() {
        authenticate("acct-buyer");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.empty());

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.CLAIMED, Map.of()));

        assertEquals(List.of("complete_money_payment", "abandon_payment"), actions.stream().map(ActionView::id).toList());
        assertEquals("payer", actions.get(0).role());
    }

    @Test
    void fulfillerDeliveryResultActionIsBlockedUntilMoneyPaymentSettlesByDefault() {
        authenticate("acct-seller");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.empty());

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.CLAIMED, Map.of()));
        ActionView deliveryResultAction = actions.stream()
                .filter(action -> "submit_delivery_result".equals(action.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(deliveryResultAction.requiresPayment());
        assertEquals("等待买家完成付款后再提交交付结果。", deliveryResultAction.disabledReason());
    }

    @Test
    void fulfillerDeliveryResultActionIsBlockedUntilBuyerPays() {
        authenticate("acct-seller");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.empty());

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.CLAIMED, Map.of("deliveryMode", "reviewed_delivery")));
        ActionView deliveryResultAction = actions.stream()
                .filter(action -> "submit_delivery_result".equals(action.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(deliveryResultAction.requiresPayment());
        assertEquals("等待买家完成付款后再提交交付结果。", deliveryResultAction.disabledReason());
    }

    @Test
    void acceptorAcceptanceActionRequiresOwnPaymentFirst() {
        authenticate("acct-buyer");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.empty());

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.DELIVERED, Map.of()));
        ActionView acceptAction = actions.stream()
                .filter(action -> "accept_order".equals(action.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(actions.stream().anyMatch(action -> "complete_money_payment".equals(action.id())));
        assertTrue(acceptAction.requiresPayment());
        assertEquals("payer", acceptAction.role());
        assertEquals("先完成付款，再验收交付结果。", acceptAction.disabledReason());
    }

    @Test
    void settledMoneyPaymentEnablesFulfillmentAction() {
        authenticate("acct-seller");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.of(paymentIntent(PaymentIntentStatus.CAPTURED)));

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.CLAIMED, Map.of()));
        ActionView deliveryResultAction = actions.stream()
                .filter(action -> "submit_delivery_result".equals(action.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(deliveryResultAction.requiresPayment());
        assertNull(deliveryResultAction.disabledReason());
    }

    @Test
    void authorizedMoneyPaymentStillBlocksFulfillmentAction() {
        authenticate("acct-seller");
        when(paymentIntentRepository.findByOrderId("order-money-1")).thenReturn(Optional.of(paymentIntent(PaymentIntentStatus.AUTHORIZED)));

        List<ActionView> actions = policy.availableActions(moneyOrder(OrderStatus.CLAIMED, Map.of()));
        ActionView deliveryResultAction = actions.stream()
                .filter(action -> "submit_delivery_result".equals(action.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(deliveryResultAction.requiresPayment());
        assertEquals("等待买家完成付款后再提交交付结果。", deliveryResultAction.disabledReason());
    }

    @Test
    void participantRolesCollapseToPayerAndFulfiller() {
        OrderEntity order = moneyOrder(OrderStatus.CLAIMED, Map.of());

        assertEquals("payer", order.roleFor("acct-buyer"));
        assertEquals("fulfiller", order.roleFor("acct-seller"));
    }

    private void authenticate(String accountId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount(accountId, "@" + accountId, accountId),
                null,
                List.of()));
        when(organizationAuthorityService.canReviewOrder(eq(accountId), any(OrderEntity.class))).thenReturn(false);
    }

    private OrderEntity moneyOrder(OrderStatus status, Map<String, Object> metadata) {
        Instant now = Instant.parse("2026-05-06T00:00:00Z");
        Map<String, Object> orderMetadata = new HashMap<>();
        orderMetadata.put("buyerAccountId", "acct-buyer");
        orderMetadata.put("sellerAccountId", "acct-seller");
        orderMetadata.put("fulfillerAccountId", "acct-seller");
        orderMetadata.put("acceptorAccountId", "acct-buyer");
        orderMetadata.put("roleModelVersion", OrderEntity.ROLE_MODEL_VERSION);
        orderMetadata.put("fulfillmentMode", metadata.getOrDefault("fulfillmentMode", "reviewed_delivery"));
        if (metadata.containsKey("deliveryMode")) {
            orderMetadata.put("deliveryMode", metadata.get("deliveryMode"));
        }
        return new OrderEntity(
                "order-money-1",
                "MF260506ORD000003R",
                "market-1",
                "listing-1",
                ListingKind.WORK,
                PostKind.OFFER,
                "offer-1",
                null,
                status,
                "delivery_result_due",
                "acct-buyer",
                null,
                null,
                null,
                null,
                null,
                SettlementType.MONEY,
                BigDecimal.valueOf(25),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "nonce-test",
                false,
                List.of("交付结果可验证"),
                "proof required",
                "money settlement",
                "none",
                "none",
                null,
                null,
                "normal",
                false,
                Map.of(),
                Map.of("paymentMethod", "okx_direct_pay"),
                Map.copyOf(orderMetadata),
                now,
                now);
    }

    private PaymentIntentEntity paymentIntent(PaymentIntentStatus status) {
        Instant now = Instant.parse("2026-05-06T00:00:00Z");
        return new PaymentIntentEntity(
                "payment-intent-1",
                "MF260506PAY000001A",
                "order-money-1",
                "acct-buyer",
                "okx",
                "provider-ref",
                status,
                2500,
                "USD",
                "callback-token",
                null,
                status == PaymentIntentStatus.CAPTURED ? now : null,
                null,
                null,
                null,
                Map.of("paymentMethod", "okx_direct_pay"),
                now,
                now);
    }
}
