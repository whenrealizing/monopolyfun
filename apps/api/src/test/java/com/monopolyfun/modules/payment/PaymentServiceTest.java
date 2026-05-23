package com.monopolyfun;

import com.monopolyfun.config.PaymentConfig;
import com.monopolyfun.modules.delivery.service.InstantFulfillmentService;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.command.PaymentService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.api.request.CreatePaymentIntentRequest;
import com.monopolyfun.modules.payment.api.request.OkxA2aCallbackRequest;
import com.monopolyfun.modules.payment.api.request.PaymentCallbackRequest;
import com.monopolyfun.modules.payment.api.response.PaymentIntentResponse;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.payment.infra.PaymentProviderEventRepository;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPayClient;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPayCreateRequest;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPaySession;
import com.monopolyfun.modules.payment.service.PaymentProviderEventService;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.modules.settlement.service.SettlementEventService;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.work.service.OrderWorkItemPublisher;
import com.monopolyfun.shared.id.BusinessIdService;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentServiceTest {
    private static BusinessIdService businessIdService() {
        return new BusinessIdService((type, bizDate) -> 1L);
    }

    private static PaymentProviderEventService paymentProviderEventService() {
        return new PaymentProviderEventService(new InMemoryPaymentProviderEventRepository());
    }

    private static SettlementEventService settlementEventService() {
        return new SettlementEventService(new InMemorySettlementEventRepository());
    }

    private static InstantFulfillmentService instantFulfillmentService() {
        InstantFulfillmentService service = Mockito.mock(InstantFulfillmentService.class);
        Mockito.when(service.tryDeliverAfterPayment(Mockito.any(), Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        return service;
    }

    @Test
    void createIntentAndCallbackCaptureForMoneyOrder() throws Exception {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");
        config.setFakeCallbackEnabled(true);

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        traceContextHolder.setTraceId("trace-pay-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        assertNotNull(created.paymentIntent());
        assertEquals("MF", created.paymentIntent().paymentNo().substring(0, 2));
        assertEquals(PaymentIntentStatus.PENDING, created.paymentIntent().status());
        assertEquals(2500, created.paymentIntent().amountMinor());

        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                created.paymentIntent().id(),
                paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow().callbackToken(),
                "provider-ref-1",
                "captured",
                2500);
        String signature = sign(config.getCallbackSecret(), callback);

        var captured = service.handleCallback(callback, signature);
        assertEquals(PaymentIntentStatus.CAPTURED, captured.status());
        assertEquals(PaymentIntentStatus.CAPTURED, service.requireSettledMoneyPayment("order-money-1").status());
    }

    @Test
    void requestMoneyPaymentWindowStartsWhenBuyerCreatesIntent() {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");
        config.setFakeCallbackEnabled(true);

        StubOrderRepository orderRepository = new StubOrderRepository(
                PostKind.REQUEST,
                Map.of(
                        "buyerAccountId", "acct-buyer",
                        "sellerAccountId", "acct-seller",
                        "fulfillerAccountId", "acct-seller",
                        "acceptorAccountId", "acct-buyer",
                        "itemId", "listing-money-1"));
        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        traceContextHolder.setTraceId("trace-request-pay-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                orderRepository,
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        assertNull(orderRepository.findById("order-money-1").orElseThrow().metadata().get("paymentDueAt"));

        service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        assertNotNull(orderRepository.savedOrder.metadata().get("paymentDueAt"));
        assertNotNull(paymentIntentRepository.findByOrderId("order-money-1").orElseThrow().metadata().get("paymentDueAt"));
    }

    @Test
    void fakeCallbackDoesNotCaptureAfterOrderFinalStatus() throws Exception {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");
        config.setFakeCallbackEnabled(true);

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        StubOrderRepository orderRepository = new StubOrderRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                orderRepository,
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        orderRepository.status = OrderStatus.FINAL_CLOSED;

        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                created.paymentIntent().id(),
                paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow().callbackToken(),
                "provider-ref-late",
                "captured",
                2500);

        var rejected = service.handleCallback(callback, sign(config.getCallbackSecret(), callback));
        assertEquals(PaymentIntentStatus.DISPUTED, rejected.status());
        assertEquals(true, rejected.metadata().get("lateCaptureRejected"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.requireSettledMoneyPayment("order-money-1"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void createIntentConvertsDecimalMoneyAmountToMinorUnits() {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");
        config.setFakeCallbackEnabled(true);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(new BigDecimal("0.01")),
                new InMemoryPaymentIntentRepository(),
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        assertEquals(1, created.paymentIntent().amountMinor());
    }

    @Test
    void createIntentBindsOrderCounterpartiesForPaymentReadback() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));
        PaymentConfig config = new PaymentConfig();
        config.setCurrency("USD");
        PaymentService service = new PaymentService(
                new StubOrderRepository(),
                new InMemoryPaymentIntentRepository(),
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        assertEquals("MF260505ORD000001X", created.paymentIntent().binding().orderNo());
        assertEquals("acct-buyer", created.paymentIntent().binding().payerAccountId());
        assertEquals("acct-seller", created.paymentIntent().binding().payeeAccountId());
        assertEquals("listing-money-1", created.paymentIntent().binding().itemId());
    }

    @Test
    void fakeCallbackIsHiddenWhenDevelopmentSwitchIsDisabled() throws Exception {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        traceContextHolder.setTraceId("trace-pay-disabled-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                created.paymentIntent().id(),
                paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow().callbackToken(),
                "provider-ref-1",
                "captured",
                2500);
        String signature = sign(config.getCallbackSecret(), callback);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.handleCallback(callback, signature));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void okxPaymentReturnsRequirementsThenCapturesAfterPayloadSettlement() {
        PaymentConfig config = new PaymentConfig();
        config.getOkx().setDefaultRecipient("0x1111111111111111111111111111111111111111");

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        traceContextHolder.setTraceId("trace-okx-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(Map.of(
                        "paymentMethod", "okx_direct_pay",
                        "currency", "USDC",
                        "paymentRecipient", "0x1111111111111111111111111111111111111111")),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new CompletedOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse prepared = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        assertEquals("MF", prepared.paymentIntent().paymentNo().substring(0, 2));
        assertEquals(PaymentIntentStatus.PENDING, prepared.paymentIntent().status());
        assertEquals("okx", prepared.paymentIntent().provider());
        assertNotNull(prepared.paymentIntent().metadata().get("paymentRequirements"));

        PaymentIntentResponse captured = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                signedX402Payload(),
                true,
                okxReconciliation(2500)));
        assertEquals(PaymentIntentStatus.CAPTURED, captured.paymentIntent().status());
        assertEquals("0xtx", captured.paymentIntent().providerPaymentRef());
        assertEquals("0xtx", captured.paymentIntent().metadata().get("txHash"));
        assertEquals(PaymentIntentStatus.CAPTURED, service.requireSettledMoneyPayment("order-money-1").status());
    }

    @Test
    void okxCapturedPaymentRequiresChainReconciliationBeforeSettlementUnlock() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
        service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        PaymentIntentResponse captured = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                signedX402Payload(),
                true,
                Map.of()));

        assertEquals(PaymentIntentStatus.CAPTURED, captured.paymentIntent().status());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.requireSettledMoneyPayment("order-money-1"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void okxProviderSettledStatusUnlocksMoneySettlement() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new SettledOkxOnchainPayClient());
        service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        PaymentIntentResponse captured = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                signedX402Payload(),
                true,
                Map.of()));

        assertEquals(PaymentIntentStatus.CAPTURED, captured.paymentIntent().status());
        assertEquals("okx-settle-1", captured.paymentIntent().providerPaymentRef());
        assertEquals("0xtx", captured.paymentIntent().metadata().get("txHash"));
        assertEquals(PaymentIntentStatus.CAPTURED, service.requireSettledMoneyPayment("order-money-1").status());
    }

    @Test
    void okxSynchronousCapturePublishesSellerDeliveryWorkItem() {
        OrderWorkItemPublisher publisher = Mockito.mock(OrderWorkItemPublisher.class);
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new SettledOkxOnchainPayClient(), publisher);
        service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                signedX402Payload(),
                true,
                Map.of()));

        Mockito.verify(publisher).publishPaymentCaptured(
                Mockito.argThat(order -> "MF260505ORD000001X".equals(order.orderNo())),
                Mockito.any(Instant.class));
    }

    @Test
    void okxA2aCallbackCapturesWithChainEvidenceAndIsIdempotent() {
        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        OrderWorkItemPublisher publisher = Mockito.mock(OrderWorkItemPublisher.class);
        PaymentService service = okxPaymentService(paymentIntentRepository, new CompletedOkxOnchainPayClient(), publisher);
        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));

        OkxA2aCallbackRequest callback = okxA2aCallback(created.paymentIntent().id(), "completed", 2500, "0xa2a");
        String timestamp = Instant.now().toString();
        var captured = service.handleOkxA2aCallback(callback, signOkxA2a("okx-secret", callback, timestamp), timestamp);

        assertEquals(PaymentIntentStatus.CAPTURED, captured.status());
        assertEquals("0xa2a", captured.providerPaymentRef());
        assertEquals("0xa2a", captured.metadata().get("txHash"));
        assertEquals(PaymentIntentStatus.CAPTURED, service.requireSettledMoneyPayment("order-money-1").status());

        var duplicate = service.handleOkxA2aCallback(callback, signOkxA2a("okx-secret", callback, timestamp), timestamp);
        assertEquals(PaymentIntentStatus.CAPTURED, duplicate.status());
        Mockito.verify(publisher, Mockito.times(2)).publishPaymentCaptured(
                Mockito.argThat(order -> "MF260505ORD000001X".equals(order.orderNo())),
                Mockito.any(Instant.class));
    }

    @Test
    void okxA2aIntentBindsExternalPaymentIdBeforeCallback() {
        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        PaymentService service = okxPaymentService(paymentIntentRepository, new CompletedOkxOnchainPayClient());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                Map.of(
                        "providerFlow", "okx_a2a",
                        "paymentId", "a2a_real_external_payment",
                        "paymentUrl", "https://web3.okx.com/api/v6/pay/a2a/p/a2a_real_external_payment"),
                false,
                Map.of()));

        assertEquals(PaymentIntentStatus.PENDING, created.paymentIntent().status());
        assertEquals("a2a_real_external_payment", created.paymentIntent().providerPaymentRef());
        assertEquals("a2a_real_external_payment", created.paymentIntent().metadata().get("paymentId"));

        OkxA2aCallbackRequest callback = okxA2aCallback(
                "a2a_real_external_payment",
                created.paymentIntent().id(),
                "completed",
                2500,
                "0xa2areal");
        String timestamp = Instant.now().toString();
        var captured = service.handleOkxA2aCallback(callback, signOkxA2a("okx-secret", callback, timestamp), timestamp);

        assertEquals(PaymentIntentStatus.CAPTURED, captured.status());
        assertEquals("0xa2areal", captured.providerPaymentRef());
        assertEquals(PaymentIntentStatus.CAPTURED, service.requireSettledMoneyPayment("order-money-1").status());
    }

    @Test
    void okxA2aCallbackRejectsAmountMismatch() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        OkxA2aCallbackRequest callback = okxA2aCallback(created.paymentIntent().id(), "completed", 2400, "0xa2a");
        String timestamp = Instant.now().toString();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.handleOkxA2aCallback(callback, signOkxA2a("okx-secret", callback, timestamp), timestamp));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void okxA2aCallbackRejectsPayerMismatchWhenPaymentAddressWasBound() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest(
                "acct-buyer",
                "0x2222222222222222222222222222222222222222",
                Map.of(
                        "providerFlow", "okx_a2a",
                        "paymentId", "a2a_bound_payer"),
                false,
                Map.of()));
        OkxA2aCallbackRequest callback = new OkxA2aCallbackRequest(
                "a2a_bound_payer",
                created.paymentIntent().id(),
                "MF260505ORD000001X",
                2500,
                "USDC",
                "0x1111111111111111111111111111111111111111",
                "0x3333333333333333333333333333333333333333",
                "0xa2apayer",
                "completed",
                "evt-0xa2apayer",
                "eip155:196",
                "success",
                1,
                "docs/evidence/okx-a2a-real-payment/test/reconciliation.json");
        String timestamp = Instant.now().toString();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.handleOkxA2aCallback(callback, signOkxA2a("okx-secret", callback, timestamp), timestamp));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void okxA2aCallbackRejectsBadSignature() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        OkxA2aCallbackRequest callback = okxA2aCallback(created.paymentIntent().id(), "completed", 2500, "0xa2a");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.handleOkxA2aCallback(callback, "bad-signature", Instant.now().toString()));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void authorizedPaymentDoesNotUnlockMoneySettlement() throws Exception {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setCurrency("USD");
        config.setFakeCallbackEnabled(true);

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                created.paymentIntent().id(),
                paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow().callbackToken(),
                "provider-ref-authorized",
                "authorized",
                2500);
        service.handleCallback(callback, sign(config.getCallbackSecret(), callback));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.requireSettledMoneyPayment("order-money-1"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void okxPaymentPayloadRequiresX402Fields() {
        List<Map<String, Object>> invalidPayloads = List.of(
                Map.of(
                        "x402Version", 2,
                        "accepted", x402Accepted("0x1111111111111111111111111111111111111111"),
                        "payload", Map.of("signature", "0xsig")),
                Map.of(
                        "x402Version", 2,
                        "accepted", x402Accepted("0x1111111111111111111111111111111111111111"),
                        "payload", Map.of("authorization", x402Authorization(), "signature", "")),
                Map.of(
                        "x402Version", 2,
                        "accepted", x402Accepted(""),
                        "payload", Map.of("authorization", x402Authorization(), "signature", "0xsig")),
                Map.of(
                        "x402Version", 2,
                        "accepted", x402Accepted("0x1111111111111111111111111111111111111111"),
                        "payload", Map.of("authorization", x402AuthorizationMissingTo(), "signature", "0xsig")));

        for (Map<String, Object> payload : invalidPayloads) {
            PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                    service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, payload, true, Map.of())));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
    }

    @Test
    void okxPaymentPayloadRejectsX402RecipientMismatch() {
        PaymentService service = okxPaymentService(new InMemoryPaymentIntentRepository(), new CompletedOkxOnchainPayClient());
        Map<String, Object> payload = Map.of(
                "x402Version", 2,
                "accepted", x402Accepted("0x3333333333333333333333333333333333333333"),
                "payload", Map.of("authorization", x402Authorization(), "signature", "0xsig"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, payload, true, Map.of())));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private Map<String, Object> signedX402Payload() {
        return Map.of(
                "x402Version", 2,
                "accepted", Map.of(
                        "scheme", "exact",
                        "network", "eip155:196",
                        "asset", "0x74b7f16337b8972027f6196a17a631ac6de26d22",
                        "amount", "25000000",
                        "payTo", "0x1111111111111111111111111111111111111111"),
                "payload", Map.of(
                        "authorization", Map.of(
                                "from", "0x2222222222222222222222222222222222222222",
                                "to", "0x1111111111111111111111111111111111111111",
                                "value", "25000000",
                                "validAfter", "0",
                                "validBefore", "9999999999",
                                "nonce", "0x0000000000000000000000000000000000000000000000000000000000000001"),
                        "signature", "0xsig"));
    }

    private Map<String, Object> okxReconciliation(int amountMinor) {
        return Map.of(
                "runId", "okx-reconciliation-test",
                "amountMinor", amountMinor,
                "network", "eip155:196",
                "recipient", "0x1111111111111111111111111111111111111111",
                "evidencePath", "docs/evidence/okx-real-payment/okx-reconciliation-test/reconciliation.json",
                "verify", Map.of("isValid", true),
                "settle", Map.of("success", true, "transaction", "0xtx"),
                "status", Map.of("status", "success", "transaction", "0xtx"),
                "chain", Map.of(
                        "txHash", "0xtx",
                        "receiptStatus", "success",
                        "transferLogCount", 1,
                        "payerBefore", "25.00",
                        "payerAfter", "0.00",
                        "recipientBefore", "0.00",
                        "recipientAfter", "25.00"));
    }

    private PaymentService okxPaymentService(InMemoryPaymentIntentRepository paymentIntentRepository, OkxOnchainPayClient client) {
        return okxPaymentService(paymentIntentRepository, client, null);
    }

    private PaymentService okxPaymentService(InMemoryPaymentIntentRepository paymentIntentRepository, OkxOnchainPayClient client, OrderWorkItemPublisher orderWorkItemPublisher) {
        PaymentConfig config = new PaymentConfig();
        config.getOkx().setDefaultRecipient("0x1111111111111111111111111111111111111111");
        config.getOkx().setApiSecret("okx-secret");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));
        return new PaymentService(
                new StubOrderRepository(Map.of(
                        "paymentMethod", "okx_direct_pay",
                        "currency", "USDC",
                        "paymentRecipient", "0x1111111111111111111111111111111111111111")),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                client,
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService(),
                orderWorkItemPublisher);
    }

    private OkxA2aCallbackRequest okxA2aCallback(String paymentId, String status, int amountMinor, String txHash) {
        return okxA2aCallback(paymentId, paymentId, status, amountMinor, txHash);
    }

    private OkxA2aCallbackRequest okxA2aCallback(String paymentId, String intentId, String status, int amountMinor, String txHash) {
        return new OkxA2aCallbackRequest(
                paymentId,
                intentId,
                "MF260505ORD000001X",
                amountMinor,
                "USDC",
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                txHash,
                status,
                "evt-" + txHash,
                "eip155:196",
                "success",
                1,
                "docs/evidence/okx-a2a-real-payment/test/reconciliation.json");
    }

    private String signOkxA2a(String secret, OkxA2aCallbackRequest request, String timestamp) {
        String payload = "%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s".formatted(
                timestamp,
                request.callbackEventId(),
                request.paymentId(),
                request.intentId(),
                request.orderNo(),
                request.status().toLowerCase(),
                request.amountMinor(),
                request.currency(),
                request.recipient(),
                request.txHash(),
                request.network());
        return hmac(secret, payload);
    }

    private Map<String, Object> x402Accepted(String payTo) {
        return Map.of(
                "scheme", "exact",
                "network", "eip155:196",
                "asset", "0x74b7f16337b8972027f6196a17a631ac6de26d22",
                "amount", "25000000",
                "payTo", payTo);
    }

    private Map<String, Object> x402Authorization() {
        return Map.of(
                "from", "0x2222222222222222222222222222222222222222",
                "to", "0x1111111111111111111111111111111111111111",
                "value", "25000000",
                "validAfter", "0",
                "validBefore", "9999999999",
                "nonce", "0x0000000000000000000000000000000000000000000000000000000000000001");
    }

    private Map<String, Object> x402AuthorizationMissingTo() {
        return Map.of(
                "from", "0x2222222222222222222222222222222222222222",
                "value", "25000000",
                "validAfter", "0",
                "validBefore", "9999999999",
                "nonce", "0x0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    void localFakeCallbackCanCaptureOkxDirectOrderWithoutOkxCredentials() throws Exception {
        PaymentConfig config = new PaymentConfig();
        config.setCallbackSecret("secret-123");
        config.setPublicBaseUrl("http://localhost:8080");
        config.setFakeCallbackEnabled(true);

        InMemoryPaymentIntentRepository paymentIntentRepository = new InMemoryPaymentIntentRepository();
        TraceContextHolder traceContextHolder = new TraceContextHolder();
        traceContextHolder.setTraceId("trace-okx-fake-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-buyer", "@buyer", "Buyer"),
                null,
                List.of()));

        PaymentService service = new PaymentService(
                new StubOrderRepository(Map.of(
                        "paymentMethod", "okx_direct_pay",
                        "currency", "USDC",
                        "paymentRecipient", "0x1111111111111111111111111111111111111111")),
                paymentIntentRepository,
                new CurrentAccountAccess(),
                config,
                new NoopOkxOnchainPayClient(),
                new NoopAuditEventRecorder(),
                traceContextHolder,
                new RiskEventService(new NoopRiskEventRepository(), traceContextHolder),
                businessIdService(),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                paymentProviderEventService(),
                settlementEventService(),
                instantFulfillmentService());

        PaymentIntentResponse created = service.createIntent("MF260505ORD000001X", new CreatePaymentIntentRequest("acct-buyer", null, Map.of(), false, Map.of()));
        assertEquals("fake", created.paymentIntent().provider());

        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                created.paymentIntent().id(),
                paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow().callbackToken(),
                "provider-ref-okx-fake",
                "captured",
                2500);
        var captured = service.handleCallback(callback, sign(config.getCallbackSecret(), callback));
        assertEquals(PaymentIntentStatus.CAPTURED, captured.status());
    }

    private String sign(String secret, PaymentCallbackRequest request) throws Exception {
        String payload = "%s:%s:%s:%s:%s".formatted(
                request.intentId(),
                request.callbackToken(),
                request.providerPaymentRef(),
                request.status().toLowerCase(),
                request.amountMinor());
        return hmac(secret, payload);
    }

    private String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class StubOrderRepository implements OrderRepository {
        private final Map<String, Object> settlementSnapshot;
        private final BigDecimal settlementAmount;
        private final PostKind postKind;
        private final Map<String, Object> metadata;
        private OrderStatus status = OrderStatus.CLAIMED;
        private OrderEntity savedOrder;

        private StubOrderRepository() {
            this(BigDecimal.valueOf(25), Map.of(), PostKind.OFFER, Map.of());
        }

        private StubOrderRepository(BigDecimal settlementAmount) {
            this(settlementAmount, Map.of(), PostKind.OFFER, Map.of());
        }

        private StubOrderRepository(Map<String, Object> settlementSnapshot) {
            this(BigDecimal.valueOf(25), settlementSnapshot, PostKind.OFFER, Map.of());
        }

        private StubOrderRepository(PostKind postKind, Map<String, Object> metadata) {
            this(BigDecimal.valueOf(25), Map.of(), postKind, metadata);
        }

        private StubOrderRepository(BigDecimal settlementAmount, Map<String, Object> settlementSnapshot) {
            this(settlementAmount, settlementSnapshot, PostKind.OFFER, Map.of());
        }

        private StubOrderRepository(BigDecimal settlementAmount, Map<String, Object> settlementSnapshot, PostKind postKind, Map<String, Object> metadata) {
            this.settlementAmount = settlementAmount;
            this.settlementSnapshot = settlementSnapshot;
            this.postKind = postKind;
            this.metadata = metadata;
        }

        @Override
        public List<OrderEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public List<OrderEntity> findByParticipantAccountId(String accountId, int limit) {
            return List.of();
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<OrderEntity> findByParticipantAccountId(
                String accountId,
                com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return new com.monopolyfun.shared.pagination.PageResult<>(
                    List.of(),
                    new com.monopolyfun.shared.pagination.PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public List<OrderEntity> findWorkbenchCandidates(String accountId, int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findDisputed(int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findExpiredPaymentLocks(Instant dueAt, int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findExpiredDisputeWindows(Instant dueAt, int limit) {
            return List.of();
        }

        @Override
        public List<SettlementAnomaly> findSettlementAnomalies(int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findByMarketId(String marketId) {
            return List.of();
        }

        @Override
        public Optional<OrderEntity> findById(String id) {
            if (savedOrder != null) {
                return Optional.of(savedOrder);
            }
            return Optional.of(new OrderEntity(
                    id,
                    "MF260505ORD000001X",
                    "mkt-money-1",
                    "listing-money-1",
                    ListingKind.WORK,
                    postKind,
                    postKind == PostKind.REQUEST ? "request-test" : "offer-test",
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
                    settlementAmount,
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
                    List.of("payment verified"),
                    "payment proof",
                    "money settlement",
                    "none",
                    "none",
                    null,
                    null,
                    "normal",
                    false,
                    Map.of(),
                    settlementSnapshot,
                    mergedMetadata(Map.of(
                            "buyerAccountId", "acct-buyer",
                            "sellerAccountId", "acct-seller",
                            "fulfillerAccountId", "acct-seller",
                            "acceptorAccountId", "acct-buyer",
                            "itemId", "listing-money-1")),
                    Instant.now(),
                    Instant.now()));
        }

        private Map<String, Object> mergedMetadata(Map<String, Object> base) {
            Map<String, Object> merged = new HashMap<>(base);
            merged.putAll(metadata);
            return Map.copyOf(merged);
        }

        @Override
        public Optional<OrderEntity> findByOrderNo(String orderNo) {
            return findById("order-money-1").filter(order -> order.orderNo().equals(orderNo));
        }

        @Override
        public Optional<OrderEntity> findFirstByListingId(String listingId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderEntity> findFirstByParentOrderId(String parentOrderId) {
            return Optional.empty();
        }

        @Override
        public OrderEntity save(OrderEntity order) {
            savedOrder = order;
            return savedOrder;
        }
    }

    private static final class StubMarketRepository implements MarketRepository {
        @Override
        public List<MarketEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public Optional<MarketEntity> findById(String id) {
            return Optional.of(new MarketEntity(
                    id,
                    "Market",
                    "Summary",
                    "Goal",
                    "acct-lead",
                    "source",
                    "https://example.com",
                    SettlementType.MONEY,
                    1,
                    MarketStatus.ACTIVE,
                    Instant.now(),
                    "occupied",
                    Map.of(),
                    Instant.now(),
                    Instant.now()));
        }

        @Override
        public MarketEntity save(MarketEntity market) {
            return market;
        }
    }

    private static final class InMemoryPaymentIntentRepository implements PaymentIntentRepository {
        private final Map<String, PaymentIntentEntity> byId = new HashMap<>();

        @Override
        public Optional<PaymentIntentEntity> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<PaymentIntentEntity> findByPaymentNo(String paymentNo) {
            return byId.values().stream().filter(intent -> intent.paymentNo().equals(paymentNo)).findFirst();
        }

        @Override
        public Optional<PaymentIntentEntity> findByOrderId(String orderId) {
            return byId.values().stream().filter(intent -> intent.orderId().equals(orderId)).findFirst();
        }

        @Override
        public List<PaymentIntentEntity> findRecent(int limit) {
            return byId.values().stream().limit(limit).toList();
        }

        @Override
        public List<PaymentIntentEntity> findAll() {
            return byId.values().stream().toList();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public PaymentIntentEntity save(PaymentIntentEntity paymentIntent) {
            byId.put(paymentIntent.id(), paymentIntent);
            return paymentIntent;
        }
    }

    private static final class InMemoryPaymentProviderEventRepository implements PaymentProviderEventRepository {
        private final Map<String, PaymentProviderEventEntity> events = new HashMap<>();

        @Override
        public Optional<PaymentProviderEventEntity> findByProviderEventId(String provider, String providerEventId) {
            return Optional.ofNullable(events.get(provider + ":" + providerEventId));
        }

        @Override
        public PaymentProviderEventEntity save(PaymentProviderEventEntity event) {
            events.putIfAbsent(event.provider() + ":" + event.providerEventId(), event);
            return events.get(event.provider() + ":" + event.providerEventId());
        }
    }

    private static final class InMemorySettlementEventRepository implements SettlementEventRepository {
        private final Map<String, SettlementEventEntity> events = new HashMap<>();

        @Override
        public Optional<SettlementEventEntity> findByUniqueKey(String orderId, String eventType, String idempotencyKey) {
            return Optional.ofNullable(events.get(orderId + ":" + eventType + ":" + idempotencyKey));
        }

        @Override
        public SettlementEventEntity save(SettlementEventEntity event) {
            events.putIfAbsent(event.orderId() + ":" + event.eventType() + ":" + event.idempotencyKey(), event);
            return events.get(event.orderId() + ":" + event.eventType() + ":" + event.idempotencyKey());
        }

        @Override
        public List<SettlementEventEntity> findByOrderId(String orderId) {
            return events.values().stream().filter(event -> event.orderId().equals(orderId)).toList();
        }
    }

    private static final class NoopAuditEventRecorder implements AuditEventRecorder {
        @Override
        public void record(AuditEvent event) {
        }
    }

    private static final class NoopOkxOnchainPayClient implements OkxOnchainPayClient {
        @Override
        public Map<String, Object> buildPaymentRequirements(int amountMinor, String asset, String recipient) {
            return Map.of();
        }

        @Override
        public OkxOnchainPaySession createPayment(OkxOnchainPayCreateRequest request) {
            return new OkxOnchainPaySession(
                    request.idempotencyKey(),
                    null,
                    "pending",
                    request.amountMinor(),
                    request.asset(),
                    request.network(),
                    request.recipient(),
                    request.payer(),
                    null,
                    Map.of(),
                    null,
                    request.orderId(),
                    null,
                    Map.of());
        }

        @Override
        public OkxOnchainPaySession getPaymentStatus(String paymentSessionId, OkxOnchainPayCreateRequest request) {
            return createPayment(request);
        }
    }

    private static class CompletedOkxOnchainPayClient implements OkxOnchainPayClient {
        @Override
        public Map<String, Object> buildPaymentRequirements(int amountMinor, String asset, String recipient) {
            return Map.of(
                    "scheme", "exact",
                    "network", "eip155:196",
                    "asset", "0x74b7f16337b8972027f6196a17a631ac6de26d22",
                    "amount", String.valueOf((long) amountMinor * 10_000L),
                    "payTo", recipient == null ? "0x1111111111111111111111111111111111111111" : recipient,
                    "maxTimeoutSeconds", 300,
                    "extra", Map.of("name", "USD Coin", "version", "2"));
        }

        @Override
        public OkxOnchainPaySession createPayment(OkxOnchainPayCreateRequest request) {
            Map<String, Object> requirements = buildPaymentRequirements(request.amountMinor(), request.asset(), request.recipient());
            if (request.paymentPayload() == null || request.paymentPayload().isEmpty()) {
                return new OkxOnchainPaySession(
                        request.idempotencyKey(),
                        null,
                        "requires_payment",
                        request.amountMinor(),
                        request.asset(),
                        request.network(),
                        request.recipient(),
                        request.payer(),
                        null,
                        requirements,
                        null,
                        request.orderId(),
                        null,
                        Map.of("paymentRequirements", requirements));
            }
            return new OkxOnchainPaySession(
                    "okx-settle-1",
                    null,
                    "completed",
                    request.amountMinor(),
                    request.asset(),
                    request.network(),
                    request.recipient(),
                    request.payer(),
                    "0xtx",
                    requirements,
                    "okx-settle-1",
                    request.orderId(),
                    null,
                    Map.of("paymentRequirements", requirements, "settlement", Map.of("status", "completed")));
        }

        @Override
        public OkxOnchainPaySession getPaymentStatus(String paymentSessionId, OkxOnchainPayCreateRequest request) {
            return createPayment(new OkxOnchainPayCreateRequest(
                    request.orderId(),
                    request.idempotencyKey(),
                    request.amountMinor(),
                    request.asset(),
                    request.network(),
                    request.recipient(),
                    request.payer(),
                    Map.of("payload", Map.of()),
                    true,
                    request.metadata()));
        }
    }

    private static final class SettledOkxOnchainPayClient extends CompletedOkxOnchainPayClient {
        @Override
        public OkxOnchainPaySession createPayment(OkxOnchainPayCreateRequest request) {
            if (request.paymentPayload() == null || request.paymentPayload().isEmpty()) {
                return super.createPayment(request);
            }
            Map<String, Object> requirements = buildPaymentRequirements(request.amountMinor(), request.asset(), request.recipient());
            return new OkxOnchainPaySession(
                    "okx-settle-1",
                    null,
                    "completed",
                    request.amountMinor(),
                    request.asset(),
                    request.network(),
                    request.recipient(),
                    request.payer(),
                    "0xtx",
                    requirements,
                    "okx-settle-1",
                    request.orderId(),
                    null,
                    Map.of(
                            "verify", Map.of("isValid", true, "payer", request.payer()),
                            "settlement", Map.of("success", true, "transaction", "0xtx"),
                            "status", Map.of("success", true, "status", "success", "transaction", "0xtx")));
        }
    }

    private static final class NoopRiskEventRepository implements RiskEventRepository {
        @Override
        public RiskEventEntity save(RiskEventEntity event) {
            return event;
        }

        @Override
        public List<RiskEventEntity> findRecent(int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findRecentByAccount(String accountId, int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countBySeverity() {
            return Map.of();
        }
    }
}
