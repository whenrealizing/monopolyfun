package com.monopolyfun.modules.order.service.query;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.domain.ProofEntity;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderProgressUpdateRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.infra.ProofRepository;
import com.monopolyfun.modules.order.service.view.OrderDetailView;
import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.modules.order.service.view.ReviewContext;
import com.monopolyfun.modules.order.service.view.SettlementPreview;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.modules.post.service.query.PostViewResolver;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.infra.ShareReleaseRequestRepository;
import com.monopolyfun.modules.share.infra.ShareSettlementHoldRepository;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final ProofRepository proofRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderProgressUpdateRepository orderProgressUpdateRepository;
    private final OrderActionPolicy actionPolicy;
    private final PostViewResolver postViewResolver;
    private final PostItemWorkspaceQueryService postItemWorkspaceQueryService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ShareSettlementHoldRepository shareSettlementHoldRepository;
    private final ShareReleaseRequestRepository shareReleaseRequestRepository;
    private final WorkRepository workRepository;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;

    public OrderQueryService(
            OrderRepository orderRepository,
            ProofRepository proofRepository,
            PaymentIntentRepository paymentIntentRepository,
            OrderEventRepository orderEventRepository,
            OrderProgressUpdateRepository orderProgressUpdateRepository,
            OrderActionPolicy actionPolicy,
            PostViewResolver postViewResolver,
            PostItemWorkspaceQueryService postItemWorkspaceQueryService,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService,
            ShareSettlementHoldRepository shareSettlementHoldRepository,
            ShareReleaseRequestRepository shareReleaseRequestRepository,
            WorkRepository workRepository,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory) {
        this.orderRepository = orderRepository;
        this.proofRepository = proofRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderProgressUpdateRepository = orderProgressUpdateRepository;
        this.actionPolicy = actionPolicy;
        this.postViewResolver = postViewResolver;
        this.postItemWorkspaceQueryService = postItemWorkspaceQueryService;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
        this.shareSettlementHoldRepository = shareSettlementHoldRepository;
        this.shareReleaseRequestRepository = shareReleaseRequestRepository;
        this.workRepository = workRepository;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
    }

    public List<OrderSummary> listCurrentOrders() {
        return listCurrentOrders(false);
    }

    public List<OrderSummary> listCurrentOrders(boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        // 中文注释：订单列表直接按参与方快照查询，组织审核补充入口由后台和详情页承担。
        List<OrderEntity> orders = orderRepository.findByParticipantAccountId(accountId, 300);
        Map<String, PaymentIntentEntity> paymentIntents = paymentIntentRepository.findByOrderIds(orders.stream().map(OrderEntity::id).toList());
        return orders.stream()
                .map(order -> orderSummary(order, accountId, includeAgent, paymentIntents.get(order.id())))
                .toList();
    }

    public PageResult<OrderSummary> listCurrentOrders(PageQuery pageQuery) {
        return listCurrentOrders(pageQuery, false);
    }

    public PageResult<OrderSummary> listCurrentOrders(PageQuery pageQuery, boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        var page = orderRepository.findByParticipantAccountId(accountId, pageQuery);
        Map<String, PaymentIntentEntity> paymentIntents = paymentIntentRepository.findByOrderIds(page.items().stream().map(OrderEntity::id).toList());
        return new PageResult<>(page.items().stream()
                .map(order -> orderSummary(order, accountId, includeAgent, paymentIntents.get(order.id())))
                .toList(), page.pageInfo());
    }

    public OrderDetailView getOrderDetail(String orderId) {
        return getOrderDetail(orderId, false);
    }

    public OrderDetailView getOrderDetail(String orderId, boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        OrderEntity order = orderRepository.findByOrderNo(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!canReadOrder(order, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order participant required");
        }
        ProofEntity proof = proofRepository.findById(order.proofId()).orElse(null);
        PaymentIntentEntity paymentIntent = paymentIntentRepository.findByOrderId(order.id()).orElse(null);
        OrderSummary summary = orderSummary(order, accountId, currentAccountRole(order, accountId), includeAgent, paymentIntent);

        return new OrderDetailView(
                summary,
                postViewResolver.resolve(order),
                resolveItem(order, includeAgent),
                com.monopolyfun.modules.order.service.mapper.OrderViewMapper.proof(proof),
                orderProgressUpdateRepository.findByOrderId(order.id()).stream().map(com.monopolyfun.modules.order.service.mapper.OrderViewMapper::progress).toList(),
                com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(paymentIntent),
                actionPolicy.availableActions(order),
                order.displayPhase(),
                settlementPreview(order, paymentIntent),
                shareSettlementHoldRepository.findByOrderId(order.id())
                        .map(hold -> com.monopolyfun.modules.share.service.mapper.ShareViewMapper.shareSettlementHold(hold, summary))
                        .orElse(null),
                shareReleaseRequestRepository.findByOrderId(order.id())
                        .map(com.monopolyfun.modules.share.service.mapper.ShareViewMapper::shareReleaseRequest)
                        .orElse(null),
                reviewContext(order),
                orderEventRepository.findByOrderId(order.id()).stream().map(com.monopolyfun.modules.order.service.mapper.OrderViewMapper::event).toList());
    }

    private SettlementPreview settlementPreview(OrderEntity order, PaymentIntentEntity paymentIntent) {
        boolean willMintShares = order.settlementType() == SettlementType.SHARES
                && (order.status() == OrderStatus.DELIVERED
                || order.status() == OrderStatus.ACCEPTED_OPEN
                || order.status() == OrderStatus.DISPUTED
                || order.status() == OrderStatus.FINAL_ACCEPTED);
        String accountId = order.fulfillerAccountId();
        String summary = order.settlementType() == SettlementType.MONEY
                ? paymentSummary(paymentIntent)
                : switch (order.status()) {
            case ACCEPTED_OPEN -> "订单已验收，争议窗口关闭后释放 Shares";
            case DISPUTED -> "订单争议处理中，股份发放等待最终结论";
            case FINAL_ACCEPTED -> "Shares 已写入接收账号账本";
            case DELIVERED -> "验收后进入争议窗口，再释放 Shares";
            default -> "结算快照已记录，等待订单推进";
        };
        // 中文注释：页面契约统一使用小写枚举字符串，前端生成类型可以直接用于展示判断。
        return new SettlementPreview(order.settlementType().name().toLowerCase(Locale.ROOT), order.effectiveSettlementAmount(), accountId, willMintShares, summary);
    }

    private PostItemView resolveItem(OrderEntity order, boolean includeAgent) {
        try {
            return postItemWorkspaceQueryService.getItem(order.listingId(), includeAgent);
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private String paymentSummary(PaymentIntentEntity paymentIntent) {
        if (paymentIntent == null) {
            return "现金订单需要先创建支付会话";
        }
        return "支付会话 " + paymentIntent.status().name().toLowerCase(Locale.ROOT) + " · " + paymentIntent.amountMinor() + " " + paymentIntent.currency();
    }

    private boolean canReadOrder(OrderEntity order, String accountId) {
        // 中文注释：订单详情采用参与方可见，组织权限只用于运营验收和争议处理入口。
        return order.hasParticipant(accountId)
                || organizationAuthorityService.canReviewOrder(accountId, order)
                || organizationAuthorityService.canResolveOrderDispute(accountId, order);
    }

    private String currentAccountRole(OrderEntity order, String accountId) {
        String participantRole = order.roleFor(accountId);
        if (participantRole != null) {
            return participantRole;
        }
        // 中文注释：非参与方详情访问只来自组织权限，统一标成 authority，避免前端再猜后台身份。
        if (organizationAuthorityService.canResolveOrderDispute(accountId, order)
                || organizationAuthorityService.canReviewOrder(accountId, order)) {
            return OrderEntity.ROLE_AUTHORITY;
        }
        return null;
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId) {
        return orderSummary(order, accountId, false);
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId, boolean includeAgent) {
        return orderSummary(order, accountId, order.roleFor(accountId), includeAgent, null);
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId, String currentAccountRole) {
        return orderSummary(order, accountId, currentAccountRole, false);
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId, String currentAccountRole, boolean includeAgent) {
        return orderSummary(order, accountId, currentAccountRole, includeAgent, null);
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId, boolean includeAgent, PaymentIntentEntity paymentIntent) {
        return orderSummary(order, accountId, order.roleFor(accountId), includeAgent, paymentIntent);
    }

    private OrderSummary orderSummary(OrderEntity order, String accountId, String currentAccountRole, boolean includeAgent, PaymentIntentEntity paymentIntent) {
        OrderSummary summary = com.monopolyfun.modules.order.service.mapper.OrderViewMapper.order(order, accountId, currentAccountRole)
                .withPaymentState(null, paymentDueAt(order));
        summary = withPaymentState(summary, paymentIntent);
        if (!includeAgent) {
            return summary;
        }
        // 中文注释：订单 agent 能力与当前账号角色强绑定，只有 agent 读取时才扩展这组动作提示。
        return summary.withAgentState(
                agentResourceKeyFactory.order(order.orderNo()),
                agentCapabilityResolver.orderCapabilities(order, accountId),
                agentCapabilityResolver.orderBlockedCapabilities(order, accountId));
    }

    private OrderSummary withPaymentState(OrderSummary summary, PaymentIntentEntity paymentIntent) {
        if (paymentIntent == null) {
            return summary;
        }
        java.time.Instant dueAt = paymentDueAt(paymentIntent);
        return summary.withPaymentState(
                paymentIntent.status() == null ? null : paymentIntent.status().name().toLowerCase(Locale.ROOT),
                dueAt == null ? summary.paymentDueAt() : dueAt);
    }

    private java.time.Instant paymentDueAt(PaymentIntentEntity paymentIntent) {
        Object value = paymentIntent.metadata() == null ? null : paymentIntent.metadata().get("paymentDueAt");
        return paymentDueAt(value);
    }

    private java.time.Instant paymentDueAt(Map<String, Object> metadata) {
        return paymentDueAt(metadata == null ? null : metadata.get("paymentDueAt"));
    }

    private java.time.Instant paymentDueAt(OrderEntity order) {
        return paymentDueAt(order.metadata());
    }

    private java.time.Instant paymentDueAt(Object value) {
        if (value instanceof java.time.Instant instant) {
            return instant;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.time.Instant.parse(text);
            } catch (java.time.format.DateTimeParseException exception) {
                return null;
            }
        }
        return null;
    }

    private ReviewContext reviewContext(OrderEntity order) {
        if (order.parentOrderId() == null && order.reviewPostId() == null && order.disputeReason() == null) {
            return null;
        }
        OrderEntity parentOrder = order.parentOrderId() == null ? null : orderRepository.findById(order.parentOrderId()).orElse(null);
        OrderEntity reviewOrder = orderRepository.findFirstByParentOrderId(order.id()).orElse(null);
        // 中文注释：争议证据来源于 WorkItem 输出契约，详情页直接展示进入 Work Review 时的不可变快照。
        List<String> disputeEvidenceRefs = order.reviewPostId() == null
                ? List.of()
                : workRepository.findItemsBySource("order", order.orderNo()).stream()
                .filter(item -> order.reviewPostId().equals(item.itemNo()))
                .findFirst()
                .map(item -> stringList(item.outputSchema().get("evidenceRefs")))
                .orElse(List.of());
        return new ReviewContext(
                order.parentOrderId(),
                parentOrder == null ? null : parentOrder.orderNo(),
                order.reviewPostId(),
                reviewOrder == null ? null : reviewOrder.id(),
                reviewOrder == null ? null : reviewOrder.orderNo(),
                order.disputeReason(),
                order.disputeOpenedByAccountId(),
                order.disputeOpenedFromStatus() == null ? null : order.disputeOpenedFromStatus().name().toLowerCase(Locale.ROOT),
                order.disputeOpenedFromWindowStatus(),
                order.disputeOpenedFromWindowExpiresAt(),
                order.disputeOpenedAt(),
                order.disputeCancelledByAccountId(),
                order.disputeCancelledAt(),
                order.disputeCancelReason(),
                order.reviewerAccountId(),
                order.reviewDueAt(),
                order.backofficeOverrideDecision() == null ? null : order.backofficeOverrideDecision().name().toLowerCase(),
                order.backofficeOverrideReason(),
                disputeEvidenceRefs);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }
}
