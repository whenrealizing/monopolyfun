package com.monopolyfun.modules.order.service.command;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.api.request.AcceptOrderRequest;
import com.monopolyfun.modules.order.api.request.AppealOrderRequest;
import com.monopolyfun.modules.order.api.request.AssignReviewerRequest;
import com.monopolyfun.modules.order.api.request.BackofficeOverrideReviewRequest;
import com.monopolyfun.modules.order.api.request.CancelDisputeRequest;
import com.monopolyfun.modules.order.api.request.CloseOrderRequest;
import com.monopolyfun.modules.order.api.request.DisputeOrderRequest;
import com.monopolyfun.modules.order.api.request.SubmitProgressRequest;
import com.monopolyfun.modules.order.api.request.SubmitProofRequest;
import com.monopolyfun.modules.order.domain.OrderAction;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.domain.OrderProgressUpdateEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.domain.ProofEntity;
import com.monopolyfun.modules.order.domain.ProofKind;
import com.monopolyfun.modules.order.domain.ProofLink;
import com.monopolyfun.modules.order.domain.ReviewDecision;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderProgressUpdateRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.infra.ProofRepository;
import com.monopolyfun.modules.order.service.workflow.OrderWorkflowCatalog;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.service.ProjectLifecycleService;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareSettlementHoldEntity;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.modules.share.service.ShareReleaseService;
import com.monopolyfun.modules.upload.domain.ProofAssetEntity;
import com.monopolyfun.modules.upload.domain.ProofAssetStatus;
import com.monopolyfun.modules.upload.infra.ProofAssetRepository;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.platform.command.CommandKernel;
import com.monopolyfun.platform.command.CommandMetadata;
import com.monopolyfun.platform.command.CommandResult;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.error.ApiStatusException;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandService {
    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    private static final int INITIAL_REWARD = 1000;
    private static final double DECAY = 0.98;
    private static final int MIN_REWARD = 50;
    private static final long DISPUTE_WINDOW_SECONDS = 24L * 60L * 60L;

    private final AccountRepository accountRepository;
    private final MarketRepository marketRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final ProofAssetRepository proofAssetRepository;
    private final ProofRepository proofRepository;
    private final OrderProgressUpdateRepository orderProgressUpdateRepository;
    private final OrderEventRepository orderEventRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final CommandKernel commandKernel;
    private final ProjectLifecycleService projectLifecycleService;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ShareReleaseService shareReleaseService;
    private final ProjectSharePoolService projectSharePoolService;
    private final WorkRepository workRepository;
    private final OrderWorkflowCatalog workflowCatalog;

    public OrderCommandService(
            AccountRepository accountRepository,
            MarketRepository marketRepository,
            ListingRepository listingRepository,
            OrderRepository orderRepository,
            PaymentService paymentService,
            ProofAssetRepository proofAssetRepository,
            ProofRepository proofRepository,
            OrderProgressUpdateRepository orderProgressUpdateRepository,
            OrderEventRepository orderEventRepository,
            CurrentAccountAccess currentAccountAccess,
            CommandKernel commandKernel,
            ProjectLifecycleService projectLifecycleService,
            OrganizationAuthorityService organizationAuthorityService,
            ShareReleaseService shareReleaseService,
            ProjectSharePoolService projectSharePoolService,
            WorkRepository workRepository,
            OrderWorkflowCatalog workflowCatalog) {
        this.accountRepository = accountRepository;
        this.marketRepository = marketRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.proofAssetRepository = proofAssetRepository;
        this.proofRepository = proofRepository;
        this.orderProgressUpdateRepository = orderProgressUpdateRepository;
        this.orderEventRepository = orderEventRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.commandKernel = commandKernel;
        this.projectLifecycleService = projectLifecycleService;
        this.organizationAuthorityService = organizationAuthorityService;
        this.shareReleaseService = shareReleaseService;
        this.projectSharePoolService = projectSharePoolService;
        this.workRepository = workRepository;
        this.workflowCatalog = workflowCatalog;
    }

    public CommandReceipt submitProgress(String orderId, SubmitProgressRequest request) {
        return commandKernel.execute(new CommandMetadata("submit_progress", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.submittedByAccountId());
            validateProgressRequest(request);
            OrderEntity order = requireOrder(orderId);
            workflowCatalog.require(OrderAction.SUBMIT_PROGRESS, order.status(), OrderStatus.CLAIMED);
            if (!request.submittedByAccountId().equals(order.fulfillerAccountId())) {
                throw api(HttpStatus.FORBIDDEN, "order.progress.submitter_not_fulfiller", "Only order fulfiller can submit progress", Map.of("orderNo", order.orderNo()));
            }
            requireMoneyPaymentBeforeFulfillment(order);
            boolean reviewedDeliveryTask = order.isReviewOrder() && "reviewed_delivery".equalsIgnoreCase(String.valueOf(order.metadata().get("fulfillmentMode")));
            ListingEntity listing = order.isReviewOrder() ? null : requireListing(order.listingId());
            if (!reviewedDeliveryTask && (listing == null || !PostItemSupport.SUBJECT_TYPE.equalsIgnoreCase(listing.subjectType()) || !PostItemSupport.isReviewedFulfillment(listing.metadata()))) {
                throw api(HttpStatus.CONFLICT, "order.progress.unsupported", "Order progress is only enabled for reviewed delivery items", Map.of("orderNo", order.orderNo()));
            }

            int nextStepIndex = orderProgressUpdateRepository.findByOrderId(order.id()).size() + 1;
            OrderProgressUpdateEntity progress = new OrderProgressUpdateEntity(
                    "progress-" + UUID.randomUUID(),
                    order.id(),
                    order.listingId(),
                    nextStepIndex,
                    request.stepTitle(),
                    request.summary(),
                    request.links() == null ? List.of() : request.links(),
                    request.artifacts() == null ? List.of() : request.artifacts(),
                    request.progressPayload() == null ? Map.of() : request.progressPayload(),
                    request.submittedByAccountId(),
                    request.executionMode(),
                    request.agentSessionId(),
                    request.agentRuntime(),
                    Instant.now());
            orderProgressUpdateRepository.save(progress);

            Instant now = Instant.now();
            int progressTimeoutSeconds = listing == null
                    ? PostItemSupport.DEFAULT_PROGRESS_TIMEOUT_SECONDS
                    : PostItemSupport.progressTimeoutSeconds(listing.metadata());
            Map<String, Object> nextMetadata = PostItemSupport.withOrderTimingMetadata(
                    order.metadata(),
                    PostItemSupport.metadataInstant(order.metadata(), "lockExpiresAt"),
                    now.plusSeconds(progressTimeoutSeconds),
                    now,
                    nextStepIndex);
            var progressed = order.recordProgress(nextMetadata, lifecycleContext(context, Map.of("progressId", progress.id(), "stepIndex", nextStepIndex)));
            orderRepository.save(progressed.entity());
            saveEvent(progressed.entity().id(), "progress_submitted", request.submittedByAccountId(), Map.of(
                    "progressId", progress.id(),
                    "stepTitle", request.stepTitle(),
                    "label", "任务进度已提交"));
            return new CommandResult(
                    progressed.entity().orderNo(),
                    progressed.entity().status().name(),
                    Map.of("orderNo", progressed.entity().orderNo(), "progressId", progress.id(), "stepIndex", nextStepIndex),
                    List.of(progressed.transition()));
        });
    }

    public CommandReceipt submitProof(String orderId, SubmitProofRequest request) {
        return commandKernel.execute(new CommandMetadata("submit_proof", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.submittedByAccountId());
            validateProofRequest(request);
            OrderEntity order = requireOrder(orderId);
            workflowCatalog.require(OrderAction.SUBMIT_PROOF, order.status(), OrderStatus.DELIVERED);
            if (!request.submittedByAccountId().equals(order.fulfillerAccountId())) {
                throw api(HttpStatus.FORBIDDEN, "order.proof.submitter_not_claimed_account", "Only order fulfiller can submit proof", Map.of("orderNo", order.orderNo()));
            }
            requireMoneyPaymentBeforeFulfillment(order);
            List<ProofLink> links = request.links() == null ? List.of() : request.links();
            List<String> artifacts = request.artifacts() == null ? List.of() : request.artifacts();
            List<String> evidenceRefs = cleanRefs(request.evidenceRefs());
            List<String> criteriaRefs = cleanRefs(request.criteriaRefs());
            if (links.isEmpty() && artifacts.isEmpty() && evidenceRefs.isEmpty()) {
                throw api(HttpStatus.BAD_REQUEST, "order.proof.evidence_required", "Proof requires at least one link or artifact", Map.of("orderNo", order.orderNo()));
            }
            if (criteriaRefs.isEmpty()) {
                throw api(HttpStatus.BAD_REQUEST, "order.proof.criteria_required", "Proof must cite at least one acceptance criterion", Map.of("orderNo", order.orderNo()));
            }
            // 中文注释：上传资产按公开订单号登记，证明提交也用同一编号校验附件归属。
            List<ProofAssetEntity> registeredAssets = validateRegisteredArtifacts(order, artifacts);
            Map<String, Object> rawProofPayload = request.proofPayload() == null ? Map.of() : request.proofPayload();
            validateProjectCodeProof(order, links, artifacts, registeredAssets, rawProofPayload);
            Map<String, Object> proofPayload = withEvidenceSnapshot(
                    rawProofPayload,
                    order,
                    links,
                    registeredAssets,
                    request.executionTraceRef(),
                    context.startedAt());
            List<String> contentHashes = buildContentHashes(request.contentHashes(), links, registeredAssets, proofPayload);

            ProofKind proofKind = order.kind() == ListingKind.REVIEW ? ProofKind.REVIEW_PROOF : ProofKind.WORK_PROOF;
            if (proofKind == ProofKind.REVIEW_PROOF && request.decision() == null) {
                throw api(HttpStatus.BAD_REQUEST, "order.review_proof.decision_required", "Review proof requires a decision", Map.of("orderNo", order.orderNo()));
            }
            String proofId = "proof-" + UUID.randomUUID();
            ProofEntity proof = new ProofEntity(proofId, order.id(), proofKind, order.parentOrderId(),
                    request.submittedByAccountId(), request.summary(), links, artifacts,
                    proofPayload, request.executionMode(),
                    request.agentSessionId(), request.agentRuntime(), request.decision(), evidenceRefs, contentHashes,
                    criteriaRefs, normalizeVisibility(request.visibility()), normalizeOptionalText(request.executionTraceRef(), 500), Instant.now());
            proofRepository.save(proof);
            if (proofKind == ProofKind.REVIEW_PROOF) {
                if (order.parentOrderId() != null) {
                    // 中文注释：旧 review order 只保留订单状态回写，争议裁决的新事实来源已经迁到 WorkReview。
                    OrderEntity original = requireInternalOrder(order.parentOrderId());
                    orderRepository.save(original.markReviewSubmitted(lifecycleContext(context, Map.of("reviewOrderId", order.id()))).entity());
                }
            }
            var delivered = order.submitProof(proof.id(), request.submittedByAccountId(), lifecycleContext(context, Map.of("proofId", proof.id())));
            orderRepository.save(delivered.entity());
            saveEvent(delivered.entity().id(), proofKind == ProofKind.REVIEW_PROOF ? "review_proof_submitted" : "proof_submitted",
                    request.submittedByAccountId(), Map.of("proofId", proof.id(), "traceId", context.traceId()));
            return new CommandResult(
                    delivered.entity().orderNo(),
                    delivered.entity().status().name(),
                    Map.of("orderNo", delivered.entity().orderNo(), "proofId", proof.id()),
                    List.of(delivered.transition()));
        });
    }

    public CommandReceipt submitDeliveryResult(
            String orderId,
            String actorAccountId,
            String deliverySummary,
            Map<String, Object> deliveryPayload,
            Map<String, Object> deliveryReceipt,
            List<ProofLink> links,
            List<String> artifacts,
            String agentRuntime) {
        return commandKernel.execute(new CommandMetadata("submit_delivery_result", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(actorAccountId);
            OrderEntity order = requireOrder(orderId);
            workflowCatalog.require(OrderAction.SUBMIT_DELIVERY_RESULT, order.status(), OrderStatus.DELIVERED);
            if (!"reviewed_delivery".equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")))) {
                throw api(HttpStatus.CONFLICT, "order.delivery.reviewed_required", "Order is not configured for reviewed delivery", Map.of("orderNo", order.orderNo()));
            }
            String fulfillerAccountId = deliveryResultFulfiller(order);
            if (!actorAccountId.equals(fulfillerAccountId)) {
                throw api(HttpStatus.FORBIDDEN, "order.delivery.submitter_not_fulfiller", "Only order fulfiller can submit delivery result", Map.of("orderNo", order.orderNo()));
            }
            requireMoneyPaymentBeforeFulfillment(order);
            List<ProofLink> deliveryLinks = links == null ? List.of() : List.copyOf(links);
            List<String> deliveryArtifacts = cleanRefs(artifacts);
            List<ProofAssetEntity> registeredAssets = validateRegisteredArtifacts(order, deliveryArtifacts);

            // 中文注释：交付结果保存结构化证据快照，订单验收和争议读取同一份可复核交付事实。
            LinkedHashMap<String, Object> nextDeliverySnapshot = new LinkedHashMap<>(order.deliverySnapshot());
            nextDeliverySnapshot.put("deliverySummary", deliverySummary);
            nextDeliverySnapshot.put("deliveryPayload", deliveryPayload == null ? Map.of() : deliveryPayload);
            nextDeliverySnapshot.put("deliveryReceipt", deliveryReceipt == null ? Map.of() : deliveryReceipt);
            nextDeliverySnapshot.put("links", deliveryLinks);
            nextDeliverySnapshot.put("artifacts", deliveryArtifacts);
            nextDeliverySnapshot.put("evidenceSnapshot", evidenceSnapshot(order, deliveryLinks, registeredAssets, null, context.startedAt()));
            nextDeliverySnapshot.put("contentHashes", buildContentHashes(List.of(), deliveryLinks, registeredAssets, nextDeliverySnapshot));
            if (agentRuntime != null && !agentRuntime.isBlank()) {
                nextDeliverySnapshot.put("agentRuntimeId", agentRuntime.trim());
            }
            LinkedHashMap<String, Object> nextMetadata = new LinkedHashMap<>(order.metadata());
            nextMetadata.put("deliveryPayload", deliveryPayload == null ? Map.of() : deliveryPayload);
            nextMetadata.put("deliveryReceipt", deliveryReceipt == null ? Map.of() : deliveryReceipt);
            nextMetadata.put("deliveryLinks", deliveryLinks);
            nextMetadata.put("deliveryArtifacts", deliveryArtifacts);
            nextMetadata.put("deliveryCompletedAt", Instant.now().toString());
            nextMetadata.put("deliveryAttemptCount", deliveryAttemptCount(order.metadata()) + 1);
            if (agentRuntime != null && !agentRuntime.isBlank()) {
                nextMetadata.put("agentRuntimeId", agentRuntime.trim());
            }

            var delivered = order.submitDeliveryResult(
                    actorAccountId,
                    nextDeliverySnapshot,
                    nextMetadata,
                    lifecycleContext(context, Map.of("deliveryMode", "reviewed_delivery", "deliverySource", String.valueOf(order.metadata().get("deliverySource")))));
            orderRepository.save(delivered.entity());
            reopenLeadReviewItemsForDelivery(delivered.entity(), context.startedAt());
            saveEvent(delivered.entity().id(), "delivery_result_submitted", actorAccountId, Map.of(
                    "deliverySource", String.valueOf(order.metadata().get("deliverySource")),
                    "agentRuntimeId", agentRuntime == null ? "" : agentRuntime,
                    "linkCount", deliveryLinks.size(),
                    "artifactCount", deliveryArtifacts.size(),
                    "label", "交付结果已写入订单"));
            return new CommandResult(
                    delivered.entity().orderNo(),
                    delivered.entity().status().name(),
                    Map.of("orderNo", delivered.entity().orderNo(), "deliveryMode", "reviewed_delivery"),
                    List.of(delivered.transition()));
        });
    }

    private void validateProgressRequest(SubmitProgressRequest request) {
        // 中文注释：进度更新会进入订单事件和 workbench 投影，先限制文本和结构化 payload 边界。
        RequestPayloadLimits.requireTextLength("stepTitle", request.stepTitle(), 120);
        RequestPayloadLimits.requireTextLength("summary", request.summary(), 500);
        RequestPayloadLimits.requireProofLinks("links", request.links(), 20, 120, 500);
        RequestPayloadLimits.requireStringList("artifacts", request.artifacts(), 20, 500);
        RequestPayloadLimits.requireMapShape("progressPayload", request.progressPayload(), 4, 80, 2000);
        RequestPayloadLimits.requireTextLength("agentSessionId", request.agentSessionId(), 120);
        RequestPayloadLimits.requireTextLength("agentRuntime", request.agentRuntime(), 120);
    }

    private void validateProofRequest(SubmitProofRequest request) {
        // 中文注释：证明数据会长期参与验收、争议和审计，保存前统一卡住超大 JSON 与引用列表。
        RequestPayloadLimits.requireTextLength("summary", request.summary(), 500);
        RequestPayloadLimits.requireProofLinks("links", request.links(), 20, 120, 500);
        RequestPayloadLimits.requireStringList("artifacts", request.artifacts(), 20, 500);
        RequestPayloadLimits.requireStringList("evidenceRefs", request.evidenceRefs(), 20, 500);
        RequestPayloadLimits.requireStringList("contentHashes", request.contentHashes(), 20, 128);
        RequestPayloadLimits.requireStringList("criteriaRefs", request.criteriaRefs(), 20, 500);
        RequestPayloadLimits.requireMapShape("proofPayload", request.proofPayload(), 4, 80, 2000);
        RequestPayloadLimits.requireTextLength("agentSessionId", request.agentSessionId(), 120);
        RequestPayloadLimits.requireTextLength("agentRuntime", request.agentRuntime(), 120);
        RequestPayloadLimits.requireTextLength("visibility", request.visibility(), 40);
        RequestPayloadLimits.requireTextLength("executionTraceRef", request.executionTraceRef(), 500);
    }

    public CommandReceipt acceptOrder(String orderId, AcceptOrderRequest request) {
        return commandKernel.execute(new CommandMetadata("accept_order", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.acceptedByAccountId());
            OrderEntity order = requireOrder(orderId);
            MarketEntity market = requireMarket(order.marketId());
            requireOrderAcceptanceCapability(order, request.acceptedByAccountId(), "Order acceptance capability required");
            workflowCatalog.require(OrderAction.ACCEPT, order.status(), order.isReviewOrder() ? OrderStatus.FINAL_ACCEPTED : OrderStatus.ACCEPTED_OPEN);
            if (order.settlementFrozen() && order.kind() != ListingKind.REVIEW) {
                throw api(HttpStatus.CONFLICT, "order.settlement.frozen", "Disputed order settlement is frozen until review resolves it", Map.of("orderNo", order.orderNo()));
            }

            var accepted = order.isReviewOrder()
                    ? order.finalizeAccepted(request.acceptedByAccountId(), "accepted", lifecycleContext(context, Map.of("note", request.note() == null ? "" : request.note())))
                    : order.openAcceptanceWindow(
                    request.acceptedByAccountId(),
                    "accepted",
                    Instant.now().plusSeconds(DISPUTE_WINDOW_SECONDS),
                    lifecycleContext(context, Map.of("note", request.note() == null ? "" : request.note())));
            if (order.settlementType() == SettlementType.MONEY) {
                var paymentIntent = paymentService.requireSettledMoneyPayment(order.id());
                accepted = order.isReviewOrder()
                        ? accepted.entity().finalizeAccepted(request.acceptedByAccountId(), "accepted", lifecycleContext(context, Map.of(
                        "note", request.note() == null ? "" : request.note(),
                        "paymentIntentId", paymentIntent.id(),
                        "paymentStatus", paymentIntent.status().name())))
                        : accepted;
            }
            orderRepository.save(accepted.entity());
            if (order.postKind() == PostKind.PROJECT) {
                projectLifecycleService.touchOwnerAction(order.postId(), request.acceptedByAccountId(), "accept_project_order");
            }
            saveEvent(accepted.entity().id(), "order_accepted", request.acceptedByAccountId(),
                    Map.of("note", request.note() == null ? "" : request.note(), "traceId", context.traceId()));
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
            transitions.add(accepted.transition());
            if (order.isReviewOrder()) {
                shareReleaseService.requestRelease(accepted.entity(), request.acceptedByAccountId());
                transitions.addAll(resolveOriginalIfReview(accepted.entity(), context));
            } else {
                shareReleaseService.lockOrderSettlement(accepted.entity(), request.acceptedByAccountId(), ShareSettlementHoldEntity.LOCK_REASON_ACCEPTANCE_WINDOW);
                // 中文注释：验收窗口打开时先生成 release request，协议维护和结算维护可提前审批，真正 mint 仍等窗口结束或评审终局。
                shareReleaseService.requestRelease(accepted.entity(), request.acceptedByAccountId());
                releaseListingCapacity(accepted.entity(), context, false).ifPresent(transitions::add);
                closeCompletedProjectItemListing(accepted.entity(), context).ifPresent(transitions::add);
            }
            return new CommandResult(
                    accepted.entity().orderNo(),
                    accepted.entity().status().name(),
                    Map.of("orderNo", accepted.entity().orderNo(), "settlementAmount", accepted.entity().effectiveSettlementAmount() == null ? 0 : accepted.entity().effectiveSettlementAmount()),
                    transitions);
        });
    }

    public CommandReceipt requestRevision(String orderId, String actorAccountId, String reason) {
        return commandKernel.execute(new CommandMetadata("request_order_revision", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(actorAccountId);
            OrderEntity order = requireOrder(orderId);
            requireOrderAcceptanceCapability(order, actorAccountId, "Order revision capability required");
            workflowCatalog.require(OrderAction.REQUEST_REVISION, order.status(), OrderStatus.CLAIMED);
            String normalizedReason = reason == null || reason.isBlank() ? "需要补充交付结果" : reason.trim();
            var revised = order.requestRevision(actorAccountId, normalizedReason, lifecycleContext(context, Map.of("reason", normalizedReason)));
            orderRepository.save(revised.entity());
            reopenDeliveryWorkItemForRevision(revised.entity(), normalizedReason, context.startedAt());
            if (order.postKind() == PostKind.PROJECT) {
                projectLifecycleService.touchOwnerAction(order.postId(), actorAccountId, "request_project_order_revision");
            }
            saveEvent(revised.entity().id(), "order_revision_requested", actorAccountId,
                    Map.of("reason", normalizedReason, "traceId", context.traceId()));
            return new CommandResult(
                    revised.entity().orderNo(),
                    revised.entity().status().name(),
                    Map.of("orderNo", revised.entity().orderNo(), "reason", normalizedReason),
                    List.of(revised.transition()));
        });
    }

    public CommandReceipt openDispute(String orderId, DisputeOrderRequest request) {
        return commandKernel.execute(new CommandMetadata("open_dispute", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireAccount(request.actorAccountId());
            MarketEntity market = requireMarket(order.marketId());
            if (!canOpenDispute(order, request.actorAccountId())) {
                throw api(HttpStatus.FORBIDDEN, "order.dispute.forbidden", "Order participant or dispute capability required", Map.of("orderNo", order.orderNo()));
            }
            if (order.status() == OrderStatus.ACCEPTED_OPEN && !order.hasOpenDisputeWindow(Instant.now())) {
                throw api(HttpStatus.CONFLICT, "order.dispute.window_closed", "Accepted order dispute window is closed", Map.of("orderNo", order.orderNo()));
            }
            workflowCatalog.require(OrderAction.OPEN_DISPUTE, order.status(), OrderStatus.DISPUTED);

            Instant now = Instant.now();
            String reviewerAccountId = selectDisputeReviewer(order, market);
            String reviewItemNo = createDisputeReviewWorkItem(order, reviewerAccountId, request.actorAccountId(), "review", cleanRefs(request.evidenceRefs()), now);
            var disputed = order.openDispute(request.reason(), reviewItemNo, reviewerAccountId, lifecycleContext(context, Map.of(
                    "reason", request.reason(),
                    "reviewItemNo", reviewItemNo,
                    "reviewerAccountId", reviewerAccountId)));
            orderRepository.save(disputed.entity());
            shareReleaseService.freezeOrderSettlement(disputed.entity(), request.actorAccountId());
            if (order.postKind() == PostKind.PROJECT && organizationAuthorityService.hasProjectCapability(request.actorAccountId(), order.postId(), ProjectCapability.ORDER_DISPUTE_RESOLVE)) {
                projectLifecycleService.touchOwnerAction(order.postId(), request.actorAccountId(), "dispute_project_order");
            }
            saveEvent(disputed.entity().id(), "order_disputed", request.actorAccountId(),
                    Map.of("reason", request.reason(), "reviewerAccountId", reviewerAccountId, "traceId", context.traceId()));
            saveEvent(disputed.entity().id(), "work_review_opened", request.actorAccountId(),
                    Map.of("reviewItemNo", reviewItemNo, "traceId", context.traceId()));
            saveEvent(disputed.entity().id(), "review_workbench_published", request.actorAccountId(),
                    Map.of("assignmentMode", "work_item", "traceId", context.traceId()));
            return new CommandResult(
                    disputed.entity().orderNo(),
                    disputed.entity().status().name(),
                    Map.of("orderNo", disputed.entity().orderNo(), "reviewItemNo", reviewItemNo),
                    List.of(disputed.transition()));
        });
    }

    public CommandReceipt cancelDispute(String orderId, CancelDisputeRequest request) {
        return commandKernel.execute(new CommandMetadata("cancel_dispute", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireAccount(request.actorAccountId());
            if (!request.actorAccountId().equals(order.disputeOpenedByAccountId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only dispute opener can cancel dispute");
            }
            if (order.disputeOpenedFromStatus() != OrderStatus.DELIVERED && order.disputeOpenedFromStatus() != OrderStatus.ACCEPTED_OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispute origin state required before cancellation");
            }
            workflowCatalog.require(OrderAction.CANCEL_DISPUTE, order.status(), order.disputeOpenedFromStatus());
            Instant now = Instant.now();
            closeDisputeReviewWorkItems(order.orderNo(), "closed", now);

            // 中文注释：撤回争议只恢复原流程，验收动作仍由用户下一步显式触发。
            var cancelled = order.cancelDispute(request.actorAccountId(), request.reason(), lifecycleContext(context, Map.of(
                    "reason", request.reason(),
                    "restoredStatus", order.disputeOpenedFromStatus().name())));
            orderRepository.save(cancelled.entity());
            if (cancelled.entity().status() == OrderStatus.ACCEPTED_OPEN && cancelled.entity().settlementType() == SettlementType.SHARES) {
                shareReleaseService.lockOrderSettlement(cancelled.entity(), request.actorAccountId(), ShareSettlementHoldEntity.LOCK_REASON_ACCEPTANCE_WINDOW);
            }
            saveEvent(cancelled.entity().id(), "dispute_cancelled", request.actorAccountId(), Map.of(
                    "reason", request.reason(),
                    "restoredStatus", cancelled.entity().status().name(),
                    "traceId", context.traceId()));
            return new CommandResult(
                    cancelled.entity().orderNo(),
                    cancelled.entity().status().name(),
                    Map.of("orderNo", cancelled.entity().orderNo(), "restoredStatus", cancelled.entity().status().name()),
                    List.of(cancelled.transition()));
        });
    }

    public CommandReceipt openAppeal(String orderId, AppealOrderRequest request) {
        return commandKernel.execute(new CommandMetadata("open_appeal", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireAccount(request.actorAccountId());
            MarketEntity market = requireMarket(order.marketId());
            if (!order.hasParticipant(request.actorAccountId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order participant required");
            }
            workflowCatalog.require(OrderAction.OPEN_APPEAL, order.status(), OrderStatus.DISPUTED);
            if (OrderEntity.REVIEW_STATUS_APPEAL_OPEN.equalsIgnoreCase(order.reviewStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order appeal already opened");
            }
            Instant now = Instant.now();
            String reviewerAccountId = selectDisputeReviewer(order, market);
            String reviewItemNo = createDisputeReviewWorkItem(order, reviewerAccountId, request.actorAccountId(), "appeal", List.of("appeal:" + request.reason()), now);

            // 中文注释：二审重新推送到 Work Review 待办，订单只保存可追踪的 WorkItem 编号。
            var appealed = order.replaceReviewer(
                    reviewItemNo,
                    reviewerAccountId,
                    null,
                    OrderEntity.REVIEW_STATUS_APPEAL_OPEN,
                    lifecycleContext(context, Map.of("reason", request.reason(), "reviewItemNo", reviewItemNo)));
            orderRepository.save(appealed.entity());

            saveEvent(appealed.entity().id(), "appeal_opened", request.actorAccountId(), Map.of(
                    "reason", request.reason(),
                    "traceId", context.traceId()));
            saveEvent(appealed.entity().id(), "work_review_opened", request.actorAccountId(),
                    Map.of("reviewItemNo", reviewItemNo, "appeal", true, "traceId", context.traceId()));
            saveEvent(appealed.entity().id(), "review_workbench_published", request.actorAccountId(),
                    Map.of("assignmentMode", "work_item", "traceId", context.traceId()));
            return new CommandResult(
                    appealed.entity().orderNo(),
                    appealed.entity().status().name(),
                    Map.of("orderNo", appealed.entity().orderNo(), "reviewItemNo", reviewItemNo),
                    List.of(appealed.transition()));
        });
    }

    public int reassignExpiredReviewers() {
        Instant now = Instant.now();
        int reassignedCount = 0;
        for (OrderEntity order : orderRepository.findDisputed(500)) {
            if (order.reviewDueAt() == null || order.reviewDueAt().isAfter(now)) {
                continue;
            }
            try {
                if (reassignExpiredReviewerWorkItem(order, now)) {
                    reassignedCount++;
                }
            } catch (ResponseStatusException exception) {
                log.warn("Skipped reviewer timeout reassignment for order {}: {}", order.orderNo(), exception.getReason());
            }
        }
        return reassignedCount;
    }

    private boolean reassignExpiredReviewerWorkItem(OrderEntity order, Instant now) {
        MarketEntity market = requireMarket(order.marketId());
        closeDisputeReviewWorkItems(order.orderNo(), "closed", now);
        String reviewItemNo = createDisputeReviewWorkItem(order, market.leadAccountId(), market.leadAccountId(), "timeout_reassign", List.of("timeout:" + order.orderNo()), now);
        String nextReviewStatus = OrderEntity.REVIEW_STATUS_APPEAL_OPEN.equalsIgnoreCase(order.reviewStatus())
                ? OrderEntity.REVIEW_STATUS_APPEAL_OPEN
                : OrderEntity.REVIEW_STATUS_OPEN;
        var reassigned = order.replaceReviewer(
                reviewItemNo,
                null,
                null,
                nextReviewStatus,
                new LifecycleContext(market.leadAccountId(), "scheduler-review-timeout", now, Map.of(
                        "previousReviewerAccountId", order.reviewerAccountId() == null ? "" : order.reviewerAccountId(),
                        "reviewItemNo", reviewItemNo)));
        orderRepository.save(reassigned.entity());
        saveEvent(reassigned.entity().id(), "review_timeout_reopened", market.leadAccountId(), Map.of(
                "previousReviewerAccountId", order.reviewerAccountId() == null ? "" : order.reviewerAccountId(),
                "reviewItemNo", reviewItemNo));
        return true;
    }

    private void reopenLeadReviewItemsForDelivery(OrderEntity order, Instant now) {
        String itemNo = "wb-lead-review-" + order.orderNo();
        boolean refreshed = false;
        for (WorkItemEntity item : workRepository.findItemsBySource("order", order.orderNo())) {
            if (!itemNo.equals(item.itemNo())) {
                continue;
            }
            refreshed = true;
            if ("submitted".equals(item.status())) {
                continue;
            }
            // 中文注释：返工后二次交付会复用 owner 的验收待办，重新置为 submitted 才能再次裁决。
            workRepository.saveItem(new WorkItemEntity(
                    item.id(),
                    item.itemNo(),
                    item.sourceType(),
                    item.sourceId(),
                    item.accountId(),
                    item.title(),
                    item.goal(),
                    item.acceptanceCriteria(),
                    item.inputRefs(),
                    item.outputSchema(),
                    item.requiredRole(),
                    item.requiredCapability(),
                    item.urgency(),
                    "submitted",
                    null,
                    item.readyAt(),
                    item.createdAt(),
                    now));
            WorkRunEntity run = workRepository.findRunByItemId(item.id())
                    .orElseGet(() -> new WorkRunEntity(
                            "wr-" + UUID.randomUUID(),
                            "wr-" + item.itemNo(),
                            item.id(),
                            item.accountId(),
                            "submitted",
                            "manual",
                            now,
                            now,
                            null,
                            now));
            workRepository.saveRun(new WorkRunEntity(
                    run.id(),
                    run.runNo(),
                    run.workItemId(),
                    run.actorAccountId(),
                    "submitted",
                    run.executionMode(),
                    run.startedAt(),
                    now,
                    null,
                    now));
        }
        if (refreshed) {
            return;
        }
        // 中文注释：首次交付直接写负责人验收 WorkItem，Workbench 读取当前 Work 投影即可展示待验收订单。
        WorkItemEntity item = new WorkItemEntity(
                "wi-" + order.acceptorAccountId() + "-" + itemNo,
                itemNo,
                "order",
                order.orderNo(),
                order.acceptorAccountId(),
                "负责人验收或争议",
                "订单已交付，等待负责人验收、争议或结算。",
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                Map.of("action", "lead_accept_or_dispute", "decision", "accepted|revision_requested|disputed"),
                OrderEntity.ROLE_PAYER,
                null,
                "urgent",
                "submitted",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(item);
        WorkRunEntity run = workRepository.saveRun(new WorkRunEntity(
                "wr-" + UUID.randomUUID(),
                "wr-" + itemNo,
                item.id(),
                order.acceptorAccountId(),
                "submitted",
                "manual",
                now,
                now,
                null,
                now));
        workRepository.saveReceipt(new WorkReceiptEntity(
                "wrc-" + UUID.randomUUID(),
                "wrc-" + itemNo + "-" + now.toEpochMilli(),
                run.id(),
                "订单交付结果等待验收",
                Map.of("source", "order_delivery", "orderNo", order.orderNo()),
                List.of("order:" + order.orderNo()),
                List.of("review:" + itemNo),
                List.of(),
                now));
    }

    private void reopenDeliveryWorkItemForRevision(OrderEntity order, String reason, Instant now) {
        String itemNo = "wb-delivery-result-" + order.orderNo();
        String itemId = "wi-" + order.fulfillerAccountId() + "-" + itemNo;
        // 中文注释：返工决定同时重开执行方待办和 WorkRun，确保同一订单可再次提交新的 PR proof。
        WorkItemEntity item = new WorkItemEntity(
                itemId,
                itemNo,
                "order",
                order.orderNo(),
                order.fulfillerAccountId(),
                "提交修订后的交付结果",
                reason,
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                Map.of("action", "delivery_result_due", "summary", "string", "links", "ProofLink[]", "artifacts", "string[]"),
                "fulfiller",
                null,
                "urgent",
                "revision_requested",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(item);
        WorkRunEntity run = workRepository.findRunByItemAndActor(itemId, order.fulfillerAccountId())
                .orElseGet(() -> new WorkRunEntity(
                        "wr-" + UUID.randomUUID(),
                        "wr-" + itemNo,
                        itemId,
                        order.fulfillerAccountId(),
                        "claimed",
                        "manual",
                        now,
                        null,
                        null,
                        now));
        workRepository.saveRun(new WorkRunEntity(
                run.id(),
                run.runNo(),
                run.workItemId(),
                run.actorAccountId(),
                "revision_requested",
                run.executionMode(),
                run.startedAt(),
                run.submittedAt(),
                null,
                now));
    }

    public CommandReceipt closeOrder(String orderId, CloseOrderRequest request) {
        return commandKernel.execute(new CommandMetadata("close_order", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireAccount(request.actorAccountId());
            requireMarket(order.marketId());
            requireDisputeResolveCapability(order, request.actorAccountId(), "Order dispute resolve capability required");
            if (order.isFinalStatus()) {
                throw api(HttpStatus.CONFLICT, "order.already_terminal", "Order already terminal", Map.of("orderNo", order.orderNo(), "status", order.status().name()));
            }
            workflowCatalog.require(OrderAction.CLOSE, order.status(), OrderStatus.FINAL_CLOSED);
            var closed = order.finalizeClosed(request.actorAccountId(), request.reason(), lifecycleContext(context, Map.of("reason", request.reason())));
            orderRepository.save(closed.entity());
            workRepository.closeOpenItemsBySource("order", closed.entity().orderNo(), "order_closed");
            shareReleaseService.cancelOrderSettlement(closed.entity(), request.actorAccountId(), request.reason());
            if (order.postKind() == PostKind.PROJECT) {
                projectLifecycleService.touchOwnerAction(order.postId(), request.actorAccountId(), "close_project_order");
            }
            releaseReservedSharesIfNeeded(closed.entity());
            paymentService.refundOrderPaymentIfPresent(closed.entity(), request.actorAccountId(), request.reason());
            saveEvent(closed.entity().id(), "order_closed", request.actorAccountId(),
                    Map.of("reason", request.reason(), "traceId", context.traceId()));
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
            transitions.add(closed.transition());
            if (order.status() == OrderStatus.CLAIMED || order.acceptedByAccountId() == null) {
                if (closed.entity().kind() != ListingKind.REVIEW) {
                    releaseListingCapacity(closed.entity(), context, false).ifPresent(transitions::add);
                }
            }
            closeCompletedProjectItemListing(closed.entity(), context).ifPresent(transitions::add);
            return new CommandResult(
                    closed.entity().orderNo(),
                    closed.entity().status().name(),
                    Map.of("orderNo", closed.entity().orderNo(), "reason", request.reason()),
                    transitions);
        });
    }

    public CommandReceipt abandonPayment(String orderId, CloseOrderRequest request) {
        return commandKernel.execute(new CommandMetadata("abandon_payment", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireAccount(request.actorAccountId());
            requireMarket(order.marketId());
            if (!request.actorAccountId().equals(order.buyerAccountId())) {
                throw api(HttpStatus.FORBIDDEN, "order.payment_abandon.forbidden", "Only order payer can abandon payment", Map.of("orderNo", order.orderNo()));
            }
            if (order.settlementType() != SettlementType.MONEY) {
                throw api(HttpStatus.CONFLICT, "order.payment_abandon.not_money_order", "Only money orders can abandon payment", Map.of("orderNo", order.orderNo()));
            }
            if (order.status() != OrderStatus.CLAIMED) {
                throw api(HttpStatus.CONFLICT, "order.payment_abandon.invalid_state", "Order cannot abandon payment in current state", Map.of("orderNo", order.orderNo(), "status", order.status().name()));
            }
            if (paymentService.hasCapturedPayment(order.id())) {
                throw api(HttpStatus.CONFLICT, "order.payment_abandon.already_paid", "Captured payment cannot be abandoned", Map.of("orderNo", order.orderNo()));
            }
            String reason = request.reason() == null || request.reason().isBlank() ? "abandoned_payment" : request.reason();
            var closed = order.finalizeClosed(request.actorAccountId(), reason, lifecycleContext(context, Map.of("reason", reason)));
            orderRepository.save(closed.entity());
            workRepository.closeOpenItemsBySource("order", closed.entity().orderNo(), "payment_abandoned");
            shareReleaseService.cancelOrderSettlement(closed.entity(), request.actorAccountId(), reason);
            releaseReservedSharesIfNeeded(closed.entity());
            paymentService.refundOrderPaymentIfPresent(closed.entity(), request.actorAccountId(), reason);
            saveEvent(closed.entity().id(), "order_closed", request.actorAccountId(),
                    Map.of("reason", reason, "traceId", context.traceId()));
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
            transitions.add(closed.transition());
            if (closed.entity().kind() != ListingKind.REVIEW) {
                releaseListingCapacity(closed.entity(), context, false).ifPresent(transitions::add);
            }
            return new CommandResult(
                    closed.entity().orderNo(),
                    closed.entity().status().name(),
                    Map.of("orderNo", closed.entity().orderNo(), "reason", reason),
                    transitions);
        });
    }

    public CommandReceipt assignReviewer(String orderId, AssignReviewerRequest request) {
        return commandKernel.execute(new CommandMetadata("assign_reviewer", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireMarket(order.marketId());
            requireDisputeResolveCapability(order, request.actorAccountId(), "Order dispute resolve capability required");
            requireAccount(request.reviewerAccountId());
            organizationAuthorityService.requireReviewerCandidate(order, request.reviewerAccountId());
            workflowCatalog.require(OrderAction.ASSIGN_REVIEWER, order.status(), OrderStatus.DISPUTED);
            Instant dueAt = request.reviewDueAt() == null || request.reviewDueAt().isBlank() ? null : Instant.parse(request.reviewDueAt());
            closeDisputeReviewWorkItems(order.orderNo(), "closed", context.startedAt());
            String reviewItemNo = createDisputeReviewWorkItem(order, request.reviewerAccountId(), request.actorAccountId(), "assigned", List.of("assigned_by:" + request.actorAccountId()), context.startedAt());
            var assigned = order.assignReviewer(request.reviewerAccountId(), dueAt, lifecycleContext(context, Map.of("reviewerAccountId", request.reviewerAccountId())));
            assigned = assigned.entity().replaceReviewer(
                    reviewItemNo,
                    request.reviewerAccountId(),
                    dueAt,
                    assigned.entity().reviewStatus(),
                    lifecycleContext(context, Map.of("reviewItemNo", reviewItemNo, "reviewerAccountId", request.reviewerAccountId())));
            orderRepository.save(assigned.entity());
            saveEvent(assigned.entity().id(), "reviewer_assigned", request.actorAccountId(), Map.of("reviewerAccountId", request.reviewerAccountId(), "reviewItemNo", reviewItemNo, "traceId", context.traceId()));
            return new CommandResult(
                    assigned.entity().orderNo(),
                    assigned.entity().status().name(),
                    Map.of("orderNo", assigned.entity().orderNo(), "reviewerAccountId", request.reviewerAccountId(), "reviewItemNo", reviewItemNo),
                    List.of(assigned.transition()));
        });
    }

    public CommandReceipt backofficeOverride(String orderId, BackofficeOverrideReviewRequest request) {
        return commandKernel.execute(new CommandMetadata("backoffice_override_review", "order", orderId), context -> {
            currentAccountAccess.requireSameAccount(request.actorAccountId());
            OrderEntity order = requireOrder(orderId);
            requireMarket(order.marketId());
            requireDisputeResolveCapability(order, request.actorAccountId(), "Order dispute resolve capability required");
            workflowCatalog.require(OrderAction.OVERRIDE_REVIEW, order.status(), OrderStatus.DISPUTED);
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
            var overrideMarker = order.backofficeOverride(request.decision(), request.reason(), request.actorAccountId(), lifecycleContext(context, Map.of("decision", request.decision().name())));
            orderRepository.save(overrideMarker.entity());
            transitions.add(overrideMarker.transition());
            if (request.decision() == ReviewDecision.ACCEPT_ORIGINAL) {
                if (order.settlementType() == SettlementType.MONEY) {
                    paymentService.requireSettledMoneyPayment(order.id());
                }
                workflowCatalog.require(OrderAction.ACCEPT, overrideMarker.entity().status(), OrderStatus.FINAL_ACCEPTED);
                var accepted = overrideMarker.entity().finalizeAccepted(request.actorAccountId(), "accepted_by_override", lifecycleContext(context, Map.of("reason", request.reason())));
                orderRepository.save(accepted.entity());
                workRepository.closeOpenItemsBySource("order", accepted.entity().orderNo(), "order_final_accepted");
                transitions.add(accepted.transition());
                shareReleaseService.releaseOrderSettlement(accepted.entity(), request.actorAccountId(), ShareSettlementHoldEntity.RELEASE_REASON_OVERRIDE_ACCEPTED);
                if (order.acceptedByAccountId() == null) {
                    releaseListingCapacity(accepted.entity(), context, false).ifPresent(transitions::add);
                }
                closeCompletedProjectItemListing(accepted.entity(), context).ifPresent(transitions::add);
            } else {
                workflowCatalog.require(OrderAction.CLOSE, overrideMarker.entity().status(), OrderStatus.FINAL_CLOSED);
                var closed = overrideMarker.entity().finalizeClosed(request.actorAccountId(), "closed_by_override", lifecycleContext(context, Map.of("reason", request.reason())));
                orderRepository.save(closed.entity());
                workRepository.closeOpenItemsBySource("order", closed.entity().orderNo(), "order_final_closed");
                shareReleaseService.cancelOrderSettlement(closed.entity(), request.actorAccountId(), "closed_by_override");
                releaseReservedSharesIfNeeded(closed.entity());
                paymentService.refundOrderPaymentIfPresent(closed.entity(), request.actorAccountId(), request.reason());
                transitions.add(closed.transition());
                if (order.acceptedByAccountId() == null) {
                    releaseListingCapacity(closed.entity(), context, false).ifPresent(transitions::add);
                }
                closeCompletedProjectItemListing(closed.entity(), context).ifPresent(transitions::add);
            }
            saveEvent(order.id(), "backoffice_override_applied", request.actorAccountId(), Map.of("decision", request.decision().name(), "reason", request.reason(), "traceId", context.traceId()));
            // 中文注释：高权限裁决已经给出最终结论，相关 WorkReview 待办同步关闭，避免执行队列读到过期待办。
            closeDisputeReviewWorkItems(order.orderNo(), request.decision() == ReviewDecision.ACCEPT_ORIGINAL ? "accepted" : "closed", context.startedAt());
            return new CommandResult(
                    order.orderNo(),
                    orderRepository.findById(order.id()).orElseThrow().status().name(),
                    Map.of("orderNo", order.orderNo(), "decision", request.decision().name()),
                    transitions);
        });
    }

    private List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> resolveOriginalIfReview(
            OrderEntity reviewOrder,
            com.monopolyfun.platform.command.CommandContext commandContext) {
        List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
        if (reviewOrder.kind() != ListingKind.REVIEW || reviewOrder.parentOrderId() == null || reviewOrder.proofId() == null) {
            return transitions;
        }
        ProofEntity proof = proofRepository.findById(reviewOrder.proofId()).orElse(null);
        if (proof == null || proof.decision() == null) {
            return transitions;
        }
        OrderEntity original = requireInternalOrder(reviewOrder.parentOrderId());
        if (proof.decision() == ReviewDecision.ACCEPT_ORIGINAL) {
            if (original.settlementType() == SettlementType.MONEY) {
                paymentService.requireSettledMoneyPayment(original.id());
            }
            workflowCatalog.require(OrderAction.ACCEPT, original.status(), OrderStatus.FINAL_ACCEPTED);
            var acceptedOriginal = original.finalizeAccepted(reviewOrder.acceptedByAccountId(), "accepted_by_review",
                    lifecycleContext(commandContext, Map.of("resolvedByReviewOrderId", reviewOrder.id())));
            orderRepository.save(acceptedOriginal.entity());
            shareReleaseService.releaseOrderSettlement(acceptedOriginal.entity(), reviewOrder.acceptedByAccountId(), ShareSettlementHoldEntity.RELEASE_REASON_REVIEW_ACCEPTED);
            if (original.acceptedByAccountId() == null) {
                releaseListingCapacity(acceptedOriginal.entity(), commandContext, false).ifPresent(transitions::add);
            }
            closeCompletedProjectItemListing(acceptedOriginal.entity(), commandContext).ifPresent(transitions::add);
            saveEvent(acceptedOriginal.entity().id(), "order_accepted", reviewOrder.acceptedByAccountId(),
                    Map.of("resolvedByReviewOrderId", reviewOrder.id(), "decision", proof.decision().name()));
            transitions.add(acceptedOriginal.transition());
        } else {
            workflowCatalog.require(OrderAction.CLOSE, original.status(), OrderStatus.FINAL_CLOSED);
            var closedOriginal = original.finalizeClosed(reviewOrder.acceptedByAccountId(), "closed_by_review",
                    lifecycleContext(commandContext, Map.of("resolvedByReviewOrderId", reviewOrder.id())));
            orderRepository.save(closedOriginal.entity());
            shareReleaseService.cancelOrderSettlement(closedOriginal.entity(), reviewOrder.acceptedByAccountId(), "closed_by_review");
            releaseReservedSharesIfNeeded(closedOriginal.entity());
            paymentService.refundOrderPaymentIfPresent(closedOriginal.entity(), reviewOrder.acceptedByAccountId(), "closed_by_review");
            if (original.acceptedByAccountId() == null) {
                releaseListingCapacity(closedOriginal.entity(), commandContext, false).ifPresent(transitions::add);
            }
            closeCompletedProjectItemListing(closedOriginal.entity(), commandContext).ifPresent(transitions::add);
            saveEvent(closedOriginal.entity().id(), "order_closed", reviewOrder.acceptedByAccountId(),
                    Map.of("resolvedByReviewOrderId", reviewOrder.id(), "decision", proof.decision().name()));
            transitions.add(closedOriginal.transition());
        }
        return transitions;
    }

    private void releaseReservedSharesIfNeeded(OrderEntity order) {
        if (!isProjectItemOrder(order)) {
            return;
        }
        Integer reservedShares = PostItemSupport.metadataInt(order.metadata(), "reservedShares");
        if (reservedShares == null || reservedShares <= 0) {
            return;
        }
        // 中文注释：取消项目任务订单时释放 task_reserved，shares 池是财务状态唯一来源。
        projectSharePoolService.releaseTaskReservation(order.marketId(), reservedShares);
    }

    private int projectedReward(String marketId, ListingKind kind) {
        int slot = requireMarket(marketId).nextCurveSlot();
        int workReward = Math.max(MIN_REWARD, (int) Math.floor(INITIAL_REWARD * Math.pow(DECAY, slot)));
        return kind == ListingKind.REVIEW ? Math.min(50, (int) Math.floor(workReward * 0.05)) : workReward;
    }

    private int projectedSettlementAmount(ListingEntity listing) {
        if (listing.settlementType() == SettlementType.MONEY) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(listing.settlementSpec());
            if (!matcher.find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Money listing settlementSpec must include an amount");
            }
            return Integer.parseInt(matcher.group(1));
        }
        return listing.kind() == ListingKind.REVIEW ? 50 : projectedReward(listing.marketId(), listing.kind());
    }

    private OrderEntity requireOrder(String orderId) {
        // 中文注释：控制器入口只接受用户可见订单编号，内部 UUID 关系读取走独立方法。
        return orderRepository.findByOrderNo(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderEntity requireInternalOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private ListingEntity requireListing(String listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private MarketEntity requireMarket(String marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found"));
    }

    private boolean canOpenDispute(OrderEntity order, String actorAccountId) {
        if (order.hasParticipant(actorAccountId)) {
            return true;
        }
        return organizationAuthorityService.canResolveOrderDispute(actorAccountId, order);
    }

    private void requireOrderAcceptanceCapability(OrderEntity order, String actorAccountId, String failureMessage) {
        // 中文注释：验收方来自订单参与方快照，避免 offer 场景把买家误当成交付方。
        if (!actorAccountId.equals(order.acceptorAccountId()) && !organizationAuthorityService.canReviewOrder(actorAccountId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, failureMessage);
        }
    }

    private void requireDisputeResolveCapability(OrderEntity order, String actorAccountId, String failureMessage) {
        if (!organizationAuthorityService.canResolveOrderDispute(actorAccountId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, failureMessage);
        }
    }

    private String deliveryResultFulfiller(OrderEntity order) {
        return order.fulfillerAccountId();
    }

    private int deliveryAttemptCount(Map<String, Object> metadata) {
        Integer value = PostItemSupport.metadataInt(metadata, "deliveryAttemptCount");
        return value == null ? 0 : value;
    }

    public List<AccountEntity> listReviewerCandidates(String orderId) {
        String accountId = currentAccountAccess.requireAccountId();
        OrderEntity order = requireOrder(orderId);
        if (!order.hasParticipant(accountId) && !organizationAuthorityService.canResolveOrderDispute(accountId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order participant required");
        }
        return organizationAuthorityService.listReviewerCandidates(order);
    }

    private String selectDisputeReviewer(OrderEntity order, MarketEntity market) {
        // 中文注释：争议默认交给具备评审能力且远离交易双方的账号，降低买卖双方直接裁决的治理风险。
        return organizationAuthorityService.listReviewerCandidates(order).stream()
                .map(AccountEntity::id)
                .filter(candidate -> !candidate.equals(order.buyerAccountId()))
                .filter(candidate -> !candidate.equals(order.fulfillerAccountId()))
                .findFirst()
                .orElse(market.leadAccountId());
    }

    private String createDisputeReviewWorkItem(OrderEntity order, String reviewerAccountId, String actorAccountId, String reviewKind, List<String> evidenceRefs, Instant now) {
        String itemNo = ("appeal".equals(reviewKind) ? "wb-review-appeal-" : "wb-review-open-") + order.orderNo();
        // 中文注释：争议评审写入 Work 内核，订单仅保留 WorkItem 编号，裁决结果由 WorkReview 回写原订单。
        workRepository.upsertItem(new WorkItemEntity(
                "wi-" + reviewerAccountId + "-" + itemNo,
                itemNo,
                "order",
                order.orderNo(),
                reviewerAccountId,
                "appeal".equals(reviewKind) ? "处理订单二审" : "处理订单争议评审",
                "检查原订单交付、证明和争议材料，给出 accept_original 或 close_original 裁决。",
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                Map.of(
                        "action", "resolve_disputed_order",
                        "decision", "accepted|revision_requested|disputed",
                        "evidenceRefs", evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs),
                        "openedByAccountId", actorAccountId,
                        "reviewKind", reviewKind),
                OrderEntity.ROLE_REVIEWER,
                ProjectCapability.ORDER_REVIEW.code(),
                "urgent",
                "ready",
                null,
                now,
                now,
                now));
        return itemNo;
    }

    private void closeDisputeReviewWorkItems(String orderNo, String status, Instant now) {
        for (WorkItemEntity item : workRepository.findItemsBySource("order", orderNo)) {
            if (!"resolve_disputed_order".equals(item.outputSchema().get("action"))) {
                continue;
            }
            workRepository.saveItem(new WorkItemEntity(
                    item.id(),
                    item.itemNo(),
                    item.sourceType(),
                    item.sourceId(),
                    item.accountId(),
                    item.title(),
                    item.goal(),
                    item.acceptanceCriteria(),
                    item.inputRefs(),
                    item.outputSchema(),
                    item.requiredRole(),
                    item.requiredCapability(),
                    item.urgency(),
                    status,
                    null,
                    item.readyAt(),
                    item.createdAt(),
                    now));
        }
    }

    private void requireMoneyPaymentBeforeFulfillment(OrderEntity order) {
        if (order.settlementType() != SettlementType.MONEY) {
            return;
        }
        // 中文注释：现金订单先完成资金授权或捕获，再允许执行人提交进度或交付证明，避免无付款订单进入交付链路。
        paymentService.requireSettledMoneyPayment(order.id());
    }

    private List<String> cleanRefs(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeVisibility(String value) {
        if (value == null || value.isBlank()) {
            return "public";
        }
        String normalized = value.trim().toLowerCase();
        if (!List.of("public", "private", "reviewer_only").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported proof visibility");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isBlank()) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private void requireAccount(String accountId) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
    }

    private void saveEvent(String orderId, String eventType, String actorAccountId, Map<String, Object> payload) {
        orderEventRepository.save(new OrderEventEntity("evt-" + UUID.randomUUID(), orderId, eventType, actorAccountId,
                payload, Instant.now()));
    }

    private List<ProofAssetEntity> validateRegisteredArtifacts(OrderEntity order, List<String> artifacts) {
        List<ProofAssetEntity> registered = new ArrayList<>();
        for (String artifactRef : artifacts) {
            if (artifactRef == null || !artifactRef.startsWith("asset://")) {
                continue;
            }
            // 中文注释：历史上传入口按 orderNo 写 proof_assets.order_id，新路径也兼容内部 order.id。
            var asset = proofAssetRepository.findByOrderIdAndArtifactRef(order.orderNo(), artifactRef)
                    .or(() -> proofAssetRepository.findByOrderIdAndArtifactRef(order.id(), artifactRef))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact must be registered before proof submission"));
            if (asset.status() != ProofAssetStatus.UPLOADED && asset.status() != ProofAssetStatus.VERIFIED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact upload is not complete");
            }
            registered.add(asset);
        }
        return registered;
    }

    private void validateProjectCodeProof(
            OrderEntity order,
            List<ProofLink> links,
            List<String> artifacts,
            List<ProofAssetEntity> registeredAssets,
            Map<String, Object> proofPayload) {
        if (!isProjectCodeProof(order, links, proofPayload)) {
            return;
        }
        // 中文注释：project 代码交付进入股份和治理流程，后端强制绑定 PR、CI 与 artifact 证据。
        if (artifacts == null || artifacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof requires a registered artifact");
        }
        for (String artifact : artifacts) {
            if (artifact == null || !artifact.startsWith("asset://")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof artifacts must use asset:// refs");
            }
        }
        if (registeredAssets.size() != artifacts.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof requires all artifacts to be registered");
        }

        Map<String, Object> security = securityEvidence(proofPayload);
        String repoUrl = firstNonBlank(securityText(security, "repoUrl", "repositoryUrl"), gitRepoLink(links));
        String prUrl = firstNonBlank(securityText(security, "prUrl", "pullRequestUrl"), gitPrLink(links));
        String commitSha = securityText(security, "commitSha", "headSha", "commit");
        String ciStatus = securityText(security, "ciStatus", "ciResult", "ciConclusion");
        String securityPolicyResult = securityText(security, "securityPolicyResult", "prSecurityPolicyResult", "securityPolicyStatus");
        String orderNo = securityText(security, "orderNo", "boundOrderNo");

        requireProjectCodeText(repoUrl, "Project code proof requires repoUrl");
        requireProjectCodeText(firstNonBlank(prUrl, commitSha), "Project code proof requires prUrl or commitSha");
        requireProjectCodeText(commitSha, "Project code proof requires commitSha");
        requirePassingOrNotRequiredStatus(ciStatus, "Project code proof requires passing CI");
        requirePassingStatus(securityPolicyResult, "Project code proof requires pnpm security:pr-policy passed");
        if (!order.orderNo().equals(orderNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof orderNo mismatch");
        }
        requireNoSecurityFindings(security);
        rejectHighRiskProjectCodeProof(links, proofPayload);
    }

    private boolean isProjectCodeProof(OrderEntity order, List<ProofLink> links, Map<String, Object> proofPayload) {
        if (order.postKind() != PostKind.PROJECT || order.kind() == ListingKind.REVIEW) {
            return false;
        }
        String deliveryType = stringValue(proofPayload.get("deliveryType"));
        if (deliveryType != null && List.of("code_pr", "code", "project_code", "project_code_pr").contains(deliveryType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (proofPayload.containsKey("prSecurity") || proofPayload.containsKey("securityPolicyResult")) {
            return true;
        }
        return links != null && links.stream().anyMatch(this::isGitCodeLink);
    }

    private Map<String, Object> securityEvidence(Map<String, Object> proofPayload) {
        Map<String, Object> prSecurity = mapValue(proofPayload.get("prSecurity"));
        if (!prSecurity.isEmpty()) {
            return prSecurity;
        }
        Map<String, Object> security = mapValue(proofPayload.get("security"));
        return security.isEmpty() ? proofPayload : security;
    }

    private void requireProjectCodeText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requirePassingStatus(String value, String message) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("pass", "passed", "success", "succeeded", "green", "completed").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requirePassingOrNotRequiredStatus(String value, String message) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        // 中文注释：CI 缺省是平台仓库的合法状态，安全策略仍由 PR 绑定和 security policy 证据兜底。
        if (!Set.of("pass", "passed", "success", "succeeded", "green", "completed", "not_required").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requireNoSecurityFindings(Map<String, Object> security) {
        Object findings = security.get("maliciousFindings");
        if (findings instanceof List<?> list && !list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof has unresolved security findings");
        }
        Object unresolved = security.get("unresolvedFindings");
        if (unresolved instanceof List<?> list && !list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof has unresolved security findings");
        }
    }

    private void rejectHighRiskProjectCodeProof(List<ProofLink> links, Map<String, Object> proofPayload) {
        String text = (links == null ? "" : links.toString()) + "\n" + flattenJson(proofPayload);
        String normalized = text.toLowerCase(Locale.ROOT);
        Map<String, String> blockers = Map.ofEntries(
                Map.entry("pull_request_target", "pull_request_target"),
                Map.entry("permissions:" + " write-all", "permissions write-all"),
                Map.entry("curl" + " | " + "sh", "remote script execution"),
                Map.entry("wget" + " | " + "sh", "remote script execution"),
                Map.entry("eval" + "(", "dynamic code execution"),
                Map.entry("new function", "dynamic code execution"),
                Map.entry("child_process.exec", "shell execution"),
                Map.entry("child_process.execsync", "shell execution"),
                Map.entry("runtime.exec", "shell execution"),
                Map.entry("processbuilder", "shell execution"),
                Map.entry("private_key", "secret exposure"),
                Map.entry("mnemonic", "secret exposure"),
                Map.entry("seed_phrase", "secret exposure"),
                Map.entry("okx_onchain_pay_api_secret", "secret exposure"));
        for (Map.Entry<String, String> blocker : blockers.entrySet()) {
            if (normalized.contains(blocker.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project code proof blocked by " + blocker.getValue());
            }
        }
    }

    private String flattenJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> String.valueOf(entry.getKey()) + "=" + flattenJson(entry.getValue()))
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::flattenJson).reduce("", (left, right) -> left + "\n" + right);
        }
        return String.valueOf(value);
    }

    private String securityText(Map<String, Object> security, String... keys) {
        for (String key : keys) {
            String value = stringValue(security.get(key));
            if (value != null) {
                return value;
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

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                mapped.put(key, entry.getValue());
            }
        }
        return Map.copyOf(mapped);
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private boolean isGitCodeLink(ProofLink link) {
        String href = link == null ? null : link.href();
        if (href == null) {
            return false;
        }
        String normalized = href.toLowerCase(Locale.ROOT);
        return (normalized.startsWith("http://") || normalized.startsWith("https://"))
                && (normalized.contains("/pull/") || normalized.contains("/pulls/") || normalized.contains("/commit/"));
    }

    private String gitPrLink(List<ProofLink> links) {
        String pulls = gitLink(links, "/pulls/");
        return pulls == null ? gitLink(links, "/pull/") : pulls;
    }

    private String gitRepoLink(List<ProofLink> links) {
        String href = gitPrLink(links);
        if (href == null) {
            href = gitLink(links, "/commit/");
            if (href == null) {
                return null;
            }
        }
        int pullIndex = href.indexOf("/pull/");
        int pullsIndex = href.indexOf("/pulls/");
        int commitIndex = href.indexOf("/commit/");
        int endIndex = firstPositive(pullIndex, pullsIndex, commitIndex);
        return endIndex > 0 ? href.substring(0, endIndex) : href;
    }

    private int firstPositive(int... values) {
        int first = -1;
        for (int value : values) {
            if (value > 0 && (first < 0 || value < first)) {
                first = value;
            }
        }
        return first;
    }

    private String gitLink(List<ProofLink> links, String marker) {
        if (links == null) {
            return null;
        }
        return links.stream()
                .filter(link -> link != null && link.href() != null)
                .map(link -> link.href().trim())
                .filter(href -> href.toLowerCase(Locale.ROOT).contains(marker))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> withEvidenceSnapshot(
            Map<String, Object> proofPayload,
            OrderEntity order,
            List<ProofLink> links,
            List<ProofAssetEntity> assets,
            String executionTraceRef,
            Instant capturedAt) {
        LinkedHashMap<String, Object> next = new LinkedHashMap<>(proofPayload == null ? Map.of() : proofPayload);
        next.put("evidenceSnapshot", evidenceSnapshot(order, links, assets, executionTraceRef, capturedAt));
        next.put("contentHashes", buildContentHashes(List.of(), links, assets, next));
        return Map.copyOf(next);
    }

    private Map<String, Object> evidenceSnapshot(
            OrderEntity order,
            List<ProofLink> links,
            List<ProofAssetEntity> assets,
            String executionTraceRef,
            Instant capturedAt) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderNo", order.orderNo());
        snapshot.put("challengeNonce", order.challengeNonce());
        snapshot.put("acceptanceCriteria", order.acceptanceCriteriaSnapshot());
        snapshot.put("proofSpec", order.proofSpecSnapshot());
        snapshot.put("capturedAt", capturedAt.toString());
        if (executionTraceRef != null && !executionTraceRef.isBlank()) {
            snapshot.put("executionTraceRef", executionTraceRef.trim());
        }
        snapshot.put("links", snapshotLinks(links, capturedAt));
        snapshot.put("artifacts", assets.stream().map(asset -> {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("artifactRef", asset.artifactRef());
            item.put("filename", asset.filename());
            item.put("contentType", asset.contentType());
            item.put("contentLengthBytes", asset.contentLengthBytes());
            item.put("checksumSha256", asset.checksumSha256());
            item.put("status", asset.status().name().toLowerCase());
            item.put("capturedAt", capturedAt.toString());
            return item;
        }).toList());
        return Map.copyOf(snapshot);
    }

    private List<Map<String, Object>> snapshotLinks(List<ProofLink> links, Instant capturedAt) {
        if (links == null) {
            return List.of();
        }
        return links.stream()
                .filter(link -> link != null && link.href() != null && !link.href().isBlank())
                .<Map<String, Object>>map(link -> {
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    String href = link.href().trim();
                    item.put("label", link.label() == null ? href : link.label().trim());
                    item.put("href", href);
                    item.put("capturedAt", capturedAt.toString());
                    item.put("snapshotHash", sha256Hex(href));
                    return item;
                })
                .toList();
    }

    private List<String> buildContentHashes(
            List<String> providedHashes,
            List<ProofLink> links,
            List<ProofAssetEntity> assets,
            Map<String, Object> payload) {
        Set<String> hashes = new HashSet<>(cleanRefs(providedHashes));
        for (ProofAssetEntity asset : assets) {
            if (asset.checksumSha256() != null && !asset.checksumSha256().isBlank()) {
                hashes.add(asset.checksumSha256().toLowerCase());
            }
        }
        if (links != null) {
            for (ProofLink link : links) {
                if (link != null && link.href() != null && !link.href().isBlank()) {
                    hashes.add(sha256Hex(link.href().trim()));
                }
            }
        }
        if (payload != null && !payload.isEmpty()) {
            hashes.add(sha256Hex(String.valueOf(payload)));
        }
        return hashes.stream().sorted().toList();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash evidence snapshot", exception);
        }
    }

    private java.util.Optional<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> releaseListingCapacity(
            OrderEntity order,
            com.monopolyfun.platform.command.CommandContext commandContext,
            boolean closeListingWhenDrained) {
        ListingEntity listing = listingRepository.findById(order.listingId()).orElse(null);
        if (listing == null || listing.activeOrdersCount() == 0) {
            return java.util.Optional.empty();
        }
        ListingEntity nextListing = listing.withActiveOrdersCount(listing.activeOrdersCount() - 1);
        if (closeListingWhenDrained) {
            var closed = nextListing.close(lifecycleContext(commandContext, Map.of("listingId", listing.id(), "reason", "terminal_review_order")));
            listingRepository.save(closed.entity());
            return java.util.Optional.of(closed.transition());
        }
        if (listing.status() == ListingStatus.PAUSED && nextListing.hasAvailableCapacity()) {
            var reopened = nextListing.reopen(lifecycleContext(commandContext, Map.of("listingId", listing.id(), "reason", "capacity_released")));
            listingRepository.save(reopened.entity());
            return java.util.Optional.of(reopened.transition());
        }
        listingRepository.save(nextListing);
        return java.util.Optional.empty();
    }

    private java.util.Optional<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> closeCompletedProjectItemListing(
            OrderEntity order,
            com.monopolyfun.platform.command.CommandContext commandContext) {
        if (!isProjectItemOrder(order)) {
            return java.util.Optional.empty();
        }
        ListingEntity listing = listingRepository.findById(order.listingId()).orElse(null);
        if (listing == null || listing.status() == ListingStatus.CLOSED || listing.activeOrdersCount() > 0) {
            return java.util.Optional.empty();
        }
        var closed = listing.close(lifecycleContext(commandContext, Map.of("listingId", listing.id(), "reason", order.status().name().toLowerCase())));
        listingRepository.save(closed.entity());
        return java.util.Optional.of(closed.transition());
    }

    private boolean isProjectItemOrder(OrderEntity order) {
        return order.postKind() == PostKind.PROJECT && order.metadata().get("itemId") != null;
    }

    public int finalizeExpiredDisputeWindows() {
        Instant now = Instant.now();
        int finalizedCount = 0;
        for (OrderEntity order : orderRepository.findExpiredDisputeWindows(now, 500)) {
            if (order.status() != OrderStatus.ACCEPTED_OPEN || !OrderEntity.DISPUTE_WINDOW_OPEN.equalsIgnoreCase(order.disputeWindowStatus())) {
                continue;
            }
            workflowCatalog.require(OrderAction.ACCEPT, order.status(), OrderStatus.FINAL_ACCEPTED);
            var finalized = order.finalizeAccepted(order.acceptedByAccountId(), "dispute_window_expired",
                    new LifecycleContext(order.acceptorAccountId(), "scheduler-dispute-window", now, Map.of("reason", "dispute_window_expired")));
            orderRepository.save(finalized.entity());
            workRepository.closeOpenItemsBySource("order", finalized.entity().orderNo(), "dispute_window_expired");
            shareReleaseService.releaseOrderSettlement(finalized.entity(), finalized.entity().acceptorAccountId(), ShareSettlementHoldEntity.RELEASE_REASON_WINDOW_EXPIRED);
            saveEvent(finalized.entity().id(), "order_finalized", finalized.entity().acceptorAccountId(),
                    Map.of("reason", "dispute_window_expired"));
            finalizedCount++;
        }
        return finalizedCount;
    }

    private LifecycleContext lifecycleContext(com.monopolyfun.platform.command.CommandContext context, Map<String, Object> metadata) {
        return new LifecycleContext(context.actorAccountId(), context.traceId(), Instant.now(), metadata);
    }

    private ApiStatusException api(HttpStatus status, String code, String message, Map<String, Object> context) {
        return new ApiStatusException(status, code, message, context);
    }
}
