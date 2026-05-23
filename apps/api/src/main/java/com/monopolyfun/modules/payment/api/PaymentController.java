package com.monopolyfun.modules.payment.api;

import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.command.PaymentService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.api.request.CreatePaymentIntentRequest;
import com.monopolyfun.modules.payment.api.request.OkxA2aCallbackRequest;
import com.monopolyfun.modules.payment.api.request.PaymentActionRequest;
import com.monopolyfun.modules.payment.api.request.PaymentCallbackRequest;
import com.monopolyfun.modules.payment.api.response.PaymentIntentResponse;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderRepository orderRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;

    public PaymentController(
            PaymentService paymentService,
            PaymentIntentRepository paymentIntentRepository,
            OrderRepository orderRepository,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory) {
        this.paymentService = paymentService;
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
    }

    @GetMapping("/intents/{intentId}")
    public PaymentIntentView getIntent(
            @PathVariable String intentId,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        PaymentIntentEntity intent = paymentIntentRepository.findById(intentId)
                .or(() -> paymentIntentRepository.findByPaymentNo(intentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        PaymentIntentView view = com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(intent);
        requireReadable(view, accountId);
        return paymentIntentView(view, accountId, includeAgent);
    }

    @PostMapping("/orders/{orderNo}/intent")
    public PaymentIntentResponse createIntent(
            @PathVariable String orderNo,
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        PaymentIntentResponse response = paymentService.createIntent(orderNo, request);
        return new PaymentIntentResponse(
                paymentIntentView(response.paymentIntent(), request.accountId(), includeAgent),
                response.checkoutUrl());
    }

    @PostMapping("/callback/fake")
    @Hidden
    public PaymentIntentView fakeCallback(
            @Valid @RequestBody PaymentCallbackRequest request,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signatureHeader) {
        return paymentService.handleCallback(request, signatureHeader);
    }

    @PostMapping("/callback/okx/a2a")
    @Hidden
    public PaymentIntentView okxA2aCallback(
            @Valid @RequestBody OkxA2aCallbackRequest request,
            @RequestHeader(value = "OK-ACCESS-SIGN", required = false) String signatureHeader,
            @RequestHeader(value = "OK-ACCESS-TIMESTAMP", required = false) String timestampHeader) {
        return paymentService.handleOkxA2aCallback(request, signatureHeader, timestampHeader);
    }

    @PostMapping("/intents/{intentId}/refund")
    public PaymentIntentView refundIntent(
            @PathVariable String intentId,
            @Valid @RequestBody PaymentActionRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return paymentIntentView(paymentService.refundIntent(intentId, request), request.actorAccountId(), includeAgent);
    }

    @PostMapping("/intents/{intentId}/cancel")
    public PaymentIntentView cancelIntent(
            @PathVariable String intentId,
            @Valid @RequestBody PaymentActionRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return paymentIntentView(paymentService.cancelIntent(intentId, request), request.actorAccountId(), includeAgent);
    }

    @PostMapping("/intents/{intentId}/dispute")
    public PaymentIntentView disputeIntent(
            @PathVariable String intentId,
            @Valid @RequestBody PaymentActionRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return paymentIntentView(paymentService.disputeIntent(intentId, request), request.actorAccountId(), includeAgent);
    }

    @PostMapping("/intents/{intentId}/default")
    public PaymentIntentView defaultIntent(
            @PathVariable String intentId,
            @Valid @RequestBody PaymentActionRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return paymentIntentView(paymentService.defaultIntent(intentId, request), request.actorAccountId(), includeAgent);
    }

    @PostMapping("/intents/{intentId}/refresh")
    public PaymentIntentView refreshIntent(
            @PathVariable String intentId,
            @Valid @RequestBody PaymentActionRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return paymentIntentView(paymentService.refreshIntent(intentId, request), request.actorAccountId(), includeAgent);
    }

    private PaymentIntentView paymentIntentView(PaymentIntentView view, String accountId, boolean includeAgent) {
        if (!includeAgent || view == null) {
            return view;
        }
        boolean payer = accountId != null && accountId.equals(view.accountId());
        boolean canReview = organizationAuthorityService.hasSystemCapability(accountId, ProjectCapability.PAYMENT_REVIEW);
        boolean canRefund = organizationAuthorityService.hasSystemCapability(accountId, ProjectCapability.PAYMENT_REFUND);
        boolean okxProvider = "okx".equalsIgnoreCase(view.provider());
        // 中文注释：支付能力会影响签名、退款和争议，按显式 includeAgent 才回传给自动化执行面。
        return view.withAgentState(
                agentResourceKeyFactory.paymentIntent(view.id()),
                agentCapabilityResolver.paymentIntentCapabilities(view.status(), payer, canReview, canRefund, okxProvider, accountId),
                agentCapabilityResolver.paymentIntentBlockedCapabilities(view.status(), payer, canReview, canRefund, okxProvider, accountId));
    }

    private void requireReadable(PaymentIntentView view, String accountId) {
        boolean payer = accountId != null && accountId.equals(view.accountId());
        boolean orderParticipant = orderRepository.findById(view.orderId())
                .map(order -> order.hasParticipant(accountId))
                .orElse(false);
        boolean operator = organizationAuthorityService.hasSystemCapability(accountId, ProjectCapability.PAYMENT_REVIEW)
                || organizationAuthorityService.hasSystemCapability(accountId, ProjectCapability.PAYMENT_REFUND);
        // 中文注释：卖方通过订单参与方关系读取支付凭证，订单是支付归属的唯一业务锚点。
        if (!payer && !orderParticipant && !operator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment intent participant or operator required");
        }
    }
}
