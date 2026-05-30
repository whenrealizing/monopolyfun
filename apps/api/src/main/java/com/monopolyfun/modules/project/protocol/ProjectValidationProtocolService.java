package com.monopolyfun.modules.project.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.UrlHealthCheckService;
import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.service.ProjectContributionSettlementService;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateFeedbackRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateLaunchRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateProofRequestRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateTaskRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.FeedbackView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.LaunchView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofRequestDraft;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofRequestView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofValidationStatsView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ResolveFeedbackRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ReviewProofRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ReviewQueueItemView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.RewardView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.SettleLaunchRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.SubmitProofRequest;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.TaskView;
import static com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.UpdateLaunchRequest;

@Service
@Transactional
public class ProjectValidationProtocolService {
    private static final TypeReference<List<Map<String, Object>>> JSON_LIST_OF_MAPS = new TypeReference<>() {
    };
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_LIVE = "live";
    private static final String STATUS_REVIEWING = "reviewing";
    private static final String STATUS_SETTLED = "settled";
    private static final String SOURCE_TASK = "project_validation_task";
    private static final String SOURCE_FEEDBACK = "project_validation_feedback";
    private static final String VALIDATION_MODE_ORDINARY = "ordinary";
    private static final String VALIDATION_MODE_STAKED = "staked";
    private static final BigDecimal DEFAULT_MIN_EFFECTIVE_VALIDATORS = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_SHARES_PER_EFFECTIVE_VALIDATOR = new BigDecimal("1000");
    private static final BigDecimal VALIDATOR_REWARD_WEIGHT = new BigDecimal("0.5");
    private static final int DEFAULT_MIN_PARTICIPANTS = 1;

    private final DSLContext dsl;
    private final ProjectRepository projectRepository;
    private final UrlHealthCheckService urlHealthCheckService;
    private final ProjectSharePoolService projectSharePoolService;
    private final ProjectContributionSettlementService contributionSettlementService;

    public ProjectValidationProtocolService(
            DSLContext dsl,
            ProjectRepository projectRepository,
            UrlHealthCheckService urlHealthCheckService,
            ProjectSharePoolService projectSharePoolService,
            ProjectContributionSettlementService contributionSettlementService) {
        this.dsl = dsl;
        this.projectRepository = projectRepository;
        this.urlHealthCheckService = urlHealthCheckService;
        this.projectSharePoolService = projectSharePoolService;
        this.contributionSettlementService = contributionSettlementService;
    }

