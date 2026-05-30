package com.monopolyfun.modules.share.service;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestEntity;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestStatus;
import com.monopolyfun.modules.share.domain.ShareSettlementHoldEntity;
import com.monopolyfun.modules.share.infra.ShareReleaseApprovalRepository;
import com.monopolyfun.modules.share.infra.ShareReleaseRequestRepository;
import com.monopolyfun.modules.share.infra.ShareSettlementHoldRepository;
import com.monopolyfun.modules.work.service.ProjectWorkItemPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ShareReleaseService {
    private static final List<ProjectRoleCode> APPROVAL_ROLES = List.of(
            ProjectRoleCode.SYSTEM_CEO,
            ProjectRoleCode.SYSTEM_CFO);

    private final ShareReleaseRequestRepository releaseRequestRepository;
    private final ShareReleaseApprovalRepository releaseApprovalRepository;
    private final ShareSettlementHoldRepository shareSettlementHoldRepository;
    private final ProjectContributionSettlementService contributionSettlementService;
    private final MarketRepository marketRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final RootProjectService rootProjectService;
    private final ProjectSharePoolService projectSharePoolService;
    private final ProjectWorkItemPublisher projectWorkItemPublisher;

    public ShareReleaseService(
            ShareReleaseRequestRepository releaseRequestRepository,
            ShareReleaseApprovalRepository releaseApprovalRepository,
            ShareSettlementHoldRepository shareSettlementHoldRepository,
            ProjectContributionSettlementService contributionSettlementService,
            MarketRepository marketRepository,
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            OrganizationAuthorityService organizationAuthorityService,
            RootProjectService rootProjectService,
            ProjectSharePoolService projectSharePoolService,
            ProjectWorkItemPublisher projectWorkItemPublisher) {
        this.releaseRequestRepository = releaseRequestRepository;
        this.releaseApprovalRepository = releaseApprovalRepository;
        this.shareSettlementHoldRepository = shareSettlementHoldRepository;
        this.contributionSettlementService = contributionSettlementService;
        this.marketRepository = marketRepository;
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.rootProjectService = rootProjectService;
        this.projectSharePoolService = projectSharePoolService;
        this.projectWorkItemPublisher = projectWorkItemPublisher;
    }

    public ShareReleaseRequestEntity requestRelease(OrderEntity order, String actorAccountId) {
        if (order.settlementType() != SettlementType.SHARES || order.proofId() == null) {
            return null;
        }
        ShareReleaseRequestEntity existing = releaseRequestRepository.findByOrderId(order.id()).orElse(null);
        if (existing != null) {
            existing = resolvePendingRequestIfReady(order, existing);
            mintIfResolved(order, existing);
            publishReleaseWorkItems(existing, Instant.now());
            return existing;
        }

        MarketEntity market = requireMarket(order.marketId());
        int amount = releaseAmount(order);
        if (amount <= 0) {
            return null;
        }
        int curveSlot = releaseCurveSlot(order, market);
        String accountId = releaseAccountId(order);
        ShareIssuerType issuerType = ShareIssuerType.PROJECT;
        String projectId = issuerProjectId(order, actorAccountId);
        String issuerId = projectId;
        List<ProjectRoleCode> requiredRoles = requiredApprovalRoles(projectId);
        List<ProjectRoleCode> skippedRoles = skippedApprovalRoles(projectId);
        boolean approvalOpen = canApproveReleaseRequest(order);
        List<ProjectRoleCode> approvedRoles = approvableRolesFor(projectId, actorAccountId, requiredRoles);
        ShareReleaseRequestStatus status = !approvalOpen
                ? ShareReleaseRequestStatus.PENDING
                : requiredRoles.isEmpty()
                ? ShareReleaseRequestStatus.SKIPPED
                : approvedRoles.containsAll(requiredRoles) ? ShareReleaseRequestStatus.APPROVED : ShareReleaseRequestStatus.PENDING;
        Instant now = Instant.now();

        // 中文注释：share release request 是 shares 发放的唯一闸门，账本 mint 只在请求完成后发生。
        ShareReleaseRequestEntity request = releaseRequestRepository.save(new ShareReleaseRequestEntity(
                "share-release-" + UUID.randomUUID(),
                issuerType,
                issuerId,
                order.marketId(),
                projectId,
                order.id(),
                order.proofId(),
                accountId,
                amount,
                curveSlot,
                status,
                requiredRoles,
                approvedRoles,
                skippedRoles,
                actorAccountId,
                status == ShareReleaseRequestStatus.PENDING ? null : now,
                releaseMetadata(order),
                now,
                now));
        for (ProjectRoleCode roleCode : approvedRoles) {
            releaseApprovalRepository.save(request.id(), roleCode, actorAccountId);
        }
        saveEvent(order.id(), "share_release_requested", actorAccountId, Map.of(
                "shareReleaseRequestId", request.id(),
                "issuerType", issuerType.code(),
                "issuerId", issuerId,
                "status", request.status().code()));
        mintIfResolved(order, request);
        publishReleaseWorkItems(request, now);
        return request;
    }

    public ShareReleaseRequestEntity approveRequest(String requestId, String approverAccountId) {
        ShareReleaseRequestEntity request = releaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share release request not found"));
        if (request.isResolved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Share release request is already resolved");
        }
        OrderEntity order = requireOrderSnapshot(request);
        if (!canApproveReleaseRequest(order)) {
            // 中文注释：审批动作等订单进入最终可结算状态后开放，争议窗口内只保留 pending request 和 hold。
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Share release approval requires final accepted order");
        }
        List<ProjectRoleCode> approvableRoles = approvableRolesFor(
                request.projectId(),
                approverAccountId,
                request.requiredRoleCodes()).stream()
                .filter(role -> !request.approvedRoleCodes().contains(role))
                .toList();
        if (approvableRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Share release approval role required");
        }
        for (ProjectRoleCode roleCode : approvableRoles) {
            releaseApprovalRepository.save(request.id(), roleCode, approverAccountId);
        }
        List<ProjectRoleCode> approvedRoles = mergeRoles(request.approvedRoleCodes(), approvableRoles);
        ShareReleaseRequestStatus status = approvedRoles.containsAll(request.requiredRoleCodes())
                ? ShareReleaseRequestStatus.APPROVED
                : ShareReleaseRequestStatus.PENDING;
        Instant now = Instant.now();
        ShareReleaseRequestEntity updated = status == ShareReleaseRequestStatus.APPROVED
                ? releaseRequestRepository.markResolved(request.id(), status, approvedRoles, now)
                : releaseRequestRepository.save(new ShareReleaseRequestEntity(
                request.id(),
                request.issuerType(),
                request.issuerId(),
                request.marketId(),
                request.projectId(),
                request.orderId(),
                request.proofId(),
                request.accountId(),
                request.amount(),
                request.curveSlot(),
                status,
                request.requiredRoleCodes(),
                approvedRoles,
                request.skippedRoleCodes(),
                request.requestedByAccountId(),
                null,
                request.metadata(),
                request.createdAt(),
                now));
        saveEvent(request.orderId(), "share_release_approved", approverAccountId, Map.of(
                "shareReleaseRequestId", request.id(),
                "approvedRoleCodes", approvedRoles.stream().map(ProjectRoleCode::code).toList(),
                "status", status.code()));
        if (updated.isResolved()) {
            mintIfResolved(order, updated);
        }
        publishReleaseWorkItems(updated, now);
        return updated;
    }

    public ShareSettlementHoldEntity lockOrderSettlement(OrderEntity order, String actorAccountId, String lockReason) {
        if (!requiresSettlementHold(order)) {
            return null;
        }
        Instant now = Instant.now();
        ShareSettlementHoldEntity existing = shareSettlementHoldRepository.findByOrderId(order.id()).orElse(null);
        if (existing != null) {
            ShareSettlementHoldEntity locked = existing.withLockReason(lockReason, existing.shareReleaseRequestId(), now);
            return shareSettlementHoldRepository.save(locked);
        }
        ShareSettlementHoldEntity hold = new ShareSettlementHoldEntity(
                "share-hold-" + UUID.randomUUID(),
                order.id(),
                order.proofId(),
                null,
                order.marketId(),
                issuerProjectId(order, actorAccountId),
                PostItemSupport.itemId(order.metadata(), order.listingId()),
                releaseAccountId(order),
                releaseAmount(order),
                releaseCurveSlot(order, requireMarket(order.marketId())),
                order.kind() == ListingKind.REVIEW ? LedgerReason.REVIEW_ORDER : LedgerReason.WORK_ORDER,
                ShareSettlementHoldEntity.STATUS_LOCKED,
                lockReason,
                null,
                null,
                null,
                holdMetadata(order),
                now,
                now);
        saveEvent(order.id(), "share_settlement_locked", actorAccountId, Map.of(
                "holdId", hold.id(),
                "lockReason", lockReason,
                "amount", hold.amount()));
        return shareSettlementHoldRepository.save(hold);
    }

    public ShareSettlementHoldEntity freezeOrderSettlement(OrderEntity order, String actorAccountId) {
        ShareSettlementHoldEntity existing = shareSettlementHoldRepository.findByOrderId(order.id()).orElse(null);
        if (existing == null) {
            return null;
        }
        ShareSettlementHoldEntity frozen = existing.withLockReason(ShareSettlementHoldEntity.LOCK_REASON_DISPUTED, existing.shareReleaseRequestId(), Instant.now());
        saveEvent(order.id(), "share_settlement_frozen", actorAccountId, Map.of(
                "holdId", frozen.id(),
                "lockReason", ShareSettlementHoldEntity.LOCK_REASON_DISPUTED));
        return shareSettlementHoldRepository.save(frozen);
    }

    public void releaseOrderSettlement(OrderEntity order, String actorAccountId, String releaseReason) {
        if (order.settlementType() != SettlementType.SHARES || order.proofId() == null) {
            return;
        }
        ShareSettlementHoldEntity hold = shareSettlementHoldRepository.findByOrderId(order.id()).orElse(null);
        ShareReleaseRequestEntity request = requestRelease(order, actorAccountId);
        if (request == null) {
            return;
        }
        if (hold == null && requiresSettlementHold(order)) {
            Instant now = Instant.now();
            hold = shareSettlementHoldRepository.save(new ShareSettlementHoldEntity(
                    "share-hold-" + UUID.randomUUID(),
                    order.id(),
                    order.proofId(),
                    request.id(),
                    order.marketId(),
                    issuerProjectId(order, actorAccountId),
                    PostItemSupport.itemId(order.metadata(), order.listingId()),
                    releaseAccountId(order),
                    releaseAmount(order),
                    releaseCurveSlot(order, requireMarket(order.marketId())),
                    order.kind() == ListingKind.REVIEW ? LedgerReason.REVIEW_ORDER : LedgerReason.WORK_ORDER,
                    ShareSettlementHoldEntity.STATUS_LOCKED,
                    ShareSettlementHoldEntity.LOCK_REASON_APPROVAL_PENDING,
                    null,
                    null,
                    null,
                    holdMetadata(order),
                    now,
                    now));
        }
        if (request.isResolved()) {
            shareSettlementHoldRepository.save(hold.withReleased(releaseReason, request.id(), Instant.now()));
            return;
        }
        shareSettlementHoldRepository.save(hold.withLockReason(ShareSettlementHoldEntity.LOCK_REASON_APPROVAL_PENDING, request.id(), Instant.now()));
    }

    public ShareSettlementHoldEntity cancelOrderSettlement(OrderEntity order, String actorAccountId, String releaseReason) {
        ShareSettlementHoldEntity hold = shareSettlementHoldRepository.findByOrderId(order.id()).orElse(null);
        if (hold == null || hold.isReleased() || hold.isCancelled()) {
            return hold;
        }
        ShareSettlementHoldEntity cancelled = hold.withCancelled(releaseReason, Instant.now());
        saveEvent(order.id(), "share_settlement_cancelled", actorAccountId, Map.of(
                "holdId", cancelled.id(),
                "releaseReason", releaseReason));
        return shareSettlementHoldRepository.save(cancelled);
    }

    public List<ShareSettlementHoldEntity> holdsForAccount(String accountId) {
        return shareSettlementHoldRepository.findByAccountId(accountId);
    }

    public List<ShareReleaseRequestEntity> pendingForApprover(String accountId) {
        return releaseRequestRepository.findPendingForRoleAssignee(accountId);
    }

    public ShareReleaseRequestEntity getRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share release request id is required");
        }
        return releaseRequestRepository.findById(requestId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share release request not found"));
    }

    private void mintIfResolved(OrderEntity order, ShareReleaseRequestEntity request) {
        if (!request.isResolved()) {
            return;
        }
        if (!canMintResolvedRequest(order)) {
            return;
        }
        // 中文注释：Order 释放的份额同时沉淀为项目贡献事实，贡献榜和 owner 接力共用同一账本。
        ProjectContributionSettlementService.ContributionSettlementResult settlement = contributionSettlementService.settle(new ProjectContributionSettlementService.ContributionCommand(
                request.projectId(),
                "order",
                order.id(),
                order.proofId(),
                request.accountId(),
                order.kind() == ListingKind.REVIEW ? "review_order" : "work_order",
                Math.min(10000, Math.max(0, request.amount())),
                request.amount(),
                0,
                "USDC",
                order.kind() == ListingKind.REVIEW ? LedgerReason.REVIEW_ORDER : LedgerReason.WORK_ORDER,
                order.settlementType(),
                request.issuerType(),
                request.issuerId(),
                request.marketId(),
                request.orderId(),
                request.proofId(),
                request.id(),
                PostItemSupport.itemId(order.metadata(), order.listingId()),
                request.curveSlot(),
                BigDecimal.valueOf(request.amount()),
                Map.of("orderNo", order.orderNo(), "shareReleaseRequestId", request.id()),
                false,
                Instant.now()));
        if (!settlement.shareInserted()) {
            return;
        }
        shareSettlementHoldRepository.findByOrderId(order.id())
                .filter(ShareSettlementHoldEntity::isLocked)
                .ifPresent(hold -> shareSettlementHoldRepository.save(hold.withReleased(resolveReleaseReason(order), request.id(), Instant.now())));
        saveEvent(order.id(), "shares_minted", request.accountId(), Map.of(
                "shareReleaseRequestId", request.id(),
                "issuerType", request.issuerType().code(),
                "issuerId", request.issuerId(),
                "amount", settlement.shareEntry().amount(),
                "curveSlot", settlement.shareEntry().curveSlot()));
        advanceMarketCurve(order, request);
    }

    private ShareReleaseRequestEntity resolvePendingRequestIfReady(OrderEntity order, ShareReleaseRequestEntity request) {
        if (request.isResolved() || !canApproveReleaseRequest(order)) {
            return request;
        }
        ShareReleaseRequestStatus status = request.requiredRoleCodes().isEmpty()
                ? ShareReleaseRequestStatus.SKIPPED
                : request.approvedRoleCodes().containsAll(request.requiredRoleCodes())
                ? ShareReleaseRequestStatus.APPROVED
                : ShareReleaseRequestStatus.PENDING;
        if (status == ShareReleaseRequestStatus.PENDING) {
            return request;
        }
        // 中文注释：争议窗口关闭后，已满足的审批请求在同一结算入口收口，避免空席或已签名请求永久 pending。
        return releaseRequestRepository.markResolved(request.id(), status, request.approvedRoleCodes(), Instant.now());
    }

    private void advanceMarketCurve(OrderEntity order, ShareReleaseRequestEntity request) {
        if (order.kind() == ListingKind.REVIEW) {
            return;
        }
        if (isProjectItemOrder(order)) {
            // 中文注释：任务 shares 的 curve 和预算状态只写 project_share_pools，market 不再承载财务状态。
            projectSharePoolService.mintTask(order.marketId(), request.amount(), request.curveSlot());
        }
        saveEvent(order.id(), "market_curve_slot_advanced", request.accountId(), Map.of(
                "previousSlot", request.curveSlot(),
                "nextSlot", request.curveSlot() + 1));
    }

    private void publishReleaseWorkItems(ShareReleaseRequestEntity request, Instant now) {
        projectWorkItemPublisher.publishShareRelease(request, organizationAuthorityService.listProjectRoles(request.projectId()), now);
    }

    private List<ProjectRoleCode> requiredApprovalRoles(String projectId) {
        Set<ProjectRoleCode> required = EnumSet.noneOf(ProjectRoleCode.class);
        for (RoleSlot slot : approvalSlots(projectId)) {
            if (slot.accountId() != null && !slot.accountId().isBlank()) {
                required.add(slot.roleCode());
            }
        }
        return APPROVAL_ROLES.stream().filter(required::contains).toList();
    }

    private List<ProjectRoleCode> skippedApprovalRoles(String projectId) {
        Set<ProjectRoleCode> occupied = EnumSet.noneOf(ProjectRoleCode.class);
        for (RoleSlot slot : approvalSlots(projectId)) {
            if (slot.accountId() != null && !slot.accountId().isBlank()) {
                occupied.add(slot.roleCode());
            }
        }
        return APPROVAL_ROLES.stream().filter(role -> !occupied.contains(role)).toList();
    }

    private List<ProjectRoleCode> approvableRolesFor(
            String projectId,
            String accountId,
            List<ProjectRoleCode> requiredRoles) {
        if (accountId == null || accountId.isBlank() || requiredRoles.isEmpty()) {
            return List.of();
        }
        Set<ProjectRoleCode> required = new LinkedHashSet<>(requiredRoles);
        return approvalSlots(projectId).stream()
                .filter(slot -> required.contains(slot.roleCode()))
                .filter(slot -> accountId.equals(slot.accountId()))
                .map(RoleSlot::roleCode)
                .distinct()
                .toList();
    }

    private List<RoleSlot> approvalSlots(String projectId) {
        return organizationAuthorityService.listProjectRoles(projectId).stream()
                .filter(role -> APPROVAL_ROLES.contains(role.roleCode()))
                .map(role -> new RoleSlot(role.roleCode(), role.accountId()))
                .toList();
    }

    private int releaseAmount(OrderEntity order) {
        Integer reservedShares = PostItemSupport.metadataInt(order.metadata(), "reservedShares");
        if (reservedShares != null) {
            return reservedShares;
        }
        return order.settlementAmount() == null ? 0 : order.settlementAmount().intValueExact();
    }

    private int releaseCurveSlot(OrderEntity order, MarketEntity market) {
        Integer reservedCurveSlot = PostItemSupport.metadataInt(order.metadata(), "reservedCurveSlot");
        if (reservedCurveSlot != null) {
            return reservedCurveSlot;
        }
        return isProjectItemOrder(order) ? projectSharePoolService.requireByMarketId(market.id()).nextCurveSlot() : market.nextCurveSlot();
    }

    private String releaseAccountId(OrderEntity order) {
        return order.fulfillerAccountId();
    }

    private boolean requiresSettlementHold(OrderEntity order) {
        return order.settlementType() == SettlementType.SHARES
                && order.proofId() != null
                && !order.isReviewOrder();
    }

    private boolean canMintResolvedRequest(OrderEntity order) {
        // 中文注释：普通任务 shares 必须等争议窗口关闭后 mint；review order 已经是最终验收态，可立即结算原订单。
        return order.status() == com.monopolyfun.modules.order.domain.OrderStatus.FINAL_ACCEPTED
                || order.isReviewOrder();
    }

    private boolean canApproveReleaseRequest(OrderEntity order) {
        return canMintResolvedRequest(order);
    }

    private String issuerProjectId(OrderEntity order, String actorAccountId) {
        if (order.postKind() == PostKind.PROJECT) {
            return order.postId();
        }
        // 中文注释：非 Project 的 shares 订单统一落到 Root Project，系统 shares 与项目 shares 共用同一套审批闸门。
        return rootProjectService.ensureRootProject(actorAccountId).id();
    }

    private Map<String, Object> releaseMetadata(OrderEntity order) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postKind", order.postKind().name().toLowerCase());
        metadata.put("postId", order.postId());
        metadata.put("listingId", order.listingId());
        metadata.put("itemId", PostItemSupport.itemId(order.metadata(), order.listingId()));
        metadata.put("orderKind", order.kind().name().toLowerCase());
        return metadata;
    }

    private Map<String, Object> holdMetadata(OrderEntity order) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("orderNo", order.orderNo());
        metadata.put("postKind", order.postKind() == null ? "" : order.postKind().name().toLowerCase());
        metadata.put("postId", order.postId());
        metadata.put("disputeWindowExpiresAt", order.disputeWindowExpiresAt() == null ? "" : order.disputeWindowExpiresAt().toString());
        return metadata;
    }

    private String resolveReleaseReason(OrderEntity order) {
        if ("accepted_by_review".equalsIgnoreCase(order.closedReason())) {
            return ShareSettlementHoldEntity.RELEASE_REASON_REVIEW_ACCEPTED;
        }
        if ("accepted_by_override".equalsIgnoreCase(order.closedReason())) {
            return ShareSettlementHoldEntity.RELEASE_REASON_OVERRIDE_ACCEPTED;
        }
        if ("dispute_window_expired".equalsIgnoreCase(order.closedReason())) {
            return ShareSettlementHoldEntity.RELEASE_REASON_WINDOW_EXPIRED;
        }
        return ShareSettlementHoldEntity.RELEASE_REASON_DIRECT_FINAL_ACCEPT;
    }

    private List<ProjectRoleCode> mergeRoles(List<ProjectRoleCode> existing, List<ProjectRoleCode> additions) {
        LinkedHashSet<ProjectRoleCode> merged = new LinkedHashSet<>(existing == null ? List.of() : existing);
        merged.addAll(additions == null ? List.of() : additions);
        return APPROVAL_ROLES.stream().filter(merged::contains).toList();
    }

    private MarketEntity requireMarket(String marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found"));
    }

    private OrderEntity requireOrderSnapshot(ShareReleaseRequestEntity request) {
        return orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private boolean isProjectItemOrder(OrderEntity order) {
        return order.postKind() == PostKind.PROJECT && order.kind() != ListingKind.REVIEW;
    }

    private void saveEvent(String orderId, String eventType, String actorAccountId, Map<String, Object> payload) {
        orderEventRepository.save(new OrderEventEntity(
                "evt-" + UUID.randomUUID(),
                orderId,
                eventType,
                actorAccountId,
                payload,
                Instant.now()));
    }

    private record RoleSlot(ProjectRoleCode roleCode, String accountId) {
    }
}
