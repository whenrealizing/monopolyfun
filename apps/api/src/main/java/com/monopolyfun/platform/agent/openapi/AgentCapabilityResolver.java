package com.monopolyfun.platform.agent.openapi;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.OfferStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.domain.RequestStatus;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentCapabilityResolver {
    private static final List<AgentCapabilityPolicy.AgentCapabilityRule> OFFER_RULES = List.of(
            capabilityRule("offer.create_item", facts(
                    fact("login", "login_required"),
                    fact("owner", "account_not_owner"),
                    fact("offer_open", "offer_not_open"))),
            capabilityRule("offer.close", facts(
                    fact("login", "login_required"),
                    fact("owner", "account_not_owner"),
                    fact("offer_open", "offer_not_open"))));
    private static final List<AgentCapabilityPolicy.AgentCapabilityRule> REQUEST_RULES = List.of(
            capabilityRule("request.create_item", facts(
                    fact("login", "login_required"),
                    fact("owner", "account_not_owner"),
                    fact("request_open", "request_not_open"))),
            capabilityRule("request.close", facts(
                    fact("login", "login_required"),
                    fact("owner", "account_not_owner"),
                    fact("request_open", "request_not_open"))));

    private final AgentCapabilityPolicy capabilityPolicy;

    public AgentCapabilityResolver(AgentCapabilityPolicy capabilityPolicy) {
        this.capabilityPolicy = capabilityPolicy;
    }

    private static AgentCapabilityPolicy.AgentCapabilityRule capabilityRule(
            String capability,
            List<AgentCapabilityPolicy.FactRequirement> facts) {
        return new AgentCapabilityPolicy.AgentCapabilityRule(capability, facts);
    }

    private static List<AgentCapabilityPolicy.FactRequirement> facts(AgentCapabilityPolicy.FactRequirement... facts) {
        return List.of(facts);
    }

    private static AgentCapabilityPolicy.FactRequirement fact(String fact, String blockedReason) {
        return new AgentCapabilityPolicy.FactRequirement(fact, blockedReason);
    }

    public List<String> offerCapabilities(OfferEntity offer, String accountId) {
        if (offer == null) {
            return List.of();
        }
        return capabilityPolicy.allowedCapabilities(capabilityPolicy.decide(OFFER_RULES, offerFacts(offer, accountId)));
    }

    public List<Map<String, Object>> offerBlockedCapabilities(OfferEntity offer, String accountId) {
        if (offer == null) {
            return List.of();
        }
        return capabilityPolicy.blockedCapabilities(capabilityPolicy.decide(OFFER_RULES, offerFacts(offer, accountId)));
    }

    public List<String> requestCapabilities(RequestEntity request, String accountId) {
        if (request == null) {
            return List.of();
        }
        return capabilityPolicy.allowedCapabilities(capabilityPolicy.decide(REQUEST_RULES, requestFacts(request, accountId)));
    }

    public List<Map<String, Object>> requestBlockedCapabilities(RequestEntity request, String accountId) {
        if (request == null) {
            return List.of();
        }
        return capabilityPolicy.blockedCapabilities(capabilityPolicy.decide(REQUEST_RULES, requestFacts(request, accountId)));
    }

    public List<String> projectCapabilities(
            ProjectEntity project,
            boolean canCreateItem,
            boolean canAssignRole,
            String accountId) {
        if (project == null || accountId == null || accountId.isBlank()) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        if (project.status() == ProjectStatus.ACTIVE && canCreateItem) {
            capabilities.add("project.create_item");
        }
        if (project.status() == ProjectStatus.CLAIMABLE) {
            capabilities.add("project.claim_owner");
        }
        if (project.status() == ProjectStatus.ACTIVE && canAssignRole) {
            capabilities.add("project.recommend_role_candidate");
            capabilities.add("project.draft_role_invite");
            capabilities.add("project.send_role_invite");
            capabilities.add("project.appoint_role");
        }
        return List.copyOf(capabilities);
    }

    public List<Map<String, Object>> projectBlockedCapabilities(
            ProjectEntity project,
            boolean canCreateItem,
            boolean canAssignRole,
            String accountId) {
        List<Map<String, Object>> blocked = new ArrayList<>();
        if (project == null) {
            return blocked;
        }
        if (accountId == null || accountId.isBlank()) {
            blocked.add(block("project.create_item", "login_required"));
            blocked.add(block("project.recommend_role_candidate", "login_required"));
            blocked.add(block("project.draft_role_invite", "login_required"));
            blocked.add(block("project.send_role_invite", "login_required"));
            blocked.add(block("project.appoint_role", "login_required"));
            return blocked;
        }
        if (project.status() != ProjectStatus.ACTIVE) {
            blocked.add(block("project.create_item", "project_not_active"));
            blocked.add(block("project.recommend_role_candidate", "project_not_active"));
            blocked.add(block("project.draft_role_invite", "project_not_active"));
            blocked.add(block("project.send_role_invite", "project_not_active"));
            blocked.add(block("project.appoint_role", "project_not_active"));
            return blocked;
        }
        if (!canCreateItem) {
            blocked.add(block("project.create_item", "capability_required"));
        }
        if (!canAssignRole) {
            blocked.add(block("project.recommend_role_candidate", "capability_required"));
            blocked.add(block("project.draft_role_invite", "capability_required"));
            blocked.add(block("project.send_role_invite", "capability_required"));
            blocked.add(block("project.appoint_role", "capability_required"));
        }
        return blocked;
    }

    public List<String> postItemCapabilities(
            PostKind postKind,
            ListingEntity item,
            String ownerAccountId,
            String accountId) {
        if (item == null || accountId == null || accountId.isBlank()) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        if (item.status() == ListingStatus.OPEN && canClaim(postKind, ownerAccountId, accountId)) {
            capabilities.add("post_item.claim");
        }
        if (accountId.equals(ownerAccountId) && item.status() == ListingStatus.OPEN) {
            capabilities.add("post_item.close");
        }
        return List.copyOf(capabilities);
    }

    public List<Map<String, Object>> postItemBlockedCapabilities(
            PostKind postKind,
            ListingEntity item,
            String ownerAccountId,
            String accountId) {
        List<Map<String, Object>> blocked = new ArrayList<>();
        if (item == null) {
            return blocked;
        }
        if (accountId == null || accountId.isBlank()) {
            blocked.add(block("post_item.claim", "login_required"));
            return blocked;
        }
        if (item.status() != ListingStatus.OPEN) {
            blocked.add(block("post_item.claim", "item_not_open"));
            blocked.add(block("post_item.close", "item_not_open"));
            return blocked;
        }
        if (!canClaim(postKind, ownerAccountId, accountId)) {
            blocked.add(block("post_item.claim", "self_claim_forbidden"));
        }
        if (!accountId.equals(ownerAccountId)) {
            blocked.add(block("post_item.close", "account_not_owner"));
        }
        return blocked;
    }

    public List<String> orderCapabilities(OrderEntity order, String accountId) {
        if (order == null || accountId == null || accountId.isBlank() || !order.hasParticipant(accountId)) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        if (order.status() == OrderStatus.CLAIMED && accountId.equals(order.buyerAccountId())) {
            capabilities.add("order.pay");
        }
        if (order.status() == OrderStatus.CLAIMED && accountId.equals(order.fulfillerAccountId())) {
            capabilities.add("order.submit_proof");
        }
        if (order.status() == OrderStatus.DELIVERED && accountId.equals(order.acceptorAccountId())) {
            capabilities.add("order.accept");
            capabilities.add("order.dispute");
        }
        if (order.status() == OrderStatus.ACCEPTED_OPEN && order.hasParticipant(accountId)) {
            capabilities.add("order.dispute");
        }
        return List.copyOf(capabilities);
    }

    public List<Map<String, Object>> orderBlockedCapabilities(OrderEntity order, String accountId) {
        List<Map<String, Object>> blocked = new ArrayList<>();
        if (order == null) {
            return blocked;
        }
        if (accountId == null || accountId.isBlank()) {
            blocked.add(block("order.pay", "login_required"));
            blocked.add(block("order.submit_proof", "login_required"));
            blocked.add(block("order.accept", "login_required"));
            return blocked;
        }
        if (!order.hasParticipant(accountId)) {
            blocked.add(block("order.pay", "account_not_participant"));
            blocked.add(block("order.submit_proof", "account_not_participant"));
            blocked.add(block("order.accept", "account_not_participant"));
            return blocked;
        }
        if (order.status() != OrderStatus.CLAIMED) {
            blocked.add(block("order.pay", "order_not_claimed"));
            blocked.add(block("order.submit_proof", "order_not_claimed"));
        }
        if (order.status() != OrderStatus.DELIVERED) {
            blocked.add(block("order.accept", "order_not_delivered"));
        }
        return blocked;
    }

    public List<String> paymentIntentCapabilities(
            PaymentIntentStatus status,
            boolean isPayer,
            boolean canReview,
            boolean canRefund,
            boolean okxProvider,
            String accountId) {
        if (status == null || accountId == null || accountId.isBlank()) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        if (okxProvider && isActivePaymentStatus(status) && (isPayer || canReview)) {
            capabilities.add("payment.refresh");
        }
        if (isCancelablePaymentStatus(status) && (isPayer || canReview)) {
            capabilities.add("payment.cancel");
        }
        if (status == PaymentIntentStatus.CAPTURED && canRefund) {
            capabilities.add("payment.refund");
        }
        if (isDisputablePaymentStatus(status) && (isPayer || canReview)) {
            capabilities.add("payment.dispute");
        }
        return List.copyOf(capabilities);
    }

    public List<Map<String, Object>> paymentIntentBlockedCapabilities(
            PaymentIntentStatus status,
            boolean isPayer,
            boolean canReview,
            boolean canRefund,
            boolean okxProvider,
            String accountId) {
        List<Map<String, Object>> blocked = new ArrayList<>();
        if (status == null) {
            return blocked;
        }
        if (accountId == null || accountId.isBlank()) {
            blocked.add(block("payment.refresh", "login_required"));
            blocked.add(block("payment.cancel", "login_required"));
            blocked.add(block("payment.refund", "login_required"));
            return blocked;
        }
        if (!okxProvider || !isActivePaymentStatus(status) || (!isPayer && !canReview)) {
            blocked.add(block("payment.refresh", paymentBlockReason(status, isPayer, canReview, okxProvider)));
        }
        if (!isCancelablePaymentStatus(status) || (!isPayer && !canReview)) {
            blocked.add(block("payment.cancel", paymentBlockReason(status, isPayer, canReview, true)));
        }
        if (status != PaymentIntentStatus.CAPTURED || !canRefund) {
            blocked.add(block("payment.refund", status == PaymentIntentStatus.CAPTURED ? "capability_required" : "payment_not_captured"));
        }
        if (!isDisputablePaymentStatus(status) || (!isPayer && !canReview)) {
            blocked.add(block("payment.dispute", paymentBlockReason(status, isPayer, canReview, true)));
        }
        return blocked;
    }

    public List<String> shareReleaseRequestCapabilities(
            ShareReleaseRequestStatus status,
            List<ProjectRoleCode> requiredRoles,
            List<ProjectRoleCode> approvedRoles,
            List<ProjectRoleCode> assignedRoles,
            String accountId) {
        if (status != ShareReleaseRequestStatus.PENDING || accountId == null || accountId.isBlank()) {
            return List.of();
        }
        if (hasRemainingApprovalRole(requiredRoles, approvedRoles, assignedRoles)) {
            return List.of("share_release.approve");
        }
        return List.of();
    }

    public List<Map<String, Object>> shareReleaseRequestBlockedCapabilities(
            ShareReleaseRequestStatus status,
            List<ProjectRoleCode> requiredRoles,
            List<ProjectRoleCode> approvedRoles,
            List<ProjectRoleCode> assignedRoles,
            String accountId) {
        if (status == null) {
            return List.of();
        }
        if (accountId == null || accountId.isBlank()) {
            return List.of(block("share_release.approve", "login_required"));
        }
        if (status != ShareReleaseRequestStatus.PENDING) {
            return List.of(block("share_release.approve", "request_resolved"));
        }
        if (!hasRemainingApprovalRole(requiredRoles, approvedRoles, assignedRoles)) {
            return List.of(block("share_release.approve", "approval_role_required"));
        }
        return List.of();
    }

    private boolean canClaim(PostKind postKind, String ownerAccountId, String accountId) {
        if (postKind == PostKind.PROJECT) {
            return true;
        }
        return ownerAccountId == null || !ownerAccountId.equals(accountId);
    }

    private boolean hasRemainingApprovalRole(
            List<ProjectRoleCode> requiredRoles,
            List<ProjectRoleCode> approvedRoles,
            List<ProjectRoleCode> assignedRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty() || assignedRoles == null || assignedRoles.isEmpty()) {
            return false;
        }
        List<ProjectRoleCode> approved = approvedRoles == null ? List.of() : approvedRoles;
        return assignedRoles.stream()
                .anyMatch(role -> requiredRoles.contains(role) && !approved.contains(role));
    }

    private boolean isActivePaymentStatus(PaymentIntentStatus status) {
        return status == PaymentIntentStatus.PENDING || status == PaymentIntentStatus.AUTHORIZED;
    }

    private boolean isCancelablePaymentStatus(PaymentIntentStatus status) {
        return status == PaymentIntentStatus.PENDING || status == PaymentIntentStatus.AUTHORIZED;
    }

    private boolean isDisputablePaymentStatus(PaymentIntentStatus status) {
        return status == PaymentIntentStatus.PENDING
                || status == PaymentIntentStatus.AUTHORIZED
                || status == PaymentIntentStatus.CAPTURED;
    }

    private String paymentBlockReason(PaymentIntentStatus status, boolean isPayer, boolean canReview, boolean providerSupported) {
        if (!providerSupported) {
            return "provider_not_supported";
        }
        if (!isPayer && !canReview) {
            return "capability_required";
        }
        return status == null ? "payment_missing" : "payment_status_" + status.name().toLowerCase();
    }

    private Map<String, Object> block(String capability, String reason) {
        // 中文注释：先把 blocked capability 统一收口为决策对象，后续可替换为 Casbin/OPA 策略输出而不改响应结构。
        return AgentCapabilityDecision.blocked(capability, reason).blockedCapability();
    }

    private Set<String> offerFacts(OfferEntity offer, String accountId) {
        java.util.HashSet<String> facts = new java.util.HashSet<>();
        if (accountId != null && !accountId.isBlank()) {
            facts.add("login");
        }
        if (accountId != null && accountId.equals(offer.actorAccountId())) {
            facts.add("owner");
        }
        if (offer.status() == OfferStatus.OPEN) {
            facts.add("offer_open");
        }
        return Set.copyOf(facts);
    }

    private Set<String> requestFacts(RequestEntity request, String accountId) {
        java.util.HashSet<String> facts = new java.util.HashSet<>();
        if (accountId != null && !accountId.isBlank()) {
            facts.add("login");
        }
        if (accountId != null && accountId.equals(request.actorAccountId())) {
            facts.add("owner");
        }
        if (request.status() == RequestStatus.OPEN) {
            facts.add("request_open");
        }
        return Set.copyOf(facts);
    }
}
