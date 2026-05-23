package com.monopolyfun.modules.order.service.command;

import com.monopolyfun.config.PaymentConfig;
import com.monopolyfun.modules.delivery.service.InstantFulfillmentService;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.api.request.CreatePaymentIntentRequest;
import com.monopolyfun.modules.payment.api.request.OkxA2aCallbackRequest;
import com.monopolyfun.modules.payment.api.request.PaymentActionRequest;
import com.monopolyfun.modules.payment.api.request.PaymentCallbackRequest;
import com.monopolyfun.modules.payment.api.response.PaymentIntentResponse;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPayClient;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPayCreateRequest;
import com.monopolyfun.modules.payment.infra.okx.OkxOnchainPaySession;
import com.monopolyfun.modules.payment.service.PaymentProviderEventService;
import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.settlement.service.SettlementEventService;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.work.service.OrderWorkItemPublisher;
import com.monopolyfun.shared.error.ApiStatusException;
import com.monopolyfun.shared.id.BusinessIdService;
import com.monopolyfun.shared.id.BusinessIdType;
import com.monopolyfun.shared.id.BusinessIds;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private static final String OKX_PAYMENT_METHOD = "okx_direct_pay";
    private static final String OKX_DIRECT_PAY_NETWORK = "eip155:196";
    private static final String X402_VERSION = "x402Version";
    private static final String X402_ACCEPTED = "accepted";
    private static final String X402_PAYLOAD = "payload";
    private static final String X402_AUTHORIZATION = "authorization";
    private static final String X402_SIGNATURE = "signature";
    private static final Duration DEFAULT_PAYMENT_WINDOW = Duration.ofMinutes(30);
    private static final Duration OKX_CALLBACK_TIMESTAMP_WINDOW = Duration.ofMinutes(5);

    private final OrderRepository orderRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final PaymentConfig paymentConfig;
    private final OkxOnchainPayClient okxOnchainPayClient;
    private final AuditEventRecorder auditEventRecorder;
    private final TraceContextHolder traceContextHolder;
    private final RiskEventService riskEventService;
    private final BusinessIdService businessIdService;
    private final RateLimitService rateLimitService;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final PaymentProviderEventService paymentProviderEventService;
    private final SettlementEventService settlementEventService;
    private final InstantFulfillmentService instantFulfillmentService;
    private final OrderWorkItemPublisher orderWorkItemPublisher;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentIntentRepository paymentIntentRepository,
            CurrentAccountAccess currentAccountAccess,
            PaymentConfig paymentConfig,
            OkxOnchainPayClient okxOnchainPayClient,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder,
            RiskEventService riskEventService,
            BusinessIdService businessIdService,
            RateLimitService rateLimitService,
            OrganizationAuthorityService organizationAuthorityService,
            PaymentProviderEventService paymentProviderEventService,
            SettlementEventService settlementEventService,
            InstantFulfillmentService instantFulfillmentService) {
        this(
                orderRepository,
                paymentIntentRepository,
                currentAccountAccess,
                paymentConfig,
                okxOnchainPayClient,
                auditEventRecorder,
                traceContextHolder,
                riskEventService,
                businessIdService,
                rateLimitService,
                organizationAuthorityService,
                paymentProviderEventService,
                settlementEventService,
                instantFulfillmentService,
                null);
    }

    @Autowired
    public PaymentService(
            OrderRepository orderRepository,
            PaymentIntentRepository paymentIntentRepository,
            CurrentAccountAccess currentAccountAccess,
            PaymentConfig paymentConfig,
            OkxOnchainPayClient okxOnchainPayClient,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder,
            RiskEventService riskEventService,
            BusinessIdService businessIdService,
            RateLimitService rateLimitService,
            OrganizationAuthorityService organizationAuthorityService,
            PaymentProviderEventService paymentProviderEventService,
            SettlementEventService settlementEventService,
            InstantFulfillmentService instantFulfillmentService,
            OrderWorkItemPublisher orderWorkItemPublisher) {
        this.orderRepository = orderRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.paymentConfig = paymentConfig;
        this.okxOnchainPayClient = okxOnchainPayClient;
        this.auditEventRecorder = auditEventRecorder;
        this.traceContextHolder = traceContextHolder;
        this.riskEventService = riskEventService;
        this.businessIdService = businessIdService;
        this.rateLimitService = rateLimitService;
        this.organizationAuthorityService = organizationAuthorityService;
        this.paymentProviderEventService = paymentProviderEventService;
        this.settlementEventService = settlementEventService;
        this.instantFulfillmentService = instantFulfillmentService;
        this.orderWorkItemPublisher = orderWorkItemPublisher;
    }

    public PaymentIntentResponse createIntent(String orderId, CreatePaymentIntentRequest request) {
        currentAccountAccess.requireSameAccount(request.accountId());
        RequestPayloadLimits.requireTextLength("payer", request.payer(), 120);
        RequestPayloadLimits.requireMapShape("paymentPayload", request.paymentPayload(), 4, 80, 2000);
        RequestPayloadLimits.requireMapShape("reconciliation", request.reconciliation(), 4, 80, 2000);
        OrderEntity order = requireOrder(orderId);
        if (!request.accountId().equals(order.buyerAccountId())) {
            throw api(HttpStatus.FORBIDDEN, "payment.actor.order_buyer_required", "Payment actor must match order buyer", Map.of("orderNo", order.orderNo()));
        }
        if (order.settlementType() != SettlementType.MONEY) {
            throw api(HttpStatus.BAD_REQUEST, "payment.order.not_money_settlement", "Order is not a money settlement", Map.of("orderNo", order.orderNo()));
        }
        if (order.status() != OrderStatus.CLAIMED && order.status() != OrderStatus.DELIVERED) {
            throw api(HttpStatus.CONFLICT, "payment.intent.invalid_order_state", "Payment intent can only be created for claimed or delivered orders", Map.of("orderNo", order.orderNo(), "status", order.status().name()));
        }
        if (order.settlementFrozen()) {
            throw api(HttpStatus.CONFLICT, "payment.settlement.frozen", "Disputed order settlement is frozen", Map.of("orderNo", order.orderNo()));
        }
        PaymentIntentEntity existing = paymentIntentRepository.findByOrderId(order.id()).orElse(null);
        if (!(existing != null && isReusable(existing) && existing.status() == PaymentIntentStatus.CAPTURED)) {
            order = startDeferredRequestPaymentWindow(order);
        }
        int amountMinor = requireAmountMinor(order);
        if (existing != null && isReusable(existing)) {
            if (existing.status() == PaymentIntentStatus.CAPTURED) {
                repairCapturedPaymentSideEffects(order, existing);
                return new PaymentIntentResponse(
                        com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(existing),
                        checkoutUrl(existing));
            }
            if (isOkxOrder(order) && !useFakeCallbackForOkxOrder() && hasOkxA2aPaymentPayload(request)) {
                return okxPaymentResponse(bindOkxA2aPayment(order, existing, request));
            }
            if (isOkxOrder(order) && !useFakeCallbackForOkxOrder() && hasPaymentPayload(request)) {
                return okxPaymentResponse(applyOptionalOkxReconciliation(order, submitOkxPayment(order, existing, request), request, request.accountId()));
            }
            if (hasReconciliation(request)) {
                return okxPaymentResponse(applyOkxReconciliation(order, existing, request.reconciliation(), request.accountId()));
            }
            return new PaymentIntentResponse(
                    com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(existing),
                    checkoutUrl(existing));
        }
        if (isOkxOrder(order) && !useFakeCallbackForOkxOrder()) {
            if (hasOkxA2aPaymentPayload(request)) {
                return okxPaymentResponse(startOkxA2aPayment(order, request, amountMinor, existing));
            }
            return okxPaymentResponse(applyOptionalOkxReconciliation(order, startOkxPayment(order, request, amountMinor, existing), request, request.accountId()));
        }
        Instant now = Instant.now();
        PaymentIntentEntity paymentIntent = resetPaymentIntent(
                existing,
                order.id(),
                request.accountId(),
                paymentConfig.getProvider(),
                amountMinor,
                paymentConfig.getCurrency(),
                "cbt-" + UUID.randomUUID(),
                paymentBindingMetadata(order),
                now);
        paymentIntentRepository.save(paymentIntent);
        recordAudit(existing == null ? "payment_intent_created" : "payment_intent_reopened", paymentIntent.id(), request.accountId(), Map.of("orderId", order.id(), "amountMinor", amountMinor));
        settlementEventService.recordOnce(order.id(), paymentIntent.id(), "payment_intent_created", "payment_intent:" + paymentIntent.id(), amountMinor, paymentIntent.currency(), request.accountId(), Map.of("provider", paymentIntent.provider()));
        return new PaymentIntentResponse(
                com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent),
                checkoutUrl(paymentIntent));
    }

    public PaymentIntentView handleCallback(PaymentCallbackRequest request, String signatureHeader) {
        requireFakeCallbackEnabled(request.intentId());
        enforceCallbackRateLimit(request.intentId());
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findById(request.intentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        verifySignature(request, signatureHeader);
        if (!paymentIntent.callbackToken().equals(request.callbackToken())) {
            riskEventService.record("payment_callback_token_mismatch", "payment_intent", paymentIntent.id(), paymentIntent.accountId(), "high", "Callback token mismatch", Map.of());
            throw api(HttpStatus.BAD_REQUEST, "payment.callback.token_mismatch", "Callback token mismatch", Map.of("paymentIntentId", paymentIntent.id()));
        }
        if (paymentIntent.amountMinor() != request.amountMinor()) {
            PaymentIntentEntity failed = paymentIntent.withStatus(PaymentIntentStatus.FAILED, request.providerPaymentRef(), Instant.now(), mergeMetadata(paymentIntent, Map.of("mismatchAmountMinor", request.amountMinor())));
            paymentIntentRepository.save(failed);
            recordAudit("payment_mismatch_detected", failed.id(), paymentIntent.accountId(), Map.of("expected", paymentIntent.amountMinor(), "actual", request.amountMinor()));
            riskEventService.record("payment_amount_mismatch", "payment_intent", failed.id(), paymentIntent.accountId(), "high", "Payment callback amount mismatch", Map.of("expected", paymentIntent.amountMinor(), "actual", request.amountMinor()));
            throw api(HttpStatus.CONFLICT, "payment.amount_mismatch", "Payment amount mismatch", Map.of("expectedAmountMinor", paymentIntent.amountMinor(), "actualAmountMinor", request.amountMinor()));
        }
        PaymentIntentStatus nextStatus = parseProviderStatus(request.status());
        var providerEvent = paymentProviderEventService.recordOnce(
                paymentIntent.provider(),
                "fake:%s:%s:%s".formatted(request.intentId(), request.providerPaymentRef(), request.status().toLowerCase(Locale.ROOT)),
                paymentIntent.id(),
                request.providerPaymentRef(),
                request.providerPaymentRef(),
                request.status(),
                Map.of("amountMinor", request.amountMinor()));
        if (providerEvent.duplicate()) {
            if (paymentIntent.status() == PaymentIntentStatus.CAPTURED) {
                repairCapturedPaymentSideEffects(requireOrder(paymentIntent.orderId()), paymentIntent);
                return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
            }
            if (nextStatus != PaymentIntentStatus.CAPTURED) {
                return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
            }
        }
        Instant now = Instant.now();
        OrderEntity order = nextStatus == PaymentIntentStatus.CAPTURED ? requireOrder(paymentIntent.orderId()) : null;
        if (nextStatus == PaymentIntentStatus.CAPTURED && shouldRejectLateCapture(order, paymentIntent)) {
            PaymentIntentEntity rejected = markLateCaptureRejected(paymentIntent, order, request.providerPaymentRef(), request.status(), now);
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(rejected);
        }
        PaymentIntentEntity updated = paymentIntent.withStatus(nextStatus, request.providerPaymentRef(), now, mergeMetadata(paymentIntent, Map.of("providerStatus", request.status())));
        paymentIntentRepository.save(updated);
        if (nextStatus == PaymentIntentStatus.CAPTURED) {
            finalizeCapturedPayment(
                    order,
                    paymentIntent,
                    updated,
                    now,
                    "payment_captured:" + providerEvent.event().providerEventId(),
                    Map.of("provider", updated.provider(), "providerPaymentRef", updated.providerPaymentRef()));
        }
        recordAudit("payment_callback_applied", updated.id(), paymentIntent.accountId(), Map.of("status", updated.status().name(), "providerPaymentRef", request.providerPaymentRef()));
        return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
    }

    public PaymentIntentView handleOkxA2aCallback(OkxA2aCallbackRequest request, String signatureHeader, String timestampHeader) {
        enforceCallbackRateLimit(request.intentId());
        verifyOkxA2aCallbackSignature(request, signatureHeader, timestampHeader);
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findById(request.intentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        OrderEntity order = requireOrder(paymentIntent.orderId());
        // 中文注释：真实 A2A 回调必须绑定订单和 payment intent，防止其他 paymentId 或收款地址撞库。
        requireOkxA2aBinding(order, paymentIntent, request);

        String callbackKey = okxA2aCallbackKey(request);
        PaymentIntentStatus nextStatus = okxStatus(request.status());
        var providerEvent = paymentProviderEventService.recordOnce(
                "okx",
                "okx_a2a:%s:%s:%s".formatted(request.paymentId(), request.txHash(), request.status().toLowerCase(Locale.ROOT)),
                paymentIntent.id(),
                request.paymentId(),
                request.txHash(),
                request.status(),
                Map.of("amountMinor", request.amountMinor(), "currency", request.currency(), "network", request.network()));
        if (providerEvent.duplicate()) {
            recordAudit("okx_a2a_callback_duplicate", paymentIntent.id(), paymentIntent.accountId(), Map.of("paymentId", request.paymentId(), "txHash", request.txHash()));
            if (paymentIntent.status() == PaymentIntentStatus.CAPTURED) {
                repairCapturedPaymentSideEffects(order, paymentIntent);
                return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
            }
            if (nextStatus != PaymentIntentStatus.CAPTURED) {
                return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
            }
        }
        if (isDuplicateOkxA2aCallback(paymentIntent, request, callbackKey)) {
            recordAudit("okx_a2a_callback_duplicate", paymentIntent.id(), paymentIntent.accountId(), Map.of("paymentId", request.paymentId(), "txHash", request.txHash()));
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
        }

        if (nextStatus != PaymentIntentStatus.CAPTURED) {
            PaymentIntentEntity updated = paymentIntent.withStatus(nextStatus, request.txHash(), Instant.now(), okxA2aCallbackMetadata(paymentIntent, order, request, callbackKey));
            paymentIntentRepository.save(updated);
            recordAudit("okx_a2a_callback_applied", updated.id(), paymentIntent.accountId(), Map.of("paymentId", request.paymentId(), "status", request.status(), "txHash", request.txHash()));
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
        }

        // 中文注释：captured 会解锁交付验收，因此必须同时带 provider 成功状态和链上转账证据。
        requireOkxA2aChainEvidence(request);
        Instant now = Instant.now();
        if (shouldRejectLateCapture(order, paymentIntent)) {
            PaymentIntentEntity rejected = markLateCaptureRejected(paymentIntent, order, request.txHash(), request.status(), now);
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(rejected);
        }
        PaymentIntentEntity updated = paymentIntent.withStatus(PaymentIntentStatus.CAPTURED, request.txHash(), now, okxA2aCallbackMetadata(paymentIntent, order, request, callbackKey));
        paymentIntentRepository.save(updated);
        finalizeCapturedPayment(
                order,
                paymentIntent,
                updated,
                now,
                "payment_captured:" + providerEvent.event().providerEventId(),
                Map.of("provider", "okx", "paymentId", request.paymentId(), "txHash", request.txHash()));
        recordAudit("okx_a2a_callback_captured", updated.id(), paymentIntent.accountId(), Map.of("paymentId", request.paymentId(), "txHash", request.txHash()));
        return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
    }

    private void enforceCallbackRateLimit(String intentId) {
        if (rateLimitService.isAllowed("payment_callback", intentId, 20, Duration.ofMinutes(10))) {
            return;
        }
        riskEventService.record("payment_callback_rate_limited", "payment_intent", intentId, intentId, "high", "Too many payment callback attempts", Map.of("limit", 20, "windowSeconds", 600));
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many payment callback attempts");
    }

    private void finalizeCapturedPayment(
            OrderEntity order,
            PaymentIntentEntity previousPaymentIntent,
            PaymentIntentEntity capturedPaymentIntent,
            Instant capturedAt,
            String settlementIdempotencyKey,
            Map<String, Object> settlementPayload) {
        OrderEntity deadlineOrder = refreshFulfillmentDeadlinesAfterPayment(order, previousPaymentIntent, capturedAt);
        settlementEventService.recordOnce(
                capturedPaymentIntent.orderId(),
                capturedPaymentIntent.id(),
                "payment_captured",
                settlementIdempotencyKey,
                capturedPaymentIntent.amountMinor(),
                capturedPaymentIntent.currency(),
                capturedPaymentIntent.accountId(),
                settlementPayload);
        OrderEntity deliveryOrder = instantFulfillmentService.tryDeliverAfterPayment(deadlineOrder, capturedPaymentIntent);
        publishPaymentCapturedWorkItems(deliveryOrder == null ? deadlineOrder : deliveryOrder);
    }

    private void repairCapturedPaymentSideEffects(OrderEntity order, PaymentIntentEntity capturedPaymentIntent) {
        if (order == null || capturedPaymentIntent == null || capturedPaymentIntent.status() != PaymentIntentStatus.CAPTURED) {
            return;
        }
        OrderEntity deliveryOrder = instantFulfillmentService.tryDeliverAfterPayment(order, capturedPaymentIntent);
        publishPaymentCapturedWorkItems(deliveryOrder == null ? order : deliveryOrder);
    }

    private void publishPaymentCapturedWorkItems(OrderEntity order) {
        if (orderWorkItemPublisher == null) {
            return;
        }
        // 中文注释：支付 capture 是交付待办解锁点，Workbench 直接更新 WorkItem 投影，减少旧 source 同步依赖。
        orderWorkItemPublisher.publishPaymentCaptured(order, Instant.now());
    }

    public PaymentIntentView refundIntent(String intentId, PaymentActionRequest request) {
        return transitionIntent(intentId, request, PaymentIntentStatus.REFUNDED, "payment_refunded");
    }

    public PaymentIntentView cancelIntent(String intentId, PaymentActionRequest request) {
        return transitionIntent(intentId, request, PaymentIntentStatus.CANCELLED, "payment_cancelled");
    }

    public PaymentIntentView disputeIntent(String intentId, PaymentActionRequest request) {
        return transitionIntent(intentId, request, PaymentIntentStatus.DISPUTED, "payment_disputed");
    }

    public PaymentIntentView defaultIntent(String intentId, PaymentActionRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        requirePaymentTransitionActor(paymentIntent, request.actorAccountId(), PaymentIntentStatus.REFUNDED);
        if (!isOkxDirectIntent(paymentIntent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller default only applies to OKX Direct Pay");
        }
        PaymentIntentEntity updated = paymentIntent.withStatus(PaymentIntentStatus.DISPUTED, paymentIntent.providerPaymentRef(), Instant.now(),
                mergeMetadata(paymentIntent, Map.of(
                        "refundStatus", "seller_defaulted",
                        "sellerDefaultedAt", Instant.now().toString(),
                        "reason", request.reason() == null ? "" : request.reason())));
        paymentIntentRepository.save(updated);
        riskEventService.record("okx_direct_pay_seller_defaulted", "payment_intent", updated.id(), request.actorAccountId(), "high", "OKX Direct Pay seller refund defaulted", Map.of("orderId", updated.orderId()));
        recordAudit("payment_refund_defaulted", updated.id(), request.actorAccountId(), Map.of("orderId", updated.orderId(), "reason", request.reason() == null ? "" : request.reason()));
        return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
    }

    public PaymentIntentView refreshIntent(String intentId, PaymentActionRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        if (!"okx".equalsIgnoreCase(paymentIntent.provider())) {
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent);
        }
        OrderEntity order = requireOrder(paymentIntent.orderId());
        String paymentSessionId = stringValue(paymentIntent.metadata().get("txHash"));
        if (paymentSessionId == null) paymentSessionId = paymentIntent.providerPaymentRef();
        OkxOnchainPaySession session = okxOnchainPayClient.getPaymentStatus(paymentSessionId, okxRequest(order, paymentIntent, null, null, true));
        PaymentIntentStatus nextStatus = okxStatus(session.status());
        Instant now = Instant.now();
        if (nextStatus == PaymentIntentStatus.CAPTURED && shouldRejectLateCapture(order, paymentIntent)) {
            PaymentIntentEntity rejected = markLateCaptureRejected(paymentIntent, order, session.paymentId(), session.status(), now);
            return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(rejected);
        }
        PaymentIntentEntity updated = paymentIntent.withStatus(
                nextStatus,
                session.paymentId(),
                now,
                okxMetadata(paymentIntent.metadata(), order, session));
        paymentIntentRepository.save(updated);
        if (nextStatus == PaymentIntentStatus.CAPTURED) {
            if (paymentIntent.status() == PaymentIntentStatus.CAPTURED) {
                repairCapturedPaymentSideEffects(order, updated);
            } else {
                finalizeCapturedPayment(
                        order,
                        paymentIntent,
                        updated,
                        now,
                        "payment_captured:okx_refresh:" + updated.id() + ":" + (updated.providerPaymentRef() == null ? "unknown" : updated.providerPaymentRef()),
                        Map.of("provider", "okx", "paymentId", updated.providerPaymentRef() == null ? "" : updated.providerPaymentRef(), "source", "refresh_intent"));
            }
        }
        recordAudit("okx_payment_refreshed", updated.id(), request.actorAccountId(), Map.of("status", session.status(), "paymentId", session.paymentId()));
        return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
    }

    public PaymentIntentEntity requireSettledMoneyPayment(String orderId) {
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Money settlement requires a payment intent"));
        // 中文注释：交付和验收只认最终 capture，authorized 仍可能撤销或失败，不能解锁履约。
        if (paymentIntent.status() != PaymentIntentStatus.CAPTURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Money settlement requires captured payment");
        }
        if (isOkxDirectIntent(paymentIntent)) {
            requireOkxCaptureReconciliation(paymentIntent);
        }
        return paymentIntent;
    }

    private PaymentIntentView transitionIntent(String intentId, PaymentActionRequest request, PaymentIntentStatus nextStatus, String auditType) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        requirePaymentTransitionActor(paymentIntent, request.actorAccountId(), nextStatus);
        Map<String, Object> metadata = transitionMetadata(paymentIntent, request, nextStatus);
        PaymentIntentEntity updated = paymentIntent.withStatus(nextStatus, paymentIntent.providerPaymentRef(), Instant.now(), metadata);
        paymentIntentRepository.save(updated);
        if (nextStatus == PaymentIntentStatus.REFUNDED) {
            settlementEventService.recordOnce(updated.orderId(), updated.id(), "payment_refunded", "payment_refunded:" + updated.id() + ":" + (request.refundTxHash() == null ? "manual" : request.refundTxHash()), updated.amountMinor(), updated.currency(), request.actorAccountId(), Map.of("reason", request.reason() == null ? "" : request.reason(), "refundTxHash", request.refundTxHash() == null ? "" : request.refundTxHash()));
        }
        recordAudit(auditType, updated.id(), request.actorAccountId(), Map.of(
                "reason", request.reason() == null ? "" : request.reason(),
                "refundTxHash", request.refundTxHash() == null ? "" : request.refundTxHash()));
        return com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(updated);
    }

    private Map<String, Object> transitionMetadata(PaymentIntentEntity paymentIntent, PaymentActionRequest request, PaymentIntentStatus nextStatus) {
        LinkedHashMap<String, Object> extra = new LinkedHashMap<>();
        extra.put("reason", request.reason() == null ? "" : request.reason());
        if (nextStatus == PaymentIntentStatus.REFUNDED && isOkxDirectIntent(paymentIntent)) {
            String refundTxHash = stringValue(request.refundTxHash());
            if (refundTxHash == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX Direct Pay refund requires refundTxHash");
            }
            // 中文注释：Direct Pay 已经把钱打入卖方钱包，平台只在收到链上退款交易后标记 refunded。
            extra.put("refundStatus", "refunded");
            extra.put("refundTxHash", refundTxHash);
            extra.put("refundConfirmedAt", Instant.now().toString());
        }
        return mergeMetadata(paymentIntent, extra);
    }

    private void requirePaymentTransitionActor(PaymentIntentEntity paymentIntent, String actorAccountId, PaymentIntentStatus nextStatus) {
        boolean payer = actorAccountId.equals(paymentIntent.accountId());
        // 中文注释：结算维护权限统一读取 Root Project 职位能力，退款和争议入口共享同一套系统级事实。
        boolean paymentOperator = nextStatus == PaymentIntentStatus.REFUNDED
                ? organizationAuthorityService.hasSystemCapability(actorAccountId, ProjectCapability.PAYMENT_REFUND)
                : organizationAuthorityService.hasSystemCapability(actorAccountId, ProjectCapability.PAYMENT_REVIEW);
        boolean allowed = nextStatus == PaymentIntentStatus.REFUNDED ? paymentOperator : payer || paymentOperator;
        if (!allowed) {
            riskEventService.record("payment_transition_forbidden", "payment_intent", paymentIntent.id(), actorAccountId, "high", "Payment transition actor is not allowed", Map.of("nextStatus", nextStatus.name()));
            throw api(HttpStatus.FORBIDDEN, "payment.transition.forbidden", "Payment transition actor is not allowed", Map.of("paymentIntentId", paymentIntent.id(), "nextStatus", nextStatus.name()));
        }
    }

    private OrderEntity requireOrder(String orderId) {
        // 中文注释：支付创建入口传 orderNo，刷新入口来自 payment_intents.order_id，两个入口都要解析到同一订单。
        return orderRepository.findByOrderNo(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderEntity startDeferredRequestPaymentWindow(OrderEntity order) {
        if (order.postKind() != PostKind.REQUEST
                || order.settlementType() != SettlementType.MONEY
                || order.metadata().get("paymentDueAt") != null) {
            return order;
        }
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(order.metadata());
        metadata.put("paymentDueAt", now.plus(paymentWindowDuration(order)).toString());
        return orderRepository.save(order.withMetadata(metadata, now));
    }

    private Duration paymentWindowDuration(OrderEntity order) {
        Instant lockExpiresAt = instantValue(order.metadata().get("lockExpiresAt"));
        if (lockExpiresAt == null || order.createdAt() == null) {
            return DEFAULT_PAYMENT_WINDOW;
        }
        Duration duration = Duration.between(order.createdAt(), lockExpiresAt);
        return duration.isZero() || duration.isNegative() ? DEFAULT_PAYMENT_WINDOW : duration;
    }

    private boolean isReusable(PaymentIntentEntity paymentIntent) {
        return paymentIntent.status() != PaymentIntentStatus.CANCELLED
                && paymentIntent.status() != PaymentIntentStatus.REFUNDED
                && paymentIntent.status() != PaymentIntentStatus.FAILED;
    }

    private boolean shouldRejectLateCapture(OrderEntity order, PaymentIntentEntity paymentIntent) {
        return order != null
                && order.isFinalStatus()
                && paymentIntent.status() != PaymentIntentStatus.CAPTURED;
    }

    private PaymentIntentEntity markLateCaptureRejected(
            PaymentIntentEntity paymentIntent,
            OrderEntity order,
            String providerPaymentRef,
            String providerStatus,
            Instant now) {
        LinkedHashMap<String, Object> extra = new LinkedHashMap<>();
        extra.put("lateCaptureRejected", true);
        extra.put("lateCaptureRejectedAt", now.toString());
        extra.put("orderStatus", order.status().name().toLowerCase(Locale.ROOT));
        extra.put("providerStatus", providerStatus == null ? "" : providerStatus);
        extra.put("refundStatus", "review_required");
        PaymentIntentEntity rejected = paymentIntent.withStatus(
                PaymentIntentStatus.DISPUTED,
                providerPaymentRef,
                now,
                mergeMetadata(paymentIntent, extra));
        paymentIntentRepository.save(rejected);
        riskEventService.record("payment_late_capture_rejected", "payment_intent", rejected.id(), rejected.accountId(), "high", "Captured payment callback arrived after order final status", Map.of("orderId", order.id(), "orderStatus", order.status().name()));
        recordAudit("payment_late_capture_rejected", rejected.id(), rejected.accountId(), Map.of("orderId", order.id(), "orderStatus", order.status().name()));
        return rejected;
    }

    private OrderEntity refreshFulfillmentDeadlinesAfterPayment(
            OrderEntity order,
            PaymentIntentEntity previousPaymentIntent,
            Instant capturedAt) {
        if (order == null
                || previousPaymentIntent.status() == PaymentIntentStatus.CAPTURED
                || order.settlementType() != SettlementType.MONEY
                || !"reviewed_delivery".equalsIgnoreCase(stringValue(order.metadata().get("fulfillmentMode")))) {
            return order;
        }
        Instant lockExpiresAt = instantValue(order.metadata().get("lockExpiresAt"));
        Instant nextProgressDueAt = instantValue(order.metadata().get("nextProgressDueAt"));
        if (lockExpiresAt == null && nextProgressDueAt == null) {
            return order;
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(order.metadata());
        putShiftedDeadline(metadata, "lockExpiresAt", order.createdAt(), lockExpiresAt, capturedAt);
        putShiftedDeadline(metadata, "nextProgressDueAt", order.createdAt(), nextProgressDueAt, capturedAt);
        metadata.put("fulfillmentDeadlinesStartedAt", capturedAt.toString());
        metadata.put("paymentCapturedAt", capturedAt.toString());
        return orderRepository.save(order.withMetadata(metadata, capturedAt));
    }

    private void putShiftedDeadline(
            Map<String, Object> metadata,
            String key,
            Instant originalStart,
            Instant originalDeadline,
            Instant nextStart) {
        if (originalStart == null || originalDeadline == null) {
            return;
        }
        Duration duration = Duration.between(originalStart, originalDeadline);
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        metadata.put(key, nextStart.plus(duration).toString());
    }

    private int requireAmountMinor(OrderEntity order) {
        BigDecimal amount = order.effectiveSettlementAmount();
        if (amount == null || amount.signum() <= 0) {
            throw api(HttpStatus.CONFLICT, "payment.money_order.amount_required", "Money order requires a positive settlement amount", Map.of("orderNo", order.orderNo()));
        }
        try {
            // 中文注释：订单金额按分换算 provider amountMinor，避免 0.01 这类金额在支付阶段丢精度。
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Money order amount must use at most two decimal places");
        }
    }

    private String checkoutUrl(PaymentIntentEntity paymentIntent) {
        if ("okx".equalsIgnoreCase(paymentIntent.provider())) {
            return stringValue(paymentIntent.metadata().get("paymentUrl"));
        }
        return paymentConfig.getPublicBaseUrl().replaceAll("/+$", "") + "/payments/fake/checkout/" + paymentIntent.id();
    }

    private void requireFakeCallbackEnabled(String intentId) {
        if (paymentConfig.isFakeCallbackEnabled()) {
            return;
        }
        // 中文注释：fake 回调入口默认关闭，开发环境显式开启后才暴露本地支付闭环。
        riskEventService.record("payment_fake_callback_disabled", "payment_intent", intentId, intentId, "high", "Fake payment callback is disabled", Map.of());
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment callback endpoint not found");
    }

    private PaymentIntentEntity startOkxPayment(OrderEntity order, CreatePaymentIntentRequest request, int amountMinor, PaymentIntentEntity existing) {
        Instant now = Instant.now();
        PaymentIntentEntity paymentIntent = resetPaymentIntent(
                existing,
                order.id(),
                request.accountId(),
                "okx",
                amountMinor,
                okxAsset(order),
                "okx-" + UUID.randomUUID(),
                paymentBindingMetadata(order, Map.of("paymentMethod", OKX_PAYMENT_METHOD)),
                now);
        return submitOkxPayment(order, paymentIntent, request);
    }

    private PaymentIntentEntity startOkxA2aPayment(OrderEntity order, CreatePaymentIntentRequest request, int amountMinor, PaymentIntentEntity existing) {
        Instant now = Instant.now();
        String paymentId = okxA2aPaymentId(request);
        Map<String, Object> metadata = paymentBindingMetadata(order, okxA2aMetadata(order, request));
        PaymentIntentEntity paymentIntent = resetPaymentIntent(
                existing,
                order.id(),
                request.accountId(),
                "okx",
                amountMinor,
                okxAsset(order),
                "okx-a2a-" + UUID.randomUUID(),
                metadata,
                now).withStatus(PaymentIntentStatus.PENDING, paymentId, now, metadata);
        paymentIntentRepository.save(paymentIntent);
        recordAudit("okx_a2a_payment_intent_bound", paymentIntent.id(), request.accountId(), Map.of(
                "orderId", order.id(),
                "paymentId", paymentIntent.providerPaymentRef()));
        return paymentIntent;
    }

    private PaymentIntentEntity bindOkxA2aPayment(OrderEntity order, PaymentIntentEntity existing, CreatePaymentIntentRequest request) {
        PaymentIntentEntity updated = existing.withStatus(
                PaymentIntentStatus.PENDING,
                okxA2aPaymentId(request),
                Instant.now(),
                mergeMetadata(existing, okxA2aMetadata(order, request)));
        paymentIntentRepository.save(updated);
        recordAudit("okx_a2a_payment_intent_rebound", updated.id(), request.accountId(), Map.of(
                "orderId", order.id(),
                "paymentId", updated.providerPaymentRef()));
        return updated;
    }

    private PaymentIntentEntity resetPaymentIntent(
            PaymentIntentEntity existing,
            String orderId,
            String accountId,
            String provider,
            int amountMinor,
            String currency,
            String callbackToken,
            Map<String, Object> metadata,
            Instant now) {
        BusinessIds paymentIds = existing == null ? businessIdService.next(BusinessIdType.PAYMENT) : null;
        // 中文注释：payment_intents 以 order_id 保证一单一支付意图，失败后重开沿用原行，避免重复签名提交撞唯一索引。
        return new PaymentIntentEntity(
                existing == null ? paymentIds.id() : existing.id(),
                existing == null ? paymentIds.displayNo() : existing.paymentNo(),
                orderId,
                accountId,
                provider,
                null,
                PaymentIntentStatus.PENDING,
                amountMinor,
                currency,
                callbackToken,
                null,
                null,
                null,
                null,
                null,
                metadata,
                existing == null ? now : existing.createdAt(),
                now);
    }

    private PaymentIntentEntity submitOkxPayment(OrderEntity order, PaymentIntentEntity paymentIntent, CreatePaymentIntentRequest request) {
        String payer = request.payer();
        if (hasPaymentPayload(request)) {
            Map<String, Object> bindingMetadata = x402BindingMetadata(order, paymentIntent, request.paymentPayload());
            paymentIntent = paymentIntent.withStatus(
                    paymentIntent.status(),
                    paymentIntent.providerPaymentRef(),
                    Instant.now(),
                    mergeMetadata(paymentIntent, bindingMetadata));
            payer = stringValue(bindingMetadata.get("payerAddress"));
        }
        // 中文注释：同一个 payment intent 复用同一个 idempotency key，避免用户重复提交钱包签名时生成多条外部会话。
        OkxOnchainPaySession session = okxOnchainPayClient.createPayment(okxRequest(order, paymentIntent, request.paymentPayload(), payer, Boolean.TRUE.equals(request.syncSettle())));
        PaymentIntentStatus nextStatus = okxStatus(session.status());
        Instant now = Instant.now();
        PaymentIntentEntity updated = paymentIntent.withStatus(
                nextStatus,
                session.paymentId(),
                now,
                okxMetadata(paymentIntent.metadata(), order, session));
        paymentIntentRepository.save(updated);
        if (nextStatus == PaymentIntentStatus.CAPTURED) {
            finalizeCapturedPayment(
                    order,
                    paymentIntent,
                    updated,
                    now,
                    "payment_captured:okx_session:" + updated.id() + ":" + (updated.providerPaymentRef() == null ? "unknown" : updated.providerPaymentRef()),
                    Map.of("provider", "okx", "paymentId", updated.providerPaymentRef() == null ? "" : updated.providerPaymentRef(), "source", "submit_okx_payment"));
        }
        recordAudit("okx_payment_session_updated", updated.id(), request.accountId(), Map.of(
                "orderId", order.id(),
                "status", session.status(),
                "paymentId", session.paymentId()));
        return updated;
    }

    private PaymentIntentResponse okxPaymentResponse(PaymentIntentEntity paymentIntent) {
        return new PaymentIntentResponse(com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent), checkoutUrl(paymentIntent));
    }

    private boolean hasPaymentPayload(CreatePaymentIntentRequest request) {
        return request.paymentPayload() != null && !request.paymentPayload().isEmpty();
    }

    private boolean hasOkxA2aPaymentPayload(CreatePaymentIntentRequest request) {
        Map<String, Object> payload = request.paymentPayload();
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        String flow = firstNonBlank(
                stringValue(payload.get("providerFlow")),
                stringValue(payload.get("flow")),
                stringValue(payload.get("type")));
        return "okx_a2a".equalsIgnoreCase(flow) || okxA2aPaymentId(request) != null;
    }

    private Map<String, Object> okxA2aMetadata(OrderEntity order, CreatePaymentIntentRequest request) {
        String paymentId = okxA2aPaymentId(request);
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX A2A paymentPayload.paymentId is required");
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        // 中文注释：A2A 支付意图由外部 payment link 先创建，app 只绑定 paymentId，callback 再完成链上捕获。
        metadata.put("paymentMethod", OKX_PAYMENT_METHOD);
        metadata.put("provider", "okx");
        metadata.put("providerFlow", "okx_a2a");
        metadata.put("paymentId", paymentId);
        metadata.put("okxStatus", "pending");
        metadata.put("asset", okxAsset(order));
        metadata.put("network", okxNetwork(order));
        metadata.put("recipient", okxPaymentRecipient(order));
        metadata.put("recipientAddress", okxPaymentRecipient(order));
        metadata.put("settlementOrderId", order.id());
        metadata.put("challengeNonce", order.challengeNonce());
        putIfPresent(metadata, "paymentUrl", stringValue(request.paymentPayload().get("paymentUrl")));
        putIfPresent(metadata, "payer", request.payer());
        putIfPresent(metadata, "payerAddress", request.payer());
        return Map.copyOf(metadata);
    }

    private String okxA2aPaymentId(CreatePaymentIntentRequest request) {
        Map<String, Object> payload = request.paymentPayload();
        if (payload == null) {
            return null;
        }
        String paymentId = firstNonBlank(
                stringValue(payload.get("paymentId")),
                stringValue(payload.get("payment_id")),
                stringValue(payload.get("id")));
        if (paymentId == null) {
            return null;
        }
        if (!paymentId.startsWith("a2a_")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX A2A paymentId must start with a2a_");
        }
        return paymentId;
    }

    private boolean hasReconciliation(CreatePaymentIntentRequest request) {
        return request.reconciliation() != null && !request.reconciliation().isEmpty();
    }

    private Map<String, Object> x402BindingMetadata(OrderEntity order, PaymentIntentEntity paymentIntent, Map<String, Object> paymentPayload) {
        if (!paymentPayload.containsKey(X402_VERSION)
                || !(paymentPayload.get(X402_ACCEPTED) instanceof Map<?, ?> accepted)
                || !(paymentPayload.get(X402_PAYLOAD) instanceof Map<?, ?> payload)
                || !(payload.get(X402_AUTHORIZATION) instanceof Map<?, ?> authorization)
                || stringValue(payload.get(X402_SIGNATURE)) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "x402 paymentPayload requires x402Version, accepted, payload.authorization and payload.signature");
        }
        requirePayloadText(accepted, "scheme", "x402 paymentPayload.accepted.scheme is required");
        requirePayloadText(accepted, "network", "x402 paymentPayload.accepted.network is required");
        requirePayloadText(accepted, "asset", "x402 paymentPayload.accepted.asset is required");
        requirePayloadText(accepted, "amount", "x402 paymentPayload.accepted.amount is required");
        requirePayloadText(accepted, "payTo", "x402 paymentPayload.accepted.payTo is required");
        requirePayloadText(authorization, "from", "x402 authorization.from is required");
        requirePayloadText(authorization, "to", "x402 authorization.to is required");
        requirePayloadText(authorization, "value", "x402 authorization.value is required");
        requirePayloadText(authorization, "validAfter", "x402 authorization.validAfter is required");
        requirePayloadText(authorization, "validBefore", "x402 authorization.validBefore is required");
        requirePayloadText(authorization, "nonce", "x402 authorization.nonce is required");

        String payerAddress = requirePayloadTextValue(authorization, "from", "x402 authorization.from is required");
        String authorizationTo = requirePayloadTextValue(authorization, "to", "x402 authorization.to is required");
        String authorizationValue = requirePayloadTextValue(authorization, "value", "x402 authorization.value is required");
        String acceptedPayTo = requirePayloadTextValue(accepted, "payTo", "x402 paymentPayload.accepted.payTo is required");
        String acceptedAmount = requirePayloadTextValue(accepted, "amount", "x402 paymentPayload.accepted.amount is required");
        String acceptedNetwork = requirePayloadTextValue(accepted, "network", "x402 paymentPayload.accepted.network is required");
        String acceptedAsset = requirePayloadTextValue(accepted, "asset", "x402 paymentPayload.accepted.asset is required");
        String recipientAddress = okxPaymentRecipient(order);
        Map<String, Object> expectedRequirements = okxOnchainPayClient.buildPaymentRequirements(
                paymentIntent.amountMinor(),
                okxAsset(order),
                recipientAddress);

        // 中文注释：钱包签名里的 from/to/payTo 是订单支付归属证据，错配时立即拒绝，避免一笔链上支付串到另一张订单。
        requireExactMatch(authorizationTo, recipientAddress, "x402 authorization.to must match order recipient");
        requireExactMatch(acceptedPayTo, recipientAddress, "x402 accepted.payTo must match order recipient");
        requireExactMatch(acceptedNetwork, okxNetwork(order), "x402 accepted.network must match order network");
        requireExactMatch(acceptedAmount, authorizationValue, "x402 accepted.amount must match authorization value");
        // 中文注释：提前对齐 accepted 与服务端 paymentRequirements，避免把必然 30001 的错配请求发到 OKX 网关。
        requireExactMatch(acceptedAmount, stringValue(expectedRequirements.get("amount")), "x402 accepted.amount must match payment requirements");
        requireExactMatch(acceptedAsset, stringValue(expectedRequirements.get("asset")), "x402 accepted.asset must match payment requirements");

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("payerAddress", payerAddress);
        metadata.put("recipientAddress", recipientAddress);
        metadata.put("x402Amount", acceptedAmount);
        metadata.put("network", acceptedNetwork);
        metadata.put("asset", acceptedAsset);
        metadata.put("paymentEvidenceStatus", "wallet_signature_bound");
        metadata.put("paymentIntentId", paymentIntent.id());
        return Map.copyOf(metadata);
    }

    private String requirePayloadTextValue(Map<?, ?> payload, String key, String message) {
        if (!(payload.get(key) instanceof String text) || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return text.trim();
    }

    private void requirePayloadText(Map<?, ?> payload, String key, String message) {
        requirePayloadTextValue(payload, key, message);
    }

    private OkxOnchainPayCreateRequest okxRequest(
            OrderEntity order,
            PaymentIntentEntity paymentIntent,
            Map<String, Object> paymentPayload,
            String payer,
            boolean syncSettle) {
        return new OkxOnchainPayCreateRequest(
                order.id(),
                paymentIntent.id(),
                paymentIntent.amountMinor(),
                okxAsset(order),
                okxNetwork(order),
                okxPaymentRecipient(order),
                payer == null || payer.isBlank() ? firstNonBlank(stringValue(paymentIntent.metadata().get("payerAddress")), stringValue(paymentIntent.metadata().get("payer"))) : payer.trim(),
                paymentPayload,
                syncSettle,
                Map.of("paymentIntentId", paymentIntent.id(), "orderId", order.id()));
    }

    private Map<String, Object> okxMetadata(Map<String, Object> current, OrderEntity order, OkxOnchainPaySession session) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(current == null ? Map.of() : current);
        metadata.put("paymentMethod", OKX_PAYMENT_METHOD);
        metadata.put("provider", "okx");
        metadata.put("okxStatus", session.status());
        metadata.put("paymentRequirements", session.paymentRequirements());
        metadata.put("rawStatus", session.raw());
        metadata.put("asset", session.asset());
        metadata.put("network", session.network());
        metadata.put("recipient", session.recipient());
        metadata.put("recipientAddress", session.recipient());
        metadata.put("settlementOrderId", order.id());
        metadata.put("challengeNonce", order.challengeNonce());
        putIfPresent(metadata, "paymentId", session.paymentId());
        putIfPresent(metadata, "paymentUrl", session.paymentUrl());
        putIfPresent(metadata, "payer", session.payer());
        putIfPresent(metadata, "payerAddress", session.payer());
        putIfPresent(metadata, "txHash", session.txHash());
        putIfPresent(metadata, "settlementId", session.settlementId());
        normalizeProviderOkxReconciliation(session.raw()).ifPresent(reconciliation -> {
            metadata.put("okxProviderReconciliation", reconciliation);
            if (okxStatus(session.status()) == PaymentIntentStatus.CAPTURED && providerReconciliationSettled(reconciliation)) {
                metadata.put("okxReconciliation", reconciliation);
            }
        });
        return Map.copyOf(metadata);
    }

    private PaymentIntentEntity applyOptionalOkxReconciliation(
            OrderEntity order,
            PaymentIntentEntity paymentIntent,
            CreatePaymentIntentRequest request,
            String actorAccountId) {
        if (!hasReconciliation(request)) {
            return paymentIntent;
        }
        return applyOkxReconciliation(order, paymentIntent, request.reconciliation(), actorAccountId);
    }

    private PaymentIntentEntity applyOkxReconciliation(
            OrderEntity order,
            PaymentIntentEntity paymentIntent,
            Map<String, Object> reconciliation,
            String actorAccountId) {
        if (!isOkxDirectIntent(paymentIntent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX reconciliation only applies to OKX Direct Pay");
        }
        Map<String, Object> normalized = normalizeRequiredOkxReconciliation(order, paymentIntent, reconciliation);
        PaymentIntentEntity updated = paymentIntent.withStatus(
                paymentIntent.status(),
                stringValue(normalized.get("txHash")),
                Instant.now(),
                mergeMetadata(paymentIntent, Map.of("okxReconciliation", normalized)));
        paymentIntentRepository.save(updated);
        recordAudit("okx_payment_reconciliation_attached", updated.id(), actorAccountId, Map.of(
                "orderId", order.id(),
                "txHash", normalized.get("txHash"),
                "evidencePath", normalized.getOrDefault("evidencePath", "")));
        return updated;
    }

    private java.util.Optional<Map<String, Object>> normalizeProviderOkxReconciliation(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return java.util.Optional.empty();
        }
        Map<String, Object> verify = mapValue(raw.get("verify"));
        Map<String, Object> settlement = mapValue(raw.get("settlement"));
        Map<String, Object> status = mapValue(raw.get("status"));
        if (verify.isEmpty() && settlement.isEmpty() && status.isEmpty()) {
            return java.util.Optional.empty();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("verifyValid", booleanValue(firstValue(verify, List.of("isValid", "valid", "success"))));
        normalized.put("settleSuccess", booleanValue(firstValue(settlement, List.of("success", "isValid", "valid"))));
        normalized.put("settleStatus", normalizedStatus(firstString(status, List.of("status", "state", "settleStatus"))));
        normalized.put("chainReceiptStatus", normalizedStatus(firstString(status, List.of("status", "state", "settleStatus"))));
        normalized.put("transferLogCount", "success".equals(normalized.get("chainReceiptStatus")) ? 1 : 0);
        putIfPresent(normalized, "txHash", firstString(raw, List.of("txHash", "transaction", "transactionHash")));
        if (!normalized.containsKey("txHash")) {
            putIfPresent(normalized, "txHash", firstString(settlement, List.of("transaction", "txHash", "transactionHash")));
        }
        if (!normalized.containsKey("txHash")) {
            putIfPresent(normalized, "txHash", firstString(status, List.of("transaction", "txHash", "transactionHash")));
        }
        return java.util.Optional.of(Map.copyOf(normalized));
    }

    private boolean providerReconciliationSettled(Map<String, Object> reconciliation) {
        return booleanValue(reconciliation.get("verifyValid"))
                && booleanValue(reconciliation.get("settleSuccess"))
                && "success".equals(normalizedStatus(stringValue(reconciliation.get("settleStatus"))))
                && "success".equals(normalizedStatus(stringValue(reconciliation.get("chainReceiptStatus"))))
                && longValue(reconciliation.get("transferLogCount")) >= 1
                && stringValue(reconciliation.get("txHash")) != null;
    }

    private Map<String, Object> normalizeRequiredOkxReconciliation(
            OrderEntity order,
            PaymentIntentEntity paymentIntent,
            Map<String, Object> reconciliation) {
        Map<String, Object> verify = mapValue(reconciliation.get("verify"));
        Map<String, Object> settle = mapValue(reconciliation.get("settle"));
        Map<String, Object> status = mapValue(reconciliation.get("status"));
        Map<String, Object> chain = mapValue(reconciliation.get("chain"));
        String txHash = firstNonBlank(
                firstString(chain, List.of("txHash", "transaction", "transactionHash")),
                firstString(status, List.of("transaction", "txHash", "transactionHash")),
                firstString(settle, List.of("transaction", "txHash", "transactionHash")),
                stringValue(reconciliation.get("txHash")));

        // 中文注释：真实 OKX capture 需要链上对账事实，验收阶段读取同一份归一化证据。
        requireTrue(booleanValue(firstValue(verify, List.of("isValid", "valid", "success"))), "OKX reconciliation requires verify.isValid=true");
        requireTrue(booleanValue(firstValue(settle, List.of("success", "isValid", "valid"))), "OKX reconciliation requires settle.success=true");
        requireTrue("success".equals(normalizedStatus(firstString(status, List.of("status", "state", "settleStatus")))), "OKX reconciliation requires settle/status success");
        requireTrue("success".equals(normalizedStatus(firstString(chain, List.of("receiptStatus", "status")))), "OKX reconciliation requires chain receipt success");
        requireTrue(longValue(chain.get("transferLogCount")) >= 1, "OKX reconciliation requires a matching transfer log");
        requireTrue(txHash != null, "OKX reconciliation requires txHash");
        requireOptionalMatch(reconciliation.get("orderId"), order.id(), "OKX reconciliation orderId mismatch");
        requireOptionalMatch(reconciliation.get("orderNo"), order.orderNo(), "OKX reconciliation orderNo mismatch");
        requireOptionalIntMatch(reconciliation.get("amountMinor"), paymentIntent.amountMinor(), "OKX reconciliation amount mismatch");
        requireOptionalMatch(reconciliation.get("network"), OKX_DIRECT_PAY_NETWORK, "OKX reconciliation network mismatch");
        requireOptionalMatch(reconciliation.get("recipient"), okxPaymentRecipient(order), "OKX reconciliation recipient mismatch");

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("verifyValid", true);
        normalized.put("settleSuccess", true);
        normalized.put("settleStatus", "success");
        normalized.put("chainReceiptStatus", "success");
        normalized.put("transferLogCount", longValue(chain.get("transferLogCount")));
        normalized.put("txHash", txHash);
        normalized.put("network", firstNonBlank(stringValue(reconciliation.get("network")), OKX_DIRECT_PAY_NETWORK));
        normalized.put("recipient", firstNonBlank(stringValue(reconciliation.get("recipient")), okxPaymentRecipient(order)));
        putIfPresent(normalized, "runId", reconciliation.get("runId"));
        putIfPresent(normalized, "evidencePath", reconciliation.get("evidencePath"));
        putIfPresent(normalized, "payer", reconciliation.get("payer"));
        putIfPresent(normalized, "payerBefore", chain.get("payerBefore"));
        putIfPresent(normalized, "payerAfter", chain.get("payerAfter"));
        putIfPresent(normalized, "recipientBefore", chain.get("recipientBefore"));
        putIfPresent(normalized, "recipientAfter", chain.get("recipientAfter"));
        return Map.copyOf(normalized);
    }

    private void requireOkxCaptureReconciliation(PaymentIntentEntity paymentIntent) {
        Map<String, Object> reconciliation = mapValue(paymentIntent.metadata().get("okxReconciliation"));
        if (reconciliation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX money settlement requires reconciliation evidence");
        }
        requireTrue(booleanValue(reconciliation.get("verifyValid")), "OKX reconciliation verify evidence required");
        requireTrue(booleanValue(reconciliation.get("settleSuccess")), "OKX reconciliation settle evidence required");
        requireTrue("success".equals(normalizedStatus(stringValue(reconciliation.get("settleStatus")))), "OKX reconciliation settle/status success required");
        requireTrue("success".equals(normalizedStatus(stringValue(reconciliation.get("chainReceiptStatus")))), "OKX reconciliation chain receipt success required");
        requireTrue(longValue(reconciliation.get("transferLogCount")) >= 1, "OKX reconciliation transfer log required");
        requireTrue(stringValue(reconciliation.get("txHash")) != null, "OKX reconciliation txHash required");
    }

    private void requireOkxA2aBinding(OrderEntity order, PaymentIntentEntity paymentIntent, OkxA2aCallbackRequest request) {
        if (!isOkxDirectIntent(paymentIntent)) {
            riskEventService.record("okx_a2a_callback_provider_mismatch", "payment_intent", paymentIntent.id(), paymentIntent.accountId(), "high", "OKX A2A callback requires OKX payment intent", Map.of("provider", paymentIntent.provider()));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX A2A callback requires OKX payment intent");
        }
        requireOptionalMatch(request.orderNo(), order.orderNo(), "OKX A2A callback orderNo mismatch");
        requireOptionalIntMatch(request.amountMinor(), paymentIntent.amountMinor(), "OKX A2A callback amount mismatch");
        requireOptionalMatch(request.currency(), paymentIntent.currency(), "OKX A2A callback currency mismatch");
        requireOptionalMatch(request.recipient(), okxPaymentRecipient(order), "OKX A2A callback recipient mismatch");
        requireOptionalMatch(request.network(), OKX_DIRECT_PAY_NETWORK, "OKX A2A callback network mismatch");
        requireBoundPayerMatch(request.payer(), stringValue(paymentIntent.metadata().get("payerAddress")));
        String expectedPaymentId = stringValue(paymentIntent.metadata().get("paymentId"));
        if (expectedPaymentId != null && !expectedPaymentId.equalsIgnoreCase(request.paymentId())) {
            riskEventService.record("okx_a2a_callback_payment_id_mismatch", "payment_intent", paymentIntent.id(), paymentIntent.accountId(), "high", "OKX A2A callback paymentId mismatch", Map.of("expected", expectedPaymentId, "actual", request.paymentId()));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX A2A callback paymentId mismatch");
        }
    }

    private void requireOkxA2aChainEvidence(OkxA2aCallbackRequest request) {
        requireTrue("success".equals(normalizedStatus(request.chainReceiptStatus())), "OKX A2A callback requires chain receipt success");
        requireTrue(request.transferLogCount() != null && request.transferLogCount() >= 1, "OKX A2A callback requires a matching transfer log");
    }

    private boolean isDuplicateOkxA2aCallback(PaymentIntentEntity paymentIntent, OkxA2aCallbackRequest request, String callbackKey) {
        Map<String, Object> metadata = paymentIntent.metadata();
        String previousEventId = stringValue(metadata.get("okxA2aCallbackEventId"));
        String currentEventId = stringValue(request.callbackEventId());
        if (previousEventId != null && currentEventId != null && previousEventId.equals(currentEventId)) {
            return true;
        }
        String previousKey = stringValue(metadata.get("okxA2aCallbackKey"));
        if (previousKey != null && previousKey.equals(callbackKey)) {
            return true;
        }
        return paymentIntent.status() == PaymentIntentStatus.CAPTURED
                && request.paymentId().equalsIgnoreCase(stringValue(metadata.get("paymentId")))
                && request.txHash().equalsIgnoreCase(stringValue(metadata.get("txHash")));
    }

    private Map<String, Object> okxA2aCallbackMetadata(
            PaymentIntentEntity paymentIntent,
            OrderEntity order,
            OkxA2aCallbackRequest request,
            String callbackKey) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(paymentIntent.metadata());
        metadata.put("paymentMethod", OKX_PAYMENT_METHOD);
        metadata.put("provider", "okx");
        metadata.put("paymentId", request.paymentId());
        metadata.put("okxStatus", request.status());
        metadata.put("txHash", request.txHash());
        metadata.put("recipient", request.recipient());
        metadata.put("recipientAddress", request.recipient());
        metadata.put("settlementOrderId", order.id());
        metadata.put("okxA2aCallbackKey", callbackKey);
        metadata.put("okxA2aCallbackAppliedAt", Instant.now().toString());
        putIfPresent(metadata, "payer", request.payer());
        putIfPresent(metadata, "payerAddress", request.payer());
        putIfPresent(metadata, "okxA2aCallbackEventId", request.callbackEventId());
        putIfPresent(metadata, "okxA2aEvidencePath", request.evidencePath());
        if (okxStatus(request.status()) == PaymentIntentStatus.CAPTURED) {
            metadata.put("okxReconciliation", Map.of(
                    "verifyValid", true,
                    "settleSuccess", true,
                    "settleStatus", "success",
                    "chainReceiptStatus", normalizedStatus(request.chainReceiptStatus()),
                    "transferLogCount", request.transferLogCount(),
                    "txHash", request.txHash(),
                    "network", request.network() == null || request.network().isBlank() ? OKX_DIRECT_PAY_NETWORK : request.network().trim(),
                    "recipient", request.recipient(),
                    "payer", request.payer() == null ? "" : request.payer(),
                    "paymentId", request.paymentId()));
        }
        return Map.copyOf(metadata);
    }

    private void verifyOkxA2aCallbackSignature(OkxA2aCallbackRequest request, String signatureHeader, String timestampHeader) {
        Instant timestamp = parseOkxCallbackTimestamp(timestampHeader, request);
        if (Duration.between(timestamp, Instant.now()).abs().compareTo(OKX_CALLBACK_TIMESTAMP_WINDOW) > 0) {
            riskEventService.record("okx_a2a_callback_timestamp_stale", "payment_intent", request.intentId(), request.paymentId(), "high", "OKX A2A callback timestamp is stale", Map.of("timestamp", timestampHeader));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OKX A2A callback timestamp is stale");
        }
        String expected = signOkxA2aCallback(request, timestampHeader);
        if (signatureHeader == null || !signatureHeader.equals(expected)) {
            riskEventService.record("okx_a2a_callback_signature_invalid", "payment_intent", request.intentId(), request.paymentId(), "high", "OKX A2A callback signature invalid", Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OKX A2A callback signature invalid");
        }
    }

    private Instant parseOkxCallbackTimestamp(String timestampHeader, OkxA2aCallbackRequest request) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            riskEventService.record("okx_a2a_callback_timestamp_missing", "payment_intent", request.intentId(), request.paymentId(), "high", "OKX A2A callback timestamp missing", Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OKX A2A callback timestamp missing");
        }
        try {
            return Instant.parse(timestampHeader.trim());
        } catch (Exception exception) {
            riskEventService.record("okx_a2a_callback_timestamp_invalid", "payment_intent", request.intentId(), request.paymentId(), "high", "OKX A2A callback timestamp invalid", Map.of("timestamp", timestampHeader));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OKX A2A callback timestamp invalid");
        }
    }

    private String signOkxA2aCallback(OkxA2aCallbackRequest request, String timestampHeader) {
        return hmacHex(okxCallbackSecret(), okxA2aSignaturePayload(request, timestampHeader));
    }

    private String okxA2aSignaturePayload(OkxA2aCallbackRequest request, String timestampHeader) {
        return "%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s".formatted(
                timestampHeader == null ? "" : timestampHeader.trim(),
                request.callbackEventId() == null ? "" : request.callbackEventId().trim(),
                request.paymentId(),
                request.intentId(),
                request.orderNo(),
                request.status().toLowerCase(Locale.ROOT),
                request.amountMinor(),
                request.currency(),
                request.recipient(),
                request.txHash(),
                request.network() == null ? "" : request.network().trim());
    }

    private String okxA2aCallbackKey(OkxA2aCallbackRequest request) {
        return "%s:%s:%s".formatted(request.paymentId(), request.txHash(), request.status().toLowerCase(Locale.ROOT));
    }

    private String okxCallbackSecret() {
        String secret = stringValue(paymentConfig.getOkx().getApiSecret());
        return secret == null ? paymentConfig.getCallbackSecret() : secret;
    }

    private PaymentIntentStatus okxStatus(String status) {
        String value = status == null ? "" : status.toLowerCase();
        return switch (value) {
            case "completed", "funded" -> PaymentIntentStatus.CAPTURED;
            case "cancelled", "canceled" -> PaymentIntentStatus.CANCELLED;
            case "failed", "expired", "rejected" -> PaymentIntentStatus.FAILED;
            default -> PaymentIntentStatus.PENDING;
        };
    }

    private boolean isOkxOrder(OrderEntity order) {
        return OKX_PAYMENT_METHOD.equalsIgnoreCase(stringValue(order.settlementSnapshot().get("paymentMethod")))
                || OKX_PAYMENT_METHOD.equalsIgnoreCase(stringValue(order.metadata().get("paymentMethod")));
    }

    private boolean useFakeCallbackForOkxOrder() {
        // 中文注释：本地黑盒闭环需要在缺少 OKX 凭证时完成支付捕获，生产环境由安全守卫关闭 fake callback。
        return paymentConfig.isFakeCallbackEnabled() && "fake".equalsIgnoreCase(paymentConfig.getProvider());
    }

    private boolean isOkxDirectIntent(PaymentIntentEntity paymentIntent) {
        return "okx".equalsIgnoreCase(paymentIntent.provider())
                && OKX_PAYMENT_METHOD.equalsIgnoreCase(stringValue(paymentIntent.metadata().get("paymentMethod")));
    }

    private String okxPaymentRecipient(OrderEntity order) {
        String recipient = stringValue(order.settlementSnapshot().get("paymentRecipient"));
        if (recipient == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX Direct Pay requires seller paymentRecipient");
        }
        return recipient;
    }

    private String okxAsset(OrderEntity order) {
        String asset = stringValue(order.settlementSnapshot().get("paymentAsset"));
        if (asset == null) asset = stringValue(order.settlementSnapshot().get("currency"));
        if (asset == null || asset.equalsIgnoreCase("USD")) asset = paymentConfig.getOkx().getDefaultAsset();
        return asset == null || asset.isBlank() ? "USDC" : asset.trim();
    }

    private String okxNetwork(OrderEntity order) {
        String network = stringValue(order.settlementSnapshot().get("paymentNetwork"));
        if (network != null && !OKX_DIRECT_PAY_NETWORK.equals(network)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX Direct Pay only supports X Layer eip155:196");
        }
        // 中文注释：OKX Direct Pay 的 x402 PaymentRequirements 固定使用 X Layer ChainIndex 196。
        return OKX_DIRECT_PAY_NETWORK;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private Instant instantValue(Object value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                result.put(key, entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private Object firstValue(Map<String, Object> values, List<String> keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> values, List<String> keys) {
        for (String key : keys) {
            String text = stringValue(values.get(key));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("true") || normalized.equals("success") || normalized.equals("passed");
        }
        return false;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return 0;
    }

    private String normalizedStatus(String status) {
        if (status == null) {
            return "";
        }
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (value.equals("completed") || value.equals("settled")) {
            return "success";
        }
        return value;
    }

    private void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void requireOptionalMatch(Object actual, String expected, String message) {
        String text = stringValue(actual);
        if (text != null && expected != null && !text.equalsIgnoreCase(expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void requireBoundPayerMatch(String actual, String expected) {
        if (expected == null) {
            return;
        }
        String payer = stringValue(actual);
        if (payer == null || !payer.equalsIgnoreCase(expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX A2A callback payer mismatch");
        }
    }

    private void requireExactMatch(String actual, String expected, String message) {
        if (actual == null || expected == null || !actual.equalsIgnoreCase(expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void requireOptionalIntMatch(Object actual, int expected, String message) {
        if (actual == null) {
            return;
        }
        if (longValue(actual) != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void verifySignature(PaymentCallbackRequest request, String signatureHeader) {
        String expected = signCallback(request);
        if (signatureHeader == null || !signatureHeader.equals(expected)) {
            riskEventService.record("payment_callback_signature_invalid", "payment_intent", request.intentId(), request.callbackToken(), "high", "Payment callback signature invalid", Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Payment callback signature invalid");
        }
    }

    private String signCallback(PaymentCallbackRequest request) {
        String payload = "%s:%s:%s:%s:%s".formatted(
                request.intentId(),
                request.callbackToken(),
                request.providerPaymentRef(),
                request.status().toLowerCase(),
                request.amountMinor());
        return hmacHex(paymentConfig.getCallbackSecret(), payload);
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(signature.length * 2);
            for (byte value : signature) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign payment callback", exception);
        }
    }

    private PaymentIntentStatus parseProviderStatus(String status) {
        return switch (status.toLowerCase()) {
            case "authorized" -> PaymentIntentStatus.AUTHORIZED;
            case "captured", "paid" -> PaymentIntentStatus.CAPTURED;
            case "refunded" -> PaymentIntentStatus.REFUNDED;
            case "cancelled" -> PaymentIntentStatus.CANCELLED;
            case "disputed" -> PaymentIntentStatus.DISPUTED;
            case "failed" -> PaymentIntentStatus.FAILED;
            default ->
                    throw api(HttpStatus.BAD_REQUEST, "payment.callback.status.unsupported", "Unsupported payment callback status", Map.of("status", status));
        };
    }

    private ApiStatusException api(HttpStatus status, String code, String message, Map<String, Object> context) {
        return new ApiStatusException(status, code, message, context);
    }

    private Map<String, Object> mergeMetadata(PaymentIntentEntity paymentIntent, Map<String, Object> extra) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(paymentIntent.metadata());
        merged.putAll(extra);
        return Map.copyOf(merged);
    }

    public void refundOrderPaymentIfPresent(OrderEntity order, String actorAccountId, String reason) {
        paymentIntentRepository.findByOrderId(order.id()).ifPresent(paymentIntent -> {
            if (paymentIntent.status() == PaymentIntentStatus.REFUNDED || paymentIntent.status() == PaymentIntentStatus.CANCELLED) {
                return;
            }
            if (isOkxDirectIntent(paymentIntent) && paymentIntent.status() != PaymentIntentStatus.PENDING) {
                PaymentIntentEntity updated = paymentIntent.withStatus(PaymentIntentStatus.DISPUTED, paymentIntent.providerPaymentRef(), Instant.now(),
                        mergeMetadata(paymentIntent, Map.of(
                                "refundStatus", "refund_required",
                                "refundRequiredAt", Instant.now().toString(),
                                "orderCloseReason", reason == null ? "" : reason)));
                paymentIntentRepository.save(updated);
                recordAudit("payment_refund_required", updated.id(), actorAccountId, Map.of("orderId", order.id(), "reason", reason == null ? "" : reason));
                return;
            }
            PaymentIntentStatus nextStatus = paymentIntent.status() == PaymentIntentStatus.PENDING
                    ? PaymentIntentStatus.CANCELLED
                    : PaymentIntentStatus.REFUNDED;
            PaymentIntentEntity updated = paymentIntent.withStatus(nextStatus, paymentIntent.providerPaymentRef(), Instant.now(),
                    mergeMetadata(paymentIntent, Map.of("orderCloseReason", reason == null ? "" : reason)));
            paymentIntentRepository.save(updated);
            if (nextStatus == PaymentIntentStatus.REFUNDED) {
                settlementEventService.recordOnce(order.id(), updated.id(), "payment_refunded", "payment_refunded:" + updated.id() + ":order_close", updated.amountMinor(), updated.currency(), actorAccountId, Map.of("reason", reason == null ? "" : reason));
            }
            recordAudit(nextStatus == PaymentIntentStatus.REFUNDED ? "payment_refunded" : "payment_cancelled",
                    updated.id(), actorAccountId, Map.of("orderId", order.id(), "reason", reason == null ? "" : reason));
        });
    }

    public boolean hasCapturedPayment(String orderId) {
        return paymentIntentRepository.findByOrderId(orderId)
                .map(paymentIntent -> paymentIntent.status() == PaymentIntentStatus.CAPTURED)
                .orElse(false);
    }

    private Map<String, Object> paymentBindingMetadata(OrderEntity order) {
        return paymentBindingMetadata(order, Map.of());
    }

    private Map<String, Object> paymentBindingMetadata(OrderEntity order, Map<String, Object> extra) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> orderMetadata = order.metadata() == null ? Map.of() : order.metadata();
        metadata.put("orderStatus", order.status().name());
        metadata.put("orderId", order.id());
        metadata.put("orderNo", order.orderNo());
        metadata.put("payerAccountId", order.buyerAccountId());
        metadata.put("payeeAccountId", order.sellerAccountId());
        metadata.put("fulfillerAccountId", order.fulfillerAccountId());
        String postKind = order.postKind() == null ? "" : order.postKind().name().toLowerCase(Locale.ROOT);
        metadata.put("postKind", postKind);
        metadata.put("postId", metadataString(order.postId(), ""));
        metadata.put("itemId", metadataString(orderMetadata.get("itemId"), ""));
        metadata.put("challengeNonce", order.challengeNonce());
        metadata.put("settlementAmount", order.effectiveSettlementAmount() == null ? 0 : order.effectiveSettlementAmount());
        // 中文注释：payment metadata 会被 Map.copyOf 固化，所有可选字段先归一化，避免空值破坏支付创建链路。
        String currency = metadataString(order.settlementSnapshot().get("currency"), paymentConfig.getCurrency());
        metadata.put("currency", currency);
        metadata.put("paymentAsset", metadataString(order.settlementSnapshot().get("paymentAsset"), currency));
        metadata.put("paymentNetwork", metadataString(order.settlementSnapshot().get("paymentNetwork"), ""));
        metadata.put("receiver", metadataString(order.settlementSnapshot().get("paymentRecipient"), ""));
        metadata.put("recipientAddress", metadataString(order.settlementSnapshot().get("paymentRecipient"), ""));
        metadata.put("paymentEvidenceStatus", "order_bound");
        Object paymentDueAt = orderMetadata.get("paymentDueAt");
        if (paymentDueAt != null) {
            metadata.put("paymentDueAt", paymentDueAt);
        }
        metadata.putAll(extra);
        return Map.copyOf(metadata);
    }

    private String metadataString(Object value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? (fallback == null ? "" : fallback) : text;
    }

    private void recordAudit(String type, String subjectId, String actorAccountId, Map<String, Object> payload) {
        auditEventRecorder.record(new AuditEvent(
                "audit-" + UUID.randomUUID(),
                type,
                "payment_intent",
                subjectId,
                actorAccountId,
                traceContextHolder.currentTraceId().orElse("trace-payment"),
                "success",
                payload,
                Instant.now()));
    }
}
