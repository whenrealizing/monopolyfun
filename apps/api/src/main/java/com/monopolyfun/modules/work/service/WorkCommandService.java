package com.monopolyfun.modules.work.service;

import com.monopolyfun.modules.order.api.request.AcceptOrderRequest;
import com.monopolyfun.modules.order.api.request.AppealOrderRequest;
import com.monopolyfun.modules.order.api.request.AssignReviewerRequest;
import com.monopolyfun.modules.order.api.request.BackofficeOverrideReviewRequest;
import com.monopolyfun.modules.order.api.request.CancelDisputeRequest;
import com.monopolyfun.modules.order.api.request.CloseOrderRequest;
import com.monopolyfun.modules.order.api.request.DisputeOrderRequest;
import com.monopolyfun.modules.order.api.request.SubmitProgressRequest;
import com.monopolyfun.modules.order.api.request.SubmitProofRequest;
import com.monopolyfun.modules.order.domain.ExecutionMode;
import com.monopolyfun.modules.order.domain.ReviewDecision;
import com.monopolyfun.modules.order.service.command.OrderCommandService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.work.api.request.ClaimWorkItemRequest;
import com.monopolyfun.modules.work.api.request.CloseWorkRunRequest;
import com.monopolyfun.modules.work.api.request.RequestWorkHelpRequest;
import com.monopolyfun.modules.work.api.request.ReviewWorkReceiptRequest;
import com.monopolyfun.modules.work.api.request.ReviseWorkReceiptRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkProgressRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkReceiptRequest;
import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkCommerceTrustRepository;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class WorkCommandService {
    private static final Duration CLAIM_LEASE = Duration.ofHours(3);

    private final WorkRepository workRepository;
    private final WorkQueryService workQueryService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrderCommandService orderCommandService;
    private final WorkCommerceTrustRepository commerceTrustRepository;
    private final OrganizationAuthorityService organizationAuthorityService;

    public WorkCommandService(
            WorkRepository workRepository,
            WorkQueryService workQueryService,
            CurrentAccountAccess currentAccountAccess,
            OrderCommandService orderCommandService,
            WorkCommerceTrustRepository commerceTrustRepository,
            OrganizationAuthorityService organizationAuthorityService) {
        this.workRepository = workRepository;
        this.workQueryService = workQueryService;
        this.currentAccountAccess = currentAccountAccess;
        this.orderCommandService = orderCommandService;
        this.commerceTrustRepository = commerceTrustRepository;
        this.organizationAuthorityService = organizationAuthorityService;
    }

    public CommandReceipt claimWorkItem(String itemNoOrId, ClaimWorkItemRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        workRepository.releaseExpiredClaims(Instant.now());
        WorkItemEntity item = workQueryService.requireCurrentAccountWorkItem(itemNoOrId);
        if (!"ready".equals(item.status()) && !"revision_requested".equals(item.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work item cannot be claimed from status " + item.status());
        }
        Instant now = Instant.now();
        WorkRunEntity run = workRepository.saveRun(new WorkRunEntity(
                "wr-" + UUID.randomUUID(),
                "wr-" + item.itemNo(),
                item.id(),
                request.actorAccountId(),
                "claimed",
                normalizeExecutionMode(request.executionMode()),
                now,
                null,
                null,
                now));
        WorkItemEntity claimed = workRepository.saveItem(withStatus(item, "claimed", now));
        if ("complete_money_payment".equals(item.outputSchema().get("action"))) {
            // 中文注释：资金动作从 claim 阶段生成授权记录，后续支付、结算、售后都能沿 WorkRun 回查。
            commerceTrustRepository.savePaymentAuthorization(
                    run,
                    claimed,
                    request.actorAccountId(),
                    "requested",
                    Map.of("itemNo", item.itemNo(), "sourceType", item.sourceType(), "sourceId", item.sourceId()),
                    Map.of("runNo", run.runNo(), "action", "complete_money_payment"));
        }
        saveEvent(claimed, request.actorAccountId(), "work_item_claimed", "claim_work_item",
                Map.of("itemNo", item.itemNo()),
                Map.of("runNo", run.runNo(), "claimExpiresAt", claimed.claimExpiresAt().toString()),
                null);
        return receipt("work_run", run.runNo(), "claimed", request.actorAccountId(),
                Map.of("itemNo", claimed.itemNo(), "runNo", run.runNo(), "claimExpiresAt", claimed.claimExpiresAt().toString()));
    }

    public CommandReceipt submitReceipt(String itemNoOrId, SubmitWorkReceiptRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validateReceiptRequest(request);
        WorkItemEntity item = workQueryService.requireCurrentAccountWorkItem(itemNoOrId);
        WorkRunEntity run = workRepository.findRunByItemAndActor(item.id(), request.actorAccountId())
                .orElseGet(() -> workRepository.saveRun(new WorkRunEntity(
                        "wr-" + UUID.randomUUID(),
                        "wr-" + item.itemNo(),
                        item.id(),
                        request.actorAccountId(),
                        "claimed",
                        "manual",
                        Instant.now(),
                        null,
                        null,
                        Instant.now())));
        if (!List.of("claimed", "running", "revision_requested").contains(run.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work run cannot accept receipt from status " + run.status());
        }
        CommandReceipt sourceReceipt = submitSourceReceiptIfNeeded(item, request);
        Instant now = Instant.now();
        WorkReceiptEntity workReceipt = workRepository.saveReceipt(new WorkReceiptEntity(
                "wrc-" + UUID.randomUUID(),
                "wrc-" + item.itemNo() + "-" + now.toEpochMilli(),
                run.id(),
                request.summary(),
                request.output(),
                request.evidenceRefs(),
                request.traceRefs(),
                request.contentHashes(),
                now));
        WorkRunEntity submittedRun = workRepository.saveRun(new WorkRunEntity(
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
        String itemStatus = sourceReceipt == null ? "submitted" : "closed";
        WorkItemEntity submittedItem = workRepository.saveItem(withStatus(item, itemStatus, now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemNo", submittedItem.itemNo());
        payload.put("runNo", submittedRun.runNo());
        payload.put("receiptNo", workReceipt.receiptNo());
        if (sourceReceipt != null) {
            payload.put("source", sourceReceipt.payload());
        }
        saveEvent(submittedItem, request.actorAccountId(), "work_receipt_submitted", "submit_receipt",
                Map.of("itemNo", item.itemNo(), "sourceType", item.sourceType()),
                Map.of("receiptNo", workReceipt.receiptNo(), "runNo", submittedRun.runNo(), "sourceReceipt", sourceReceipt == null ? Map.of() : sourceReceipt.payload()),
                workReceipt.id());
        return receipt("work_receipt", workReceipt.receiptNo(), sourceReceipt == null ? "submitted" : sourceReceipt.status(), request.actorAccountId(), payload);
    }

    public CommandReceipt submitProgress(String itemNoOrId, SubmitWorkProgressRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validateProgressRequest(request);
        WorkItemEntity item = workQueryService.requireCurrentAccountWorkItem(itemNoOrId);
        WorkRunEntity run = activeRun(item, request.actorAccountId());
        if (!List.of("claimed", "running", "revision_requested").contains(run.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work run cannot accept progress from status " + run.status());
        }
        CommandReceipt sourceReceipt = submitSourceProgressIfNeeded(item, request);
        Instant now = Instant.now();
        WorkReceiptEntity progressReceipt = saveWorkReceipt(
                item,
                run,
                request.summary(),
                Map.of("stepTitle", request.stepTitle(), "progressPayload", request.progressPayload() == null ? Map.of() : request.progressPayload()),
                request.evidenceRefs(),
                List.of("progress:" + request.stepTitle()));
        WorkRunEntity running = workRepository.saveRun(updateRun(run, "running", null, null, now));
        WorkItemEntity updatedItem = workRepository.saveItem(withStatus(item, "claimed", now));
        saveEvent(updatedItem, request.actorAccountId(), "work_progress_submitted", "submit_progress",
                Map.of("itemNo", item.itemNo(), "stepTitle", request.stepTitle()),
                Map.of("runNo", running.runNo(), "receiptNo", progressReceipt.receiptNo(), "source", sourceReceipt == null ? Map.of() : sourceReceipt.payload()),
                progressReceipt.id());
        return receipt("work_progress", progressReceipt.receiptNo(), "running", request.actorAccountId(),
                Map.of("itemNo", item.itemNo(), "runNo", running.runNo(), "receiptNo", progressReceipt.receiptNo()));
    }

    public CommandReceipt requestHelp(String itemNoOrId, RequestWorkHelpRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validateHelpRequest(request);
        WorkItemEntity item = workQueryService.requireCurrentAccountWorkItem(itemNoOrId);
        WorkRunEntity run = activeRun(item, request.actorAccountId());
        Instant now = Instant.now();
        WorkReceiptEntity helpReceipt = saveWorkReceipt(
                item,
                run,
                request.reason(),
                Map.of("helpPayload", request.helpPayload() == null ? Map.of() : request.helpPayload()),
                request.evidenceRefs(),
                List.of("help_request:" + item.itemNo()));
        WorkItemEntity helpItem = new WorkItemEntity(
                "wi-help-" + UUID.randomUUID(),
                "wi-help-" + now.toEpochMilli(),
                "help_request",
                item.itemNo(),
                item.accountId(),
                request.title() == null || request.title().isBlank() ? "请求协助：" + item.title() : request.title().trim(),
                request.reason().trim(),
                List.of("解决阻塞后继续执行 " + item.itemNo()),
                List.of("work_item:" + item.itemNo(), "work_run:" + run.runNo(), "receipt:" + helpReceipt.receiptNo()),
                Map.of("summary", "string", "evidenceRefs", "string[]"),
                "helper",
                item.requiredCapability(),
                "attention",
                "ready",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(helpItem);
        saveEvent(item, request.actorAccountId(), "work_help_requested", "request_help",
                Map.of("itemNo", item.itemNo(), "reason", request.reason()),
                Map.of("helpItemNo", helpItem.itemNo(), "receiptNo", helpReceipt.receiptNo()),
                helpReceipt.id());
        return receipt("work_help_request", helpItem.itemNo(), "ready", request.actorAccountId(),
                Map.of("itemNo", item.itemNo(), "helpItemNo", helpItem.itemNo(), "receiptNo", helpReceipt.receiptNo()));
    }

    public CommandReceipt reviewReceipt(String itemNoOrId, ReviewWorkReceiptRequest request) {
        currentAccountAccess.requireSameAccount(request.reviewerAccountId());
        WorkItemEntity item = requireReviewableWorkItem(itemNoOrId, request.reviewerAccountId());
        WorkRunEntity run = workRepository.findRunByItemId(item.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Work run required before review"));
        if (!"submitted".equals(run.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work run is not submitted");
        }
        String decision = normalizeDecision(request.decision());
        WorkReceiptEntity latestReceipt = workRepository.findLatestReceiptByRunId(run.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Receipt required before review"));
        CommandReceipt sourceReceipt = reviewSourceIfNeeded(item, request, decision);
        Instant now = Instant.now();
        WorkReviewEntity review = workRepository.saveReview(new WorkReviewEntity(
                "wrev-" + UUID.randomUUID(),
                "wrev-" + item.itemNo() + "-" + now.toEpochMilli(),
                run.id(),
                request.reviewerAccountId(),
                reviewStatus(decision),
                decision,
                request.reason().trim(),
                now,
                now));
        WorkRunEntity reviewedRun = workRepository.saveRun(updateRun(run, runStatus(decision), now, "accepted".equals(decision) ? now : null, now));
        WorkItemEntity reviewedItem = workRepository.saveItem(withStatus(item, itemStatus(decision), now));
        persistTrustOutcome(reviewedRun, review, reviewedItem, request.reviewerAccountId(), decision, request.reason());
        saveEvent(reviewedItem, request.reviewerAccountId(), "work_receipt_reviewed", "review_receipt",
                Map.of("itemNo", item.itemNo(), "decision", decision, "reason", request.reason()),
                Map.of("runNo", reviewedRun.runNo(), "reviewNo", review.reviewNo(), "receiptNo", latestReceipt.receiptNo(), "source", sourceReceipt == null ? Map.of() : sourceReceipt.payload()),
                latestReceipt.id());
        return receipt("work_review", review.reviewNo(), review.status(), request.reviewerAccountId(),
                Map.of("itemNo", item.itemNo(), "runNo", reviewedRun.runNo(), "reviewNo", review.reviewNo(), "decision", decision));
    }

    public CommandReceipt reviseReceipt(String itemNoOrId, ReviseWorkReceiptRequest request) {
        return reviewReceipt(itemNoOrId, new ReviewWorkReceiptRequest(
                request.reviewerAccountId(),
                "revision_requested",
                request.reason(),
                request.evidenceRefs()));
    }

    private WorkItemEntity requireReviewableWorkItem(String itemNoOrId, String reviewerAccountId) {
        workRepository.releaseExpiredClaims(Instant.now());
        WorkItemEntity item = workRepository.findItemByNoOrId(itemNoOrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        if (reviewerAccountId.equals(item.accountId()) && !"project_role_task".equals(item.sourceType())) {
            return item;
        }
        if ("project_role_task".equals(item.sourceType()) && "submitted".equals(item.status())) {
            Object projectId = item.outputSchema().get("projectId");
            if (projectId instanceof String id && !id.isBlank()) {
                // 中文注释：授权任务由执行人提交，具备质量管理权限的账号可以跨账号验收。
                organizationAuthorityService.requireProjectCapability(reviewerAccountId, id, ProjectCapability.MARKET_QUALITY_MANAGE);
                return item;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Work item belongs to another account");
    }

    public CommandReceipt cancelOrderDispute(String orderNo, CancelDisputeRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        // 中文注释：争议撤回入口进入 Work API，订单状态机只作为 source adapter 推进业务事实。
        return orderCommandService.cancelDispute(orderNo, request);
    }

    public CommandReceipt submitOrderProof(String orderNo, SubmitProofRequest request) {
        currentAccountAccess.requireSameAccount(request.submittedByAccountId());
        // 中文注释：订单证明写入口挂在 Work API，便于旧业务事实继续由统一执行层驱动。
        return orderCommandService.submitProof(orderNo, request);
    }

    public CommandReceipt submitOrderProgress(String orderNo, SubmitProgressRequest request) {
        currentAccountAccess.requireSameAccount(request.submittedByAccountId());
        return orderCommandService.submitProgress(orderNo, request);
    }

    public CommandReceipt acceptOrder(String orderNo, AcceptOrderRequest request) {
        currentAccountAccess.requireSameAccount(request.acceptedByAccountId());
        return orderCommandService.acceptOrder(orderNo, request);
    }

    public CommandReceipt openOrderDispute(String orderNo, DisputeOrderRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        return orderCommandService.openDispute(orderNo, request);
    }

    public CommandReceipt openOrderAppeal(String orderNo, AppealOrderRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        // 中文注释：二审申请从 Work API 暴露，保持前端写入口集中在执行层。
        return orderCommandService.openAppeal(orderNo, request);
    }

    public CommandReceipt assignOrderReviewer(String orderNo, AssignReviewerRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        return orderCommandService.assignReviewer(orderNo, request);
    }

    public CommandReceipt overrideOrderReview(String orderNo, BackofficeOverrideReviewRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        return orderCommandService.backofficeOverride(orderNo, request);
    }

    public CommandReceipt closeOrder(String orderNo, CloseOrderRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        return orderCommandService.closeOrder(orderNo, request);
    }

    public CommandReceipt abandonOrderPayment(String orderNo, CloseOrderRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        return orderCommandService.abandonPayment(orderNo, request);
    }

    public CommandReceipt closeWorkRun(String itemNoOrId, CloseWorkRunRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        WorkItemEntity item = workQueryService.requireCurrentAccountWorkItem(itemNoOrId);
        WorkRunEntity run = workRepository.findRunByItemId(item.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Work run required before close"));
        Instant now = Instant.now();
        WorkReceiptEntity closeReceipt = saveWorkReceipt(item, run, request.reason(), Map.of("closed", true), List.of(), List.of("close_work_run"));
        WorkRunEntity closedRun = workRepository.saveRun(updateRun(run, "closed", null, null, now));
        WorkItemEntity closedItem = workRepository.saveItem(withStatus(item, "closed", now));
        commerceTrustRepository.saveSettlementRecord(
                closedRun,
                null,
                closedItem,
                request.actorAccountId(),
                "failed",
                Map.of("itemNo", item.itemNo(), "reason", request.reason()),
                Map.of("runNo", closedRun.runNo(), "status", "closed"));
        saveEvent(closedItem, request.actorAccountId(), "work_run_closed", "close_work_run",
                Map.of("itemNo", item.itemNo(), "reason", request.reason()),
                Map.of("runNo", closedRun.runNo(), "receiptNo", closeReceipt.receiptNo()),
                closeReceipt.id());
        return receipt("work_run", closedRun.runNo(), "closed", request.actorAccountId(),
                Map.of("itemNo", closedItem.itemNo(), "runNo", closedRun.runNo(), "receiptNo", closeReceipt.receiptNo()));
    }

    private CommandReceipt submitSourceReceiptIfNeeded(WorkItemEntity item, SubmitWorkReceiptRequest request) {
        if (!"order".equals(item.sourceType())) {
            return null;
        }
        // 中文注释：订单交付与评审证明由 Work 统一收口，再分发到对应订单事实推进器。
        if ("resolve_disputed_order".equals(item.outputSchema().get("action"))) {
            return null;
        }
        if (item.itemNo().startsWith("wb-submit-proof-")) {
            return orderCommandService.submitProof(
                    item.sourceId(),
                    new SubmitProofRequest(
                            request.actorAccountId(),
                            request.summary(),
                            request.links() == null ? List.of() : request.links(),
                            request.artifacts() == null ? List.of() : request.artifacts(),
                            request.output() == null ? Map.of() : request.output(),
                            executionMode(request.sourceReceipt() == null ? null : (String) request.sourceReceipt().get("executionMode")),
                            null,
                            request.agentRuntime(),
                            null,
                            request.evidenceRefs() == null ? List.of() : request.evidenceRefs(),
                            request.contentHashes() == null ? List.of() : request.contentHashes(),
                            List.of(),
                            "participants",
                            null));
        }
        return orderCommandService.submitDeliveryResult(
                item.sourceId(),
                request.actorAccountId(),
                request.summary(),
                request.output(),
                sourceReceiptPayload(item, request),
                request.links(),
                request.artifacts(),
                request.agentRuntime());
    }

    private CommandReceipt submitSourceProgressIfNeeded(WorkItemEntity item, SubmitWorkProgressRequest request) {
        if (!"order".equals(item.sourceType())) {
            return null;
        }
        return orderCommandService.submitProgress(
                item.sourceId(),
                new SubmitProgressRequest(
                        request.actorAccountId(),
                        request.stepTitle(),
                        request.summary(),
                        request.links() == null ? List.of() : request.links(),
                        request.artifacts() == null ? List.of() : request.artifacts(),
                        request.progressPayload() == null ? Map.of() : request.progressPayload(),
                        executionMode(request.executionMode()),
                        request.agentSessionId(),
                        request.agentRuntime()));
    }

    private CommandReceipt reviewSourceIfNeeded(WorkItemEntity item, ReviewWorkReceiptRequest request, String decision) {
        if (!"order".equals(item.sourceType())) {
            return null;
        }
        if ("resolve_disputed_order".equals(item.outputSchema().get("action"))) {
            if ("revision_requested".equals(decision)) {
                // 中文注释：仲裁待办要求补证时只推进 WorkReview 状态，原订单继续停留在争议中等待下一轮证据。
                return null;
            }
            ReviewDecision sourceDecision = "accepted".equals(decision) ? ReviewDecision.ACCEPT_ORIGINAL : ReviewDecision.CLOSE_ORIGINAL;
            return orderCommandService.backofficeOverride(item.sourceId(), new BackofficeOverrideReviewRequest(request.reviewerAccountId(), sourceDecision, request.reason()));
        }
        if ("accepted".equals(decision)) {
            return orderCommandService.acceptOrder(item.sourceId(), new AcceptOrderRequest(request.reviewerAccountId(), request.reason()));
        }
        if ("revision_requested".equals(decision)) {
            return orderCommandService.requestRevision(item.sourceId(), request.reviewerAccountId(), request.reason());
        }
        if ("disputed".equals(decision)) {
            return orderCommandService.openDispute(item.sourceId(), new DisputeOrderRequest(request.reviewerAccountId(), request.reason(), request.evidenceRefs()));
        }
        return null;
    }

    private Map<String, Object> sourceReceiptPayload(WorkItemEntity item, SubmitWorkReceiptRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (request.sourceReceipt() != null) {
            payload.putAll(request.sourceReceipt());
        }
        // 中文注释：业务 receipt 透传调用方证据，同时补充 Work 内核索引，订单验收可回查统一执行事实。
        payload.put("source", "work_receipt");
        payload.put("itemNo", item.itemNo());
        return payload;
    }

    private void persistTrustOutcome(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String decision,
            String reason) {
        Map<String, Object> input = Map.of("itemNo", item.itemNo(), "decision", decision, "reason", reason);
        Map<String, Object> output = Map.of("runNo", run.runNo(), "reviewNo", review.reviewNo(), "status", run.status());
        if ("accepted".equals(decision)) {
            commerceTrustRepository.saveSettlementRecord(run, review, item, actorAccountId, "released", input, output);
            return;
        }
        if ("revision_requested".equals(decision)) {
            commerceTrustRepository.saveAfterSaleCase(run, review, item, actorAccountId, "open", reason, input);
            return;
        }
        if ("disputed".equals(decision)) {
            commerceTrustRepository.saveAfterSaleCase(run, review, item, actorAccountId, "in_review", reason, input);
            commerceTrustRepository.saveArbitrationCase(run, review, item, actorAccountId, "open", reason, input);
        }
    }

    private void validateReceiptRequest(SubmitWorkReceiptRequest request) {
        if (request.summary() == null || request.summary().trim().length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "summary must contain at least 4 characters");
        }
        RequestPayloadLimits.requireTextLength("summary", request.summary(), 1000);
        RequestPayloadLimits.requireMapShape("output", request.output(), 12, 100, 4000);
        RequestPayloadLimits.requireMapShape("sourceReceipt", request.sourceReceipt(), 12, 100, 4000);
        RequestPayloadLimits.requireStringList("evidenceRefs", request.evidenceRefs(), 30, 500);
        RequestPayloadLimits.requireStringList("traceRefs", request.traceRefs(), 30, 500);
        RequestPayloadLimits.requireStringList("contentHashes", request.contentHashes(), 30, 500);
        RequestPayloadLimits.requireProofLinks("links", request.links(), 20, 120, 500);
        RequestPayloadLimits.requireStringList("artifacts", request.artifacts(), 20, 500);
    }

    private void validateProgressRequest(SubmitWorkProgressRequest request) {
        RequestPayloadLimits.requireTextLength("stepTitle", request.stepTitle(), 120);
        RequestPayloadLimits.requireTextLength("summary", request.summary(), 500);
        RequestPayloadLimits.requireMapShape("progressPayload", request.progressPayload(), 4, 80, 2000);
        RequestPayloadLimits.requireProofLinks("links", request.links(), 20, 120, 500);
        RequestPayloadLimits.requireStringList("artifacts", request.artifacts(), 20, 500);
        RequestPayloadLimits.requireTextLength("agentSessionId", request.agentSessionId(), 120);
        RequestPayloadLimits.requireTextLength("agentRuntime", request.agentRuntime(), 120);
    }

    private void validateHelpRequest(RequestWorkHelpRequest request) {
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        RequestPayloadLimits.requireTextLength("title", request.title(), 120);
        RequestPayloadLimits.requireStringList("evidenceRefs", request.evidenceRefs(), 20, 500);
        RequestPayloadLimits.requireMapShape("helpPayload", request.helpPayload(), 4, 80, 2000);
    }

    private WorkRunEntity activeRun(WorkItemEntity item, String actorAccountId) {
        return workRepository.findRunByItemAndActor(item.id(), actorAccountId)
                .orElseGet(() -> workRepository.saveRun(new WorkRunEntity(
                        "wr-" + UUID.randomUUID(),
                        "wr-" + item.itemNo(),
                        item.id(),
                        actorAccountId,
                        "claimed",
                        "manual",
                        Instant.now(),
                        null,
                        null,
                        Instant.now())));
    }

    private WorkReceiptEntity saveWorkReceipt(
            WorkItemEntity item,
            WorkRunEntity run,
            String summary,
            Map<String, Object> output,
            List<String> evidenceRefs,
            List<String> traceRefs) {
        Instant now = Instant.now();
        return workRepository.saveReceipt(new WorkReceiptEntity(
                "wrc-" + UUID.randomUUID(),
                "wrc-" + item.itemNo() + "-" + now.toEpochMilli(),
                run.id(),
                summary.trim(),
                output == null ? Map.of() : output,
                evidenceRefs == null ? List.of() : evidenceRefs,
                traceRefs == null ? List.of() : traceRefs,
                List.of(),
                now));
    }

    private WorkRunEntity updateRun(WorkRunEntity run, String status, Instant submittedAt, Instant acceptedAt, Instant now) {
        return new WorkRunEntity(
                run.id(),
                run.runNo(),
                run.workItemId(),
                run.actorAccountId(),
                status,
                run.executionMode(),
                run.startedAt(),
                submittedAt == null ? run.submittedAt() : submittedAt,
                acceptedAt == null ? run.acceptedAt() : acceptedAt,
                now);
    }

    private String normalizeDecision(String value) {
        String decision = value == null ? "" : value.trim().toLowerCase();
        if (!List.of("accepted", "revision_requested", "disputed").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be accepted, revision_requested, or disputed");
        }
        return decision;
    }

    private String reviewStatus(String decision) {
        return switch (decision) {
            case "accepted" -> "accepted";
            case "revision_requested" -> "revision_requested";
            case "disputed" -> "disputed";
            default -> "pending";
        };
    }

    private String runStatus(String decision) {
        return switch (decision) {
            case "accepted" -> "accepted";
            case "revision_requested" -> "revision_requested";
            case "disputed" -> "disputed";
            default -> "submitted";
        };
    }

    private String itemStatus(String decision) {
        return switch (decision) {
            case "accepted" -> "accepted";
            case "revision_requested" -> "revision_requested";
            case "disputed" -> "disputed";
            default -> "submitted";
        };
    }

    private ExecutionMode executionMode(String value) {
        String mode = value == null || value.isBlank() ? "agent" : value.trim().toLowerCase();
        return switch (mode) {
            case "human" -> ExecutionMode.HUMAN;
            case "mixed" -> ExecutionMode.MIXED;
            default -> ExecutionMode.AGENT;
        };
    }

    private WorkItemEntity withStatus(WorkItemEntity item, String status, Instant now) {
        // 中文注释：claim 是三小时租约，只有 claimed 状态持有执行权，其他状态立即释放租约。
        Instant claimExpiresAt = "claimed".equals(status) ? now.plus(CLAIM_LEASE) : null;
        return new WorkItemEntity(
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
                claimExpiresAt,
                item.readyAt(),
                item.createdAt(),
                now);
    }

    private void saveEvent(
            WorkItemEntity item,
            String actorAccountId,
            String eventType,
            String actionId,
            Map<String, Object> input,
            Map<String, Object> output,
            String receiptId) {
        workRepository.saveEvent(new WorkEventEntity(
                "we-" + UUID.randomUUID(),
                "work_item",
                item.id(),
                actorAccountId,
                eventType,
                actionId,
                input,
                output,
                receiptId,
                Instant.now()));
    }

    private String normalizeExecutionMode(String value) {
        return value == null || value.isBlank() ? "manual" : value.trim();
    }

    private CommandReceipt receipt(String type, String subjectId, String status, String actorAccountId, Map<String, Object> payload) {
        return new CommandReceipt("cmd-" + UUID.randomUUID(), type, subjectId, status, payload, actorAccountId, "work-" + UUID.randomUUID(), null, Instant.now());
    }
}