    public List<LaunchView> listLaunches(String projectNo) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select * from project_validations
                        where project_id = ?
                        order by created_at desc
                        """, projectId)
                .fetch(this::mapLaunch);
    }

    public LaunchView createLaunch(String projectNo, String actorAccountId, CreateLaunchRequest request) {
        String projectId = requireProjectId(projectNo);
        String launchId = "launch_" + UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
        metadata.put("proofRequests", proofRequestDrafts(launchId, actorAccountId, request.proofRequests()));
        // 中文注释：Validation 批次只保留协议级元数据，后续 task/proof/review 全部进入 Work 内核。
        dsl.query("""
                                insert into project_validations (
                                  id, project_id, title, hypothesis, status, parent_launch_id, source_refs, metadata, created_by_account_id
                                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                                """,
                        launchId,
                        projectId,
                        request.title().trim(),
                        request.hypothesis().trim(),
                        STATUS_DRAFT,
                        blankToNull(request.parentLaunchId()),
                        jsonList(request.sourceRefs()).data(),
                        PostgresJson.jsonb(metadata).data(),
                        actorAccountId)
                .execute();
        appendEvent(projectId, launchId, "launch_created", "launch", launchId, actorAccountId, Map.of("status", STATUS_DRAFT));
        return getLaunch(projectNo, launchId);
    }

    public LaunchView updateLaunch(String projectNo, String launchId, String actorAccountId, UpdateLaunchRequest request) {
        String projectId = requireProjectId(projectNo);
        LaunchView current = getLaunch(projectNo, launchId);
        if (STATUS_SETTLED.equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settled launch cannot be updated");
        }
        Map<String, Object> metadata = new LinkedHashMap<>(current.metadata());
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        dsl.query("""
                                update project_validations
                                set title = coalesce(?, title),
                                    hypothesis = coalesce(?, hypothesis),
                                    metadata = ?::jsonb,
                                    version = version + 1,
                                    updated_at = now()
                                where id = ? and project_id = ?
                                """,
                        blankToNull(request.title()),
                        blankToNull(request.hypothesis()),
                        PostgresJson.jsonb(metadata).data(),
                        launchId,
                        projectId)
                .execute();
        appendEvent(projectId, launchId, "launch_updated", "launch", launchId, actorAccountId, Map.of("versioned", true));
        return getLaunch(projectNo, launchId);
    }

    public LaunchView publishLaunch(String projectNo, String launchId, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        LaunchView launch = getLaunch(projectNo, launchId);
        if (!STATUS_DRAFT.equals(launch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft launch can be published");
        }
        dsl.query("""
                        update project_validations
                        set status = ?, published_by_account_id = ?, published_at = now(), updated_at = now()
                        where id = ? and project_id = ?
                        """, STATUS_LIVE, actorAccountId, launchId, projectId)
                .execute();
        appendEvent(projectId, launchId, "launch_published", "launch", launchId, actorAccountId, Map.of("from", STATUS_DRAFT, "to", STATUS_LIVE));
        return getLaunch(projectNo, launchId);
    }

    public LaunchView settleLaunch(String projectNo, String launchId, String actorAccountId, SettleLaunchRequest request) {
        String projectId = requireProjectId(projectNo);
        LaunchView launch = getLaunch(projectNo, launchId);
        if (STATUS_DRAFT.equals(launch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft launch cannot be settled");
        }
        long submittedProofs = dsl.resultQuery("""
                        select count(*) as submitted_count
                        from work_receipts receipt
                        where receipt.output->>'launchId' = ?
                          and receipt.output->>'proofStatus' = 'submitted'
                        """, launchId)
                .fetchOne(record -> record.get("submitted_count", Long.class));
        if (submittedProofs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All submitted proofs must be reviewed before settlement");
        }
        Map<String, Object> rewardSnapshot = settlementRewardSnapshot(projectId, launchId, request.rewardSnapshot());
        settlePendingRewards(launchId, rewardSnapshot);
        dsl.query("""
                        update work_items
                        set status = 'closed',
                            output_schema = output_schema || jsonb_build_object('taskStatus', 'settled'),
                            updated_at = now()
                        where source_type = ? and source_id = ? and output_schema->>'taskStatus' = 'accepted'
                        """, SOURCE_TASK, launchId)
                .execute();
        Map<String, Object> metadata = new LinkedHashMap<>(launch.metadata());
        metadata.put("settlementReason", nullToEmpty(request.reason()));
        metadata.put("scoreSnapshot", request.scoreSnapshot() == null ? Map.of() : request.scoreSnapshot());
        metadata.put("curveSnapshot", request.curveSnapshot() == null ? Map.of() : request.curveSnapshot());
        metadata.put("rewardSnapshot", rewardSnapshot);
        dsl.query("""
                        update project_validations
                        set status = ?, settled_by_account_id = ?, settled_at = now(), metadata = ?::jsonb, updated_at = now()
                        where id = ? and project_id = ?
                        """, STATUS_SETTLED, actorAccountId, PostgresJson.jsonb(metadata).data(), launchId, projectId)
                .execute();
        appendEvent(projectId, launchId, "launch_settled", "launch", launchId, actorAccountId, Map.of(
                "scoreSnapshot", request.scoreSnapshot() == null ? Map.of() : request.scoreSnapshot(),
                "curveSnapshot", request.curveSnapshot() == null ? Map.of() : request.curveSnapshot()));
        return getLaunch(projectNo, launchId);
    }

    public List<ProofRequestView> listProofRequests(String projectNo, String launchId) {
        return getLaunch(projectNo, launchId).proofRequests();
    }

    public ProofRequestView createProofRequest(String projectNo, String launchId, String actorAccountId, CreateProofRequestRequest request) {
        String projectId = requireLaunchProject(projectNo, launchId);
        LaunchView launch = getLaunch(projectNo, launchId);
        List<ProofRequestView> requests = new ArrayList<>(launch.proofRequests());
        ProofRequestView proofRequest = newProofRequest(launchId, actorAccountId, request);
        requests.add(proofRequest);
        Map<String, Object> metadata = new LinkedHashMap<>(launch.metadata());
        metadata.put("proofRequests", requests.stream().map(this::proofRequestMap).toList());
        dsl.query("update project_validations set metadata = ?::jsonb, updated_at = now() where id = ? and project_id = ?",
                PostgresJson.jsonb(metadata).data(), launchId, projectId).execute();
        appendEvent(projectId, launchId, "proof_request_created", "proof_request", proofRequest.id(), actorAccountId, Map.of("version", 1));
        return proofRequest;
    }

    public List<TaskView> listTasks(String projectNo, String launchId) {
        requireLaunchProject(projectNo, launchId);
        return dsl.resultQuery("""
                        select * from work_items
                        where source_type = ? and source_id = ?
                        order by created_at asc
                        """, SOURCE_TASK, launchId)
                .fetch(this::mapTask);
    }

    public List<TaskView> listProjectTasks(String projectNo) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select * from work_items
                        where source_type = ? and output_schema->>'projectId' = ?
                        order by created_at asc
                        """, SOURCE_TASK, projectId)
                .fetch(this::mapTask);
    }

    public Map<String, Object> agentContext(String projectNo) {
        String projectId = requireProjectId(projectNo);
        List<TaskView> tasks = listProjectTasks(projectNo);
        List<RewardView> rewards = listRewards(projectNo).stream().limit(50).toList();
        // 中文注释：Agent Context 从统一 Work 投影读取任务和奖励，外部 agent 无需理解旧 validation 私表。
        return Map.of(
                "openTasks", tasks.stream().filter(task -> "open".equals(task.status())).map(this::taskContext).toList(),
                "claimedTasks", tasks.stream().filter(task -> List.of("claimed", "working", "changes_requested").contains(task.status())).map(this::taskContext).toList(),
                "reviewingTasks", tasks.stream().filter(task -> "proof_submitted".equals(task.status())).map(this::taskContext).toList(),
                "acceptedTasks", tasks.stream().filter(task -> List.of("accepted", "settled").contains(task.status())).map(this::taskContext).toList(),
                "rewards", rewards.stream().map(this::rewardContext).toList(),
                "projectId", projectId);
    }

    public TaskView createTask(String projectNo, String launchId, String actorAccountId, CreateTaskRequest request) {
        String projectId = requireLaunchProject(projectNo, launchId);
        String taskId = "vtask_" + UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> output = Map.ofEntries(
                Map.entry("projectId", projectId),
                Map.entry("launchId", launchId),
                Map.entry("intent", request.intent().trim()),
                Map.entry("linkedProofRequestIds", request.linkedProofRequestIds() == null ? List.of() : request.linkedProofRequestIds()),
                Map.entry("deliverable", request.deliverable().trim()),
                Map.entry("suggestedEvidence", request.suggestedEvidence() == null ? List.of() : request.suggestedEvidence()),
                Map.entry("rewardPreview", request.rewardPreview() == null ? Map.of() : request.rewardPreview()),
                Map.entry("templateRef", nullToEmpty(request.templateRef())),
                Map.entry("taskStatus", "open"),
                Map.entry("subStatus", ""),
                Map.entry("tags", request.tags() == null ? List.of() : request.tags()),
                Map.entry("metadata", request.metadata() == null ? Map.of() : request.metadata()),
                Map.entry("createdByAccountId", actorAccountId),
                Map.entry("claimedByAccountId", ""),
                Map.entry("claimedAt", ""));
        dsl.query("""
                                insert into work_items (
                                  id, item_no, source_type, source_id, account_id, title, goal,
                                  acceptance_criteria, input_refs, output_schema, required_role, urgency, status, ready_at, created_at, updated_at
                                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, 'project_validator', 'attention', 'ready', now(), now(), now())
                                """,
                        taskId,
                        taskId,
                        SOURCE_TASK,
                        launchId,
                        actorAccountId,
                        request.title().trim(),
                        request.deliverable().trim(),
                        PostgresJson.jsonb(request.acceptanceCriteria() == null ? List.of() : request.acceptanceCriteria()).data(),
                        PostgresJson.jsonb(List.of("project:" + projectId, "launch:" + launchId)).data(),
                        PostgresJson.jsonb(output).data())
                .execute();
        appendEvent(projectId, launchId, "task_created", "task", taskId, actorAccountId, Map.of("createdAt", now.toString()));
        return getTask(projectNo, taskId);
    }

    public TaskView claimTask(String projectNo, String taskId, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        TaskView task = getTask(projectNo, taskId);
        if (!projectId.equals(task.projectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!"open".equals(task.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open task can be claimed");
        }
        Instant now = Instant.now();
        patchTask(taskId, "claimed", "claimed", Map.of("claimedByAccountId", actorAccountId, "claimedAt", now.toString()), actorAccountId);
        upsertRun(taskId, actorAccountId, "claimed", now, null, null);
        appendEvent(projectId, task.launchId(), "task_claimed", "task", taskId, actorAccountId, Map.of());
        return getTask(projectNo, taskId);
    }

    public ProofView submitProof(String projectNo, String taskId, String actorAccountId, SubmitProofRequest request) {
        String projectId = requireProjectId(projectNo);
        TaskView task = getTask(projectNo, taskId);
        if (!projectId.equals(task.projectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!List.of("claimed", "working", "changes_requested", "open").contains(task.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task cannot accept proof in current status");
        }
        List<String> linkedProofRequests = request.linkedProofRequestIds() == null || request.linkedProofRequestIds().isEmpty()
                ? task.linkedProofRequestIds()
                : request.linkedProofRequestIds();
        Map<String, Object> evidenceValidation = validateEvidenceItems(request.evidenceItems());
        Map<String, Object> proofMetadata = mergeMetadata(request.metadata(), Map.of("evidenceValidation", evidenceValidation));
        String proofId = "vproof_" + UUID.randomUUID();
        Instant now = Instant.now();
        upsertRun(taskId, actorAccountId, "submitted", now, now, null);
        Map<String, Object> output = Map.of(
                "projectId", projectId,
                "launchId", task.launchId(),
                "taskId", taskId,
                "evidenceItems", request.evidenceItems() == null ? List.of() : request.evidenceItems(),
                "linkedProofRequestIds", linkedProofRequests,
                "notes", nullToEmpty(request.notes()),
                "proofStatus", "submitted",
                "metadata", proofMetadata,
                "submittedByAccountId", actorAccountId,
                "updatedAt", now.toString());
        dsl.query("""
                                insert into work_receipts (
                                  id, receipt_no, work_run_id, summary, output, evidence_refs, trace_refs, content_hashes, created_at
                                ) values (?, ?, ?, ?, ?::jsonb, '[]'::jsonb, ?::jsonb, '[]'::jsonb, now())
                                """,
                        proofId,
                        proofId,
                        runId(taskId),
                        request.summary().trim(),
                        PostgresJson.jsonb(output).data(),
                        PostgresJson.jsonb(linkedProofRequests).data())
                .execute();
        patchTask(taskId, "proof_submitted", "submitted", Map.of(), actorAccountId);
        dsl.query("update project_validations set status = ?, updated_at = now() where id = ? and status = ?",
                STATUS_REVIEWING, task.launchId(), STATUS_LIVE).execute();
        appendEvent(projectId, task.launchId(), "proof_submitted", "proof", proofId, actorAccountId, Map.of("taskId", taskId));
        return getProof(proofId);
    }

    public List<ProofView> listProofs(String projectNo, String launchId) {
        requireLaunchProject(projectNo, launchId);
        return dsl.resultQuery("""
                        select receipt.*
                        from work_receipts receipt
                        where receipt.output->>'launchId' = ?
                        order by receipt.created_at desc
                        """, launchId)
                .fetch(this::mapProof);
    }

    public List<ReviewQueueItemView> listReviewQueue(String projectNo, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select
                          receipt.*,
                          validation.title as launch_title,
                          validation.status as launch_status,
                          task.title as task_title,
                          task.output_schema->>'taskStatus' as task_status
                        from work_receipts receipt
                        join work_runs run on run.id = receipt.work_run_id
                        join work_items task on task.id = run.work_item_id
                        join project_validations validation on validation.id = receipt.output->>'launchId'
                        where receipt.output->>'projectId' = ?
                          and receipt.output->>'proofStatus' = 'submitted'
                          and receipt.output->>'submittedByAccountId' <> ?
                          and not exists (
                            select 1 from work_reviews review
                            where review.work_run_id = run.id
                              and review.reviewer_account_id = ?
                          )
                        order by receipt.created_at asc
                        """, projectId, actorAccountId, actorAccountId)
                .fetch(this::mapReviewQueueItem);
    }

    public ProofView reviewProof(String projectNo, String proofId, String actorAccountId, ReviewProofRequest request) {
        String projectId = requireProjectId(projectNo);
        ProofView proof = getProof(proofId);
        if (!projectId.equals(proof.projectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof not found");
        }
        if (actorAccountId.equals(proof.submittedByAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proof submitter cannot review the same proof");
        }
        if (!"submitted".equals(proof.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only submitted proof can be validated");
        }
        String runId = runId(proof.taskId());
        boolean alreadyValidated = dsl.resultQuery("""
                        select exists(
                          select 1 from work_reviews
                          where work_run_id = ? and reviewer_account_id = ?
                        ) as exists_flag
                        """, runId, actorAccountId)
                .fetchOne(record -> Boolean.TRUE.equals(record.get("exists_flag", Boolean.class)));
        if (alreadyValidated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already validated this proof");
        }
        String result = normalizeReviewResult(request.result());
        // 中文注释：hold 使用返工类 WorkReview 决策承载复核队列状态，同时保留 proof 的 held 语义给验证协议读取。
        String decision = "accept".equals(result) ? "accepted" : "revision_requested";
        String validationMode = normalizeValidationMode(request.validationMode(), request.stakedShares());
        BigDecimal stakedShares = VALIDATION_MODE_STAKED.equals(validationMode)
                ? requirePositiveStake(request.stakedShares())
                : BigDecimal.ZERO;
        String reviewId = "vreview_" + UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> reviewMetadata = mergeMetadata(request.metadata(), Map.of(
                "validationMode", validationMode,
                "stakedShares", stakedShares,
                "validationUnit", "project_share",
                "requestedEvidence", request.requestedEvidence() == null ? List.of() : request.requestedEvidence(),
                "riskFlags", request.riskFlags() == null ? List.of() : request.riskFlags(),
                "scoreInputs", request.scoreInputs() == null ? Map.of() : request.scoreInputs()));
        dsl.query("""
                        insert into work_reviews (
                          id, review_no, work_run_id, reviewer_account_id, status, decision, decision_reason, created_at, resolved_at
                        ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
                        """, reviewId, reviewId, runId, actorAccountId, decision, decision, reviewReason(request.reason(), result))
                .execute();
        appendEvent(projectId, proof.launchId(), "proof_validated", "proof", proofId, actorAccountId, Map.of(
                "result", result,
                "reviewId", reviewId,
                "validationMode", validationMode,
                "stakedShares", stakedShares,
                "reviewMetadata", reviewMetadata));
        ProofValidationStatsView validationStats = validationStats(proofId, proof.taskId());
        if ("accept".equals(result) && validationStats.finalized()) {
            patchProofStatus(proofId, "accepted");
            patchTask(proof.taskId(), "accepted", "accepted", Map.of(), actorAccountId);
            dsl.query("update work_runs set status = 'accepted', accepted_at = now(), updated_at = now() where id = ?", runId).execute();
            createValidationRewards(projectId, proof, validationStats);
        } else if ("request_changes".equals(result)) {
            patchProofStatus(proofId, "changes_requested");
            patchTask(proof.taskId(), "changes_requested", "revision_requested", Map.of(), actorAccountId);
            dsl.query("update work_runs set status = 'revision_requested', updated_at = now() where id = ?", runId).execute();
        } else if ("hold".equals(result)) {
            Map<String, Object> taskMetadata = new LinkedHashMap<>(getTask(projectNo, proof.taskId()).metadata());
            taskMetadata.put("riskFlags", request.riskFlags() == null ? List.of() : request.riskFlags());
            patchProofStatus(proofId, "held");
            patchTask(proof.taskId(), "changes_requested", "revision_requested", Map.of(
                    "subStatus", "review_hold",
                    "metadata", taskMetadata), actorAccountId);
            dsl.query("update work_runs set status = 'revision_requested', updated_at = now() where id = ?", runId).execute();
        }
        return getProof(proofId);
    }

    public List<FeedbackView> listFeedback(String projectNo) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select * from work_items
                        where source_type = ? and source_id = ?
                        order by created_at desc
                        """, SOURCE_FEEDBACK, projectId)
                .fetch(this::mapFeedback);
    }

    public FeedbackView createFeedback(String projectNo, String actorAccountId, CreateFeedbackRequest request) {
        String projectId = requireProjectId(projectNo);
        if (request.launchId() != null && !request.launchId().isBlank()) {
            requireLaunchProject(projectNo, request.launchId());
        }
        String feedbackId = "vfb_" + UUID.randomUUID();
        Map<String, Object> output = Map.ofEntries(
                Map.entry("projectId", projectId),
                Map.entry("launchId", nullToEmpty(request.launchId())),
                Map.entry("subjectType", request.subjectType().trim()),
                Map.entry("subjectId", request.subjectId().trim()),
                Map.entry("intent", request.intent().trim()),
                Map.entry("reason", request.reason().trim()),
                Map.entry("evidence", request.evidence() == null ? List.of() : request.evidence()),
                Map.entry("suggestedAction", nullToEmpty(request.suggestedAction())),
                Map.entry("feedbackStatus", "open"),
                Map.entry("metadata", request.metadata() == null ? Map.of() : request.metadata()),
                Map.entry("createdByAccountId", actorAccountId),
                Map.entry("resolvedByAccountId", ""),
                Map.entry("resolvedAt", ""));
        dsl.query("""
                                insert into work_items (
                                  id, item_no, source_type, source_id, account_id, title, goal,
                                  acceptance_criteria, input_refs, output_schema, required_role, urgency, status, ready_at, created_at, updated_at
                                ) values (?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, ?::jsonb, ?::jsonb, 'project_validator', 'attention', 'ready', now(), now(), now())
                                """,
                        feedbackId,
                        feedbackId,
                        SOURCE_FEEDBACK,
                        projectId,
                        actorAccountId,
                        request.intent().trim(),
                        request.reason().trim(),
                        PostgresJson.jsonb(List.of("project:" + projectId, "launch:" + nullToEmpty(request.launchId()))).data(),
                        PostgresJson.jsonb(output).data())
                .execute();
        appendEvent(projectId, blankToNull(request.launchId()), "feedback_created", "feedback", feedbackId, actorAccountId, Map.of("intent", request.intent()));
        return getFeedback(feedbackId);
    }

    public FeedbackView resolveFeedback(String projectNo, String feedbackId, String actorAccountId, ResolveFeedbackRequest request) {
        String projectId = requireProjectId(projectNo);
        FeedbackView feedback = getFeedback(feedbackId);
        if (!projectId.equals(feedback.projectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found");
        }
        String status = normalizeFeedbackStatus(request.status());
        Map<String, Object> patch = Map.of(
                "feedbackStatus", status,
                "resolvedByAccountId", actorAccountId,
                "resolvedAt", Instant.now().toString(),
                "resolution", nullToEmpty(request.resolution()),
                "resolutionMetadata", request.metadata() == null ? Map.of() : request.metadata());
        patchWorkItemOutput(feedbackId, status.equals("resolved") || status.equals("dismissed") ? "closed" : "submitted", patch);
        appendEvent(projectId, feedback.launchId(), "feedback_resolved", "feedback", feedbackId, actorAccountId, Map.of("status", status));
        return getFeedback(feedbackId);
    }

    public List<RewardView> listRewards(String projectNo) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = 'project_validation_reward'
                          and output_snapshot->>'projectId' = ?
                        order by created_at desc
                        """, projectId)
                .fetch(record -> mapReward(PostgresJson.map(record.get("output_snapshot", JSONB.class))));
    }

    private void createValidationRewards(String projectId, ProofView proof, ProofValidationStatsView validationStats) {
        if (!rewardExists(proof.id(), proof.submittedByAccountId(), "proof_submitter")) {
            createPendingReward(projectId, proof.launchId(), proof.taskId(), proof.id(), proof.submittedByAccountId(), BigDecimal.ONE, Map.of(
                    "role", "proof_submitter",
                    "validationStats", validationStats));
        }
        List<Record> validators = dsl.resultQuery("""
                        select review.id, review.reviewer_account_id, event.output_snapshot
                        from work_reviews review
                        left join work_events event on event.output_snapshot->>'reviewId' = review.id
                        where review.work_run_id = ? and review.decision = 'accepted'
                        order by review.created_at asc
                        """, runId(proof.taskId()))
                .fetch();
        for (Record validator : validators) {
            String reviewerAccountId = validator.get("reviewer_account_id", String.class);
            if (rewardExists(proof.id(), reviewerAccountId, "proof_validator")) {
                continue;
            }
            Map<String, Object> metadata = mapValue(PostgresJson.map(validator.get("output_snapshot", JSONB.class)).get("reviewMetadata"));
            BigDecimal contributionWeight = validatorContributionWeight(metadata, validationStats.sharesPerEffectiveValidator());
            createPendingReward(projectId, proof.launchId(), proof.taskId(), proof.id(), reviewerAccountId, contributionWeight, Map.of(
                    "role", "proof_validator",
                    "reviewId", validator.get("id", String.class),
                    "validationMode", String.valueOf(metadata.getOrDefault("validationMode", VALIDATION_MODE_ORDINARY)),
                    "stakedShares", metadata.getOrDefault("stakedShares", BigDecimal.ZERO),
                    "validationStats", validationStats));
        }
    }

    private boolean rewardExists(String proofId, String accountId, String role) {
        return dsl.resultQuery("""
                        select exists(
                          select 1 from work_events
                          where subject_type = 'project_validation_reward'
                            and output_snapshot->>'proofId' = ?
                            and output_snapshot->>'recipientAccountId' = ?
                            and output_snapshot->'rewardSnapshot'->>'role' = ?
                        ) as exists_flag
                        """, proofId, accountId, role)
                .fetchOne(record -> Boolean.TRUE.equals(record.get("exists_flag", Boolean.class)));
    }

    private void createPendingReward(
            String projectId,
            String launchId,
            String taskId,
            String proofId,
            String recipientAccountId,
            BigDecimal contributionWeight,
            Map<String, Object> rewardSnapshot) {
        String rewardId = "vreward_" + UUID.randomUUID();
        Map<String, Object> output = Map.ofEntries(
                Map.entry("id", rewardId),
                Map.entry("projectId", projectId),
                Map.entry("launchId", launchId),
                Map.entry("taskId", taskId),
                Map.entry("proofId", proofId),
                Map.entry("recipientAccountId", recipientAccountId),
                Map.entry("status", "pending"),
                Map.entry("contributionWeight", contributionWeight),
                Map.entry("rewardSnapshot", rewardSnapshot == null ? Map.of() : rewardSnapshot),
                Map.entry("metadata", Map.of()),
                Map.entry("createdAt", Instant.now().toString()),
                Map.entry("updatedAt", Instant.now().toString()));
        dsl.query("""
                insert into work_events (
                  id, subject_type, subject_id, actor_account_id, event_type, action_id,
                  input_snapshot, output_snapshot, created_at
                ) values (?, 'project_validation_reward', ?, ?, 'project_validation_reward_pending', 'project_validation_reward', '{}'::jsonb, ?::jsonb, now())
                """, rewardId, rewardId, recipientAccountId, PostgresJson.jsonb(output).data()).execute();
    }

    private void settlePendingRewards(String launchId, Map<String, Object> rewardSnapshot) {
        List<Record> pendingRewards = dsl.resultQuery("""
                        select id, output_snapshot
                        from work_events
                        where subject_type = 'project_validation_reward'
                          and output_snapshot->>'launchId' = ?
                          and output_snapshot->>'status' = 'pending'
                        order by created_at asc
                        """, launchId)
                .fetch();
        if (pendingRewards.isEmpty()) {
            return;
        }
        BigDecimal totalWeight = pendingRewards.stream()
                .map(record -> decimalValue(PostgresJson.map(record.get("output_snapshot", JSONB.class)).get("contributionWeight")))
                .filter(weight -> weight != null && weight.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rewardPool = rewardPool(rewardSnapshot);
        for (Record reward : pendingRewards) {
            LinkedHashMap<String, Object> output = new LinkedHashMap<>(PostgresJson.map(reward.get("output_snapshot", JSONB.class)));
            BigDecimal weight = positiveDecimal(output.get("contributionWeight"), BigDecimal.ZERO);
            LinkedHashMap<String, Object> nextSnapshot = new LinkedHashMap<>(mapValue(output.get("rewardSnapshot")));
            nextSnapshot.putAll(rewardSnapshot);
            if (rewardPool != null && totalWeight.signum() > 0 && weight.signum() > 0) {
                nextSnapshot.put("settledAmount", rewardPool.multiply(weight).divide(totalWeight, 6, RoundingMode.HALF_UP));
                nextSnapshot.put("settlementUnit", String.valueOf(rewardSnapshot.getOrDefault("unit", "project_share")));
                nextSnapshot.put("totalContributionWeight", totalWeight);
            }
            output.put("status", "settled");
            output.put("rewardSnapshot", nextSnapshot);
            output.put("updatedAt", Instant.now().toString());
            dsl.query("""
                    update work_events
                    set event_type = 'project_validation_reward_settled',
                        output_snapshot = ?::jsonb
                    where id = ?
                    """, PostgresJson.jsonb(output).data(), reward.get("id", String.class)).execute();
            settleValidationReward(reward.get("id", String.class), output, nextSnapshot, weight);
        }
    }

    private void settleValidationReward(String rewardId, Map<String, Object> output, Map<String, Object> rewardSnapshot, BigDecimal weight) {
        int shares = settledShares(rewardSnapshot);
        if (shares <= 0) {
            return;
        }
        String projectId = string(output, "projectId", "");
        String role = string(rewardSnapshot, "role", "proof_submitter");
        LedgerReason reason = "proof_validator".equals(role) ? LedgerReason.VALIDATION_VALIDATOR : LedgerReason.VALIDATION_SUBMITTER;
        // 中文注释：Validation reward 结算时同步写入贡献账本和 shares 账本，避免验证协议形成独立奖励孤岛。
        contributionSettlementService.settle(new ProjectContributionSettlementService.ContributionCommand(
                null,
                projectId,
                "validation_reward",
                rewardId,
                string(output, "proofId", null),
                string(output, "recipientAccountId", ""),
                role,
                contributionTaskValue(weight),
                shares,
                0,
                "USDC",
                reason,
                SettlementType.SHARES,
                ShareIssuerType.PROJECT,
                projectId,
                null,
                null,
                null,
                null,
                string(output, "taskId", null),
                null,
                weight,
                Map.of(
                        "launchId", string(output, "launchId", ""),
                        "taskId", string(output, "taskId", ""),
                        "proofId", string(output, "proofId", ""),
                        "rewardSnapshot", rewardSnapshot),
                true,
                Instant.now()));
    }

    private int settledShares(Map<String, Object> rewardSnapshot) {
        BigDecimal settledAmount = positiveDecimal(rewardSnapshot.get("settledAmount"), BigDecimal.ZERO);
        return settledAmount.signum() <= 0 ? 0 : settledAmount.setScale(0, RoundingMode.DOWN).intValue();
    }

    private int contributionTaskValue(BigDecimal weight) {
        BigDecimal normalized = weight == null ? BigDecimal.ZERO : weight.multiply(new BigDecimal("1000"));
        return Math.max(0, Math.min(10000, normalized.setScale(0, RoundingMode.DOWN).intValue()));
    }

    private Map<String, Object> settlementRewardSnapshot(String projectId, String launchId, Map<String, Object> requestedSnapshot) {
        ProjectSharePoolEntity pool = projectSharePoolService.requireByProjectId(projectId);
        Long pendingCount = dsl.resultQuery("""
                        select count(*) as pending_count
                        from work_events
                        where subject_type = 'project_validation_reward'
                          and output_snapshot->>'launchId' = ?
                          and output_snapshot->>'status' = 'pending'
                        """, launchId)
                .fetchOne(record -> record.get("pending_count", Long.class));
        int pendingRewardCount = pendingCount == null ? 0 : pendingCount.intValue();
        int computedRewardPool = Math.min(pool.taskRemaining(), pool.currentBaseReward() * Math.max(0, pendingRewardCount));
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>(requestedSnapshot == null ? Map.of() : requestedSnapshot);
        snapshot.put("unit", "project_share");
        snapshot.put("rewardPool", computedRewardPool);
        snapshot.put("poolShares", computedRewardPool);
        snapshot.put("baseReward", pool.currentBaseReward());
        snapshot.put("pendingRewardCount", pendingRewardCount);
        snapshot.put("taskPoolRemaining", pool.taskRemaining());
        snapshot.put("source", "project_share_pool");
        return snapshot;
    }

    private ProofValidationStatsView validationStats(String proofId, String taskId) {
        ValidationPolicy policy = validationPolicy(taskId);
        Record aggregate = dsl.resultQuery("""
                        select
                          count(distinct review.reviewer_account_id)::int as participant_count,
                          count(*) filter (
                            where coalesce(nullif(event.output_snapshot->'reviewMetadata'->>'validationMode', ''), 'ordinary') = 'ordinary'
                          )::int as ordinary_count,
                          count(*) filter (
                            where coalesce(nullif(event.output_snapshot->'reviewMetadata'->>'validationMode', ''), 'ordinary') = 'staked'
                          )::int as staked_count,
                          coalesce(sum(
                            case
                              when coalesce(nullif(event.output_snapshot->'reviewMetadata'->>'validationMode', ''), 'ordinary') = 'staked'
                              then nullif(event.output_snapshot->'reviewMetadata'->>'stakedShares', '')::numeric
                              else 0
                            end
                          ), 0)::numeric as staked_shares
                        from work_reviews review
                        left join work_events event on event.output_snapshot->>'reviewId' = review.id
                        where review.work_run_id = ? and review.decision = 'accepted'
                        """, runId(taskId))
                .fetchOne();
        int participantCount = valueOrDefault(aggregate.get("participant_count", Integer.class), 0);
        int ordinaryCount = valueOrDefault(aggregate.get("ordinary_count", Integer.class), 0);
        int stakedCount = valueOrDefault(aggregate.get("staked_count", Integer.class), 0);
        BigDecimal stakedShares = numericOrZero(aggregate.get("staked_shares", BigDecimal.class));
        BigDecimal effectiveCount = BigDecimal.valueOf(ordinaryCount)
                .add(stakedShares.divide(policy.sharesPerEffectiveValidator(), 6, RoundingMode.DOWN));
        boolean finalized = participantCount >= policy.minParticipantCount()
                && effectiveCount.compareTo(policy.minEffectiveValidationCount()) >= 0;
        return new ProofValidationStatsView(
                participantCount,
                policy.minParticipantCount(),
                ordinaryCount,
                stakedCount,
                stakedShares,
                effectiveCount,
                policy.minEffectiveValidationCount(),
                policy.sharesPerEffectiveValidator(),
                finalized);
    }

    private ValidationPolicy validationPolicy(String taskId) {
        Map<String, Object> output = dsl.resultQuery("select output_schema from work_items where id = ?", taskId)
                .fetchOptional(record -> PostgresJson.map(record.get("output_schema", JSONB.class)))
                .orElse(Map.of());
        Map<String, Object> metadata = mapValue(output.get("metadata"));
        Map<String, Object> rawPolicy = mapValue(metadata.get("validationPolicy"));
        return new ValidationPolicy(
                positiveInt(rawPolicy.get("minParticipantCount"), DEFAULT_MIN_PARTICIPANTS),
                positiveDecimal(rawPolicy.get("minEffectiveValidationCount"), DEFAULT_MIN_EFFECTIVE_VALIDATORS),
                positiveDecimal(rawPolicy.get("sharesPerEffectiveValidator"), DEFAULT_SHARES_PER_EFFECTIVE_VALIDATOR));
    }

    private LaunchView getLaunch(String projectNo, String launchId) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("select * from project_validations where id = ? and project_id = ?", launchId, projectId)
                .fetchOptional(this::mapLaunch)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Launch not found"));
    }

    private TaskView getTask(String projectNo, String taskId) {
        String projectId = requireProjectId(projectNo);
        return dsl.resultQuery("""
                        select * from work_items
                        where id = ? and source_type = ? and output_schema->>'projectId' = ?
                        """, taskId, SOURCE_TASK, projectId)
                .fetchOptional(this::mapTask)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private ProofView getProof(String proofId) {
        return dsl.resultQuery("select * from work_receipts where id = ? or receipt_no = ?", proofId, proofId)
                .fetchOptional(this::mapProof)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof not found"));
    }

    private FeedbackView getFeedback(String feedbackId) {
        return dsl.resultQuery("select * from work_items where id = ? and source_type = ?", feedbackId, SOURCE_FEEDBACK)
                .fetchOptional(this::mapFeedback)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
    }

    private String requireLaunchProject(String projectNo, String launchId) {
        String projectId = requireProjectId(projectNo);
        String actual = dsl.resultQuery("select project_id from project_validations where id = ?", launchId)
                .fetchOptional(record -> record.get("project_id", String.class))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Launch not found"));
        if (!projectId.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Launch not found");
        }
        return projectId;
    }

    private String requireProjectId(String projectNo) {
        if (projectNo == null || projectNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .map(project -> project.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void appendEvent(String projectId, String launchId, String eventType, String subjectType, String subjectId, String actorAccountId, Map<String, Object> payload) {
        dsl.query("""
                                insert into work_events (
                                  id, subject_type, subject_id, actor_account_id, event_type, action_id,
                                  input_snapshot, output_snapshot, created_at
                                ) values (?, ?, ?, ?, ?, 'project_validation_protocol', ?::jsonb, ?::jsonb, now())
                                """,
                        "vevt_" + UUID.randomUUID(),
                        "project_validation_" + subjectType,
                        subjectId,
                        actorAccountId,
                        eventType,
                        PostgresJson.jsonb(Map.of("projectId", projectId, "launchId", launchId == null ? "" : launchId)).data(),
                        PostgresJson.jsonb(payload == null ? Map.of() : payload).data())
                .execute();
    }

    private void upsertRun(String taskId, String actorAccountId, String status, Instant startedAt, Instant submittedAt, Instant acceptedAt) {
        dsl.query("""
                                insert into work_runs (
                                  id, run_no, work_item_id, actor_account_id, status, execution_mode, started_at, submitted_at, accepted_at, updated_at
                                ) values (?, ?, ?, ?, ?, 'manual', ?::timestamptz, ?::timestamptz, ?::timestamptz, now())
                                on conflict (work_item_id, actor_account_id) do update
                                set status = excluded.status,
                                    submitted_at = coalesce(excluded.submitted_at, work_runs.submitted_at),
                                    accepted_at = coalesce(excluded.accepted_at, work_runs.accepted_at),
                                    updated_at = now()
                                """,
                        runId(taskId),
                        runId(taskId),
                        taskId,
                        actorAccountId,
                        status,
                        PostgresJson.offsetDateTime(startedAt),
                        PostgresJson.offsetDateTime(submittedAt),
                        PostgresJson.offsetDateTime(acceptedAt))
                .execute();
    }

    private void patchTask(String taskId, String taskStatus, String workStatus, Map<String, Object> extraOutput, String accountId) {
        LinkedHashMap<String, Object> patch = new LinkedHashMap<>(extraOutput == null ? Map.of() : extraOutput);
        patch.put("taskStatus", taskStatus);
        patchWorkItemOutput(taskId, workStatus, patch, accountId);
    }

    private void patchWorkItemOutput(String itemId, String workStatus, Map<String, Object> patch) {
        patchWorkItemOutput(itemId, workStatus, patch, null);
    }

    private void patchWorkItemOutput(String itemId, String workStatus, Map<String, Object> patch, String accountId) {
        List<Object> args = new ArrayList<>();
        args.add(workStatus);
        args.add(PostgresJson.jsonb(patch == null ? Map.of() : patch).data());
        // 中文注释：校验任务认领会绑定执行账号，其余状态更新只写状态快照，避免拼接 SQL 破坏参数边界。
        if (accountId == null) {
            args.add(itemId);
            dsl.query("""
                            update work_items
                            set status = ?,
                                output_schema = output_schema || ?::jsonb,
                                updated_at = now()
                            where id = ?
                            """, args.toArray())
                    .execute();
            return;
        }
        args.add(accountId);
        args.add(itemId);
        dsl.query("""
                        update work_items
                        set status = ?,
                            output_schema = output_schema || ?::jsonb,
                            updated_at = now(),
                            account_id = ?
                        where id = ?
                        """, args.toArray())
                .execute();
    }

    private void patchProofStatus(String proofId, String status) {
        dsl.query("""
                update work_receipts
                set output = output || jsonb_build_object('proofStatus', ?, 'updatedAt', ?)
                where id = ? or receipt_no = ?
                """, status, Instant.now().toString(), proofId, proofId).execute();
    }

    private LaunchView mapLaunch(Record record) {
        Map<String, Object> metadata = PostgresJson.map(record.get("metadata", JSONB.class));
        return new LaunchView(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("title", String.class),
                record.get("hypothesis", String.class),
                record.get("status", String.class),
                valueOrDefault(record.get("version", Integer.class), 1),
                record.get("parent_launch_id", String.class),
                jsonList(record.get("source_refs", JSONB.class)),
                metadata,
                record.get("created_by_account_id", String.class),
                record.get("published_by_account_id", String.class),
                record.get("settled_by_account_id", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("published_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("settled_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)),
                proofRequests(metadata.get("proofRequests")));
    }

    private TaskView mapTask(Record record) {
        Map<String, Object> output = PostgresJson.map(record.get("output_schema", JSONB.class));
        return new TaskView(
                record.get("id", String.class),
                string(output, "projectId", ""),
                string(output, "launchId", record.get("source_id", String.class)),
                record.get("title", String.class),
                string(output, "intent", ""),
                stringList(output.get("linkedProofRequestIds")),
                string(output, "deliverable", record.get("goal", String.class)),
                PostgresJson.stringList(record.get("acceptance_criteria", JSONB.class)),
                listOfMaps(output.get("suggestedEvidence")),
                mapValue(output.get("rewardPreview")),
                blankToNull(string(output, "templateRef", "")),
                string(output, "taskStatus", taskStatusFromWork(record.get("status", String.class))),
                blankToNull(string(output, "subStatus", "")),
                stringList(output.get("tags")),
                mapValue(output.get("metadata")),
                string(output, "createdByAccountId", record.get("account_id", String.class)),
                blankToNull(string(output, "claimedByAccountId", "")),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                instantValue(output.get("claimedAt")),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private ProofView mapProof(Record record) {
        Map<String, Object> output = PostgresJson.map(record.get("output", JSONB.class));
        String proofId = record.get("id", String.class);
        String taskId = string(output, "taskId", "");
        return new ProofView(
                proofId,
                string(output, "projectId", ""),
                string(output, "launchId", ""),
                taskId,
                record.get("summary", String.class),
                listOfMaps(output.get("evidenceItems")),
                stringList(output.get("linkedProofRequestIds")),
                blankToNull(string(output, "notes", "")),
                string(output, "proofStatus", "submitted"),
                validationStats(proofId, taskId),
                mapValue(output.get("metadata")),
                string(output, "submittedByAccountId", ""),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                instantValue(output.get("updatedAt"), PostgresJson.instant(record.get("created_at", OffsetDateTime.class))));
    }

    private ReviewQueueItemView mapReviewQueueItem(Record record) {
        ProofView proof = mapProof(record);
        return new ReviewQueueItemView(
                proof,
                record.get("launch_title", String.class),
                record.get("launch_status", String.class),
                record.get("task_title", String.class),
                record.get("task_status", String.class),
                proof.submittedByAccountId(),
                proof.createdAt(),
                Map.of(
                        "role", "proof_validator",
                        "participantProgress", proof.validationStats().participantCount() + "/" + proof.validationStats().minParticipantCount(),
                        "effectiveProgress", proof.validationStats().effectiveValidationCount() + "/" + proof.validationStats().minEffectiveValidationCount()));
    }

    private FeedbackView mapFeedback(Record record) {
        Map<String, Object> output = PostgresJson.map(record.get("output_schema", JSONB.class));
        return new FeedbackView(
                record.get("id", String.class),
                string(output, "projectId", record.get("source_id", String.class)),
                blankToNull(string(output, "launchId", "")),
                string(output, "subjectType", ""),
                string(output, "subjectId", ""),
                string(output, "intent", record.get("title", String.class)),
                string(output, "reason", record.get("goal", String.class)),
                listOfMaps(output.get("evidence")),
                blankToNull(string(output, "suggestedAction", "")),
                string(output, "feedbackStatus", "open"),
                mapValue(output.get("metadata")),
                string(output, "createdByAccountId", record.get("account_id", String.class)),
                blankToNull(string(output, "resolvedByAccountId", "")),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                instantValue(output.get("resolvedAt")),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private RewardView mapReward(Map<String, Object> output) {
        return new RewardView(
                string(output, "id", ""),
                string(output, "projectId", ""),
                string(output, "launchId", ""),
                blankToNull(string(output, "taskId", "")),
                blankToNull(string(output, "proofId", "")),
                blankToNull(string(output, "recipientAccountId", "")),
                string(output, "status", "pending"),
                positiveDecimal(output.get("contributionWeight"), BigDecimal.ZERO),
                mapValue(output.get("rewardSnapshot")),
                mapValue(output.get("metadata")),
                instantValue(output.get("createdAt")),
                instantValue(output.get("updatedAt")));
    }

    private List<Map<String, Object>> proofRequestDrafts(String launchId, String actorAccountId, List<ProofRequestDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        return drafts.stream()
                .map(draft -> proofRequestMap(new ProofRequestView(
                        "prq_" + UUID.randomUUID(),
                        launchId,
                        draft.title().trim(),
                        draft.intent().trim(),
                        draft.evidenceRequirements() == null ? List.of() : draft.evidenceRequirements(),
                        draft.acceptanceSignals() == null ? List.of() : draft.acceptanceSignals(),
                        blankDefault(draft.riskLevel(), "normal"),
                        1,
                        null,
                        "current",
                        draft.metadata() == null ? Map.of() : draft.metadata(),
                        actorAccountId,
                        Instant.now(),
                        Instant.now())))
                .toList();
    }

    private ProofRequestView newProofRequest(String launchId, String actorAccountId, CreateProofRequestRequest request) {
        Instant now = Instant.now();
        return new ProofRequestView(
                "prq_" + UUID.randomUUID(),
                launchId,
                request.title().trim(),
                request.intent().trim(),
                request.evidenceRequirements() == null ? List.of() : request.evidenceRequirements(),
                request.acceptanceSignals() == null ? List.of() : request.acceptanceSignals(),
                blankDefault(request.riskLevel(), "normal"),
                1,
                null,
                "current",
                request.metadata() == null ? Map.of() : request.metadata(),
                actorAccountId,
                now,
                now);
    }

    private Map<String, Object> proofRequestMap(ProofRequestView view) {
        return Map.ofEntries(
                Map.entry("id", view.id()),
                Map.entry("launchId", view.launchId()),
                Map.entry("title", view.title()),
                Map.entry("intent", view.intent()),
                Map.entry("evidenceRequirements", view.evidenceRequirements()),
                Map.entry("acceptanceSignals", view.acceptanceSignals()),
                Map.entry("riskLevel", view.riskLevel()),
                Map.entry("version", view.version()),
                Map.entry("parentVersionId", view.parentVersionId() == null ? "" : view.parentVersionId()),
                Map.entry("status", view.status()),
                Map.entry("metadata", view.metadata()),
                Map.entry("createdByAccountId", view.createdByAccountId()),
                Map.entry("createdAt", view.createdAt().toString()),
                Map.entry("updatedAt", view.updatedAt().toString()));
    }

    private List<ProofRequestView> proofRequests(Object value) {
        return listOfMaps(value).stream()
                .map(map -> new ProofRequestView(
                        string(map, "id", ""),
                        string(map, "launchId", ""),
                        string(map, "title", ""),
                        string(map, "intent", ""),
                        listOfMaps(map.get("evidenceRequirements")),
                        listOfMaps(map.get("acceptanceSignals")),
                        string(map, "riskLevel", "normal"),
                        intValue(map.get("version"), 1),
                        blankToNull(string(map, "parentVersionId", "")),
                        string(map, "status", "current"),
                        mapValue(map.get("metadata")),
                        string(map, "createdByAccountId", ""),
                        instantValue(map.get("createdAt")),
                        instantValue(map.get("updatedAt"))))
                .toList();
    }

    private Map<String, Object> taskContext(TaskView task) {
        return Map.of(
                "taskId", task.id(),
                "launchId", task.launchId(),
                "title", task.title(),
                "intent", task.intent(),
                "status", task.status(),
                "linkedProofRequestIds", task.linkedProofRequestIds(),
                "deliverable", task.deliverable(),
                "acceptanceCriteria", task.acceptanceCriteria(),
                "claimedByAccountId", task.claimedByAccountId() == null ? "" : task.claimedByAccountId());
    }

    private Map<String, Object> rewardContext(RewardView reward) {
        return Map.of(
                "rewardId", reward.id(),
                "launchId", reward.launchId(),
                "taskId", reward.taskId() == null ? "" : reward.taskId(),
                "proofId", reward.proofId() == null ? "" : reward.proofId(),
                "recipientAccountId", reward.recipientAccountId() == null ? "" : reward.recipientAccountId(),
                "status", reward.status(),
                "contributionWeight", reward.contributionWeight(),
                "rewardSnapshot", reward.rewardSnapshot());
    }

    private Map<String, Object> validateEvidenceItems(List<Map<String, Object>> evidenceItems) {
        if (evidenceItems == null || evidenceItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof evidenceItems must include at least one evidence item");
        }
        int urlCount = 0;
        List<Map<String, Object>> healthChecks = new ArrayList<>();
        for (Map<String, Object> item : evidenceItems) {
            String kind = String.valueOf(item.getOrDefault("kind", "")).trim();
            if (kind.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof evidence item kind is required");
            }
            Object urlValue = item.get("url");
            if (urlValue == null) {
                urlValue = item.get("href");
            }
            if (urlValue instanceof String url && !url.isBlank()) {
                validateEvidenceUrl(url);
                urlCount++;
                if ("deployment_url".equals(kind)) {
                    healthChecks.add(urlHealthCheckService.check(url));
                }
            }
        }
        return Map.of("itemCount", evidenceItems.size(), "urlCount", urlCount, "healthChecks", healthChecks);
    }

    private void validateEvidenceUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof evidence URL must be http or https");
            }
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof evidence URL is invalid", exception);
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> metadata, Map<String, Object> patch) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        merged.putAll(patch);
        return merged;
    }

    private BigDecimal rewardPool(Map<String, Object> rewardSnapshot) {
        for (String key : List.of("rewardPool", "poolShares", "amount")) {
            BigDecimal value = decimalValue(rewardSnapshot.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal validatorContributionWeight(Map<String, Object> metadata, BigDecimal sharesPerEffectiveValidator) {
        String mode = String.valueOf(metadata.getOrDefault("validationMode", VALIDATION_MODE_ORDINARY));
        if (VALIDATION_MODE_STAKED.equals(mode)) {
            return positiveDecimal(metadata.get("stakedShares"), BigDecimal.ZERO)
                    .divide(sharesPerEffectiveValidator, 6, RoundingMode.DOWN)
                    .multiply(VALIDATOR_REWARD_WEIGHT);
        }
        return VALIDATOR_REWARD_WEIGHT;
    }

    private BigDecimal requirePositiveStake(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staked shares must be positive");
        }
        return value.setScale(6, RoundingMode.DOWN);
    }

    private String normalizeValidationMode(String mode, BigDecimal stakedShares) {
        String normalized = mode == null || mode.isBlank() ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return stakedShares != null && stakedShares.signum() > 0 ? VALIDATION_MODE_STAKED : VALIDATION_MODE_ORDINARY;
        }
        if (!List.of(VALIDATION_MODE_ORDINARY, VALIDATION_MODE_STAKED).contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation mode must be ordinary or staked");
        }
        return normalized;
    }

    private String normalizeReviewResult(String result) {
        String normalized = result == null ? "" : result.trim().toLowerCase();
        if (!List.of("accept", "request_changes", "hold").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review result must be accept, request_changes, or hold");
        }
        return normalized;
    }

    private String normalizeFeedbackStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (!List.of("resolved", "changes_requested", "held", "dismissed").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback status must be resolved, changes_requested, held, or dismissed");
        }
        return normalized;
    }

    private String reviewReason(String reason, String result) {
        String normalized = reason == null ? "" : reason.trim();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "accept".equals(result) ? "确认成果有效" : "需要补充成果";
    }

    private JSONB jsonList(List<Map<String, Object>> values) {
        return PostgresJson.jsonb(values == null ? List.of() : values);
    }

    private List<Map<String, Object>> jsonList(JSONB value) {
        return PostgresJson.jsonbValue(value, JSON_LIST_OF_MAPS, List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
        }
        if (value instanceof JSONB jsonb) {
            return PostgresJson.jsonbValue(jsonb, JSON_LIST_OF_MAPS, List.of());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof JSONB jsonb) {
            return PostgresJson.map(jsonb);
        }
        return Map.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (value instanceof JSONB jsonb) {
            return PostgresJson.stringList(jsonb);
        }
        return List.of();
    }

    private BigDecimal positiveDecimal(Object value, BigDecimal defaultValue) {
        BigDecimal parsed = decimalValue(value);
        return parsed != null && parsed.signum() > 0 ? parsed : defaultValue;
    }

    private int positiveInt(Object value, int defaultValue) {
        BigDecimal parsed = decimalValue(value);
        return parsed != null && parsed.signum() > 0 ? parsed.intValue() : defaultValue;
    }

    private BigDecimal numericOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation numeric value is invalid", exception);
            }
        }
        return null;
    }

    private int intValue(Object value, int defaultValue) {
        BigDecimal parsed = decimalValue(value);
        return parsed == null ? defaultValue : parsed.intValue();
    }

    private Instant instantValue(Object value) {
        return instantValue(value, null);
    }

    private Instant instantValue(Object value, Instant defaultValue) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        return defaultValue;
    }

    private String string(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String text ? text : defaultValue;
    }

    private String taskStatusFromWork(String status) {
        return switch (status) {
            case "ready" -> "open";
            case "claimed" -> "claimed";
            case "submitted" -> "proof_submitted";
            case "accepted" -> "accepted";
            case "revision_requested" -> "changes_requested";
            default -> "settled";
        };
    }

    private String runId(String taskId) {
        return "wr-" + taskId;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record ValidationPolicy(
            int minParticipantCount,
            BigDecimal minEffectiveValidationCount,
            BigDecimal sharesPerEffectiveValidator
    ) {
    }
}
