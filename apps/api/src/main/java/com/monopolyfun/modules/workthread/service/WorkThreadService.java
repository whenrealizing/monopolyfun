package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.api.request.ClaimWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.CreateWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.ReviewWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.SubmitWorkThreadResultRequest;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadReviewEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import com.monopolyfun.modules.workthread.service.view.WorkResultView;
import com.monopolyfun.modules.workthread.service.view.ContributionLedgerEntryView;
import com.monopolyfun.modules.workthread.service.view.ContributionMemberView;
import com.monopolyfun.modules.workthread.service.view.ContributionRewardView;
import com.monopolyfun.modules.workthread.service.view.DistributionBatchView;
import com.monopolyfun.modules.workthread.service.view.ProjectRevenueAddressView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadOverviewView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadPacketView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadView;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkThreadService {
    private final WorkThreadRepository repository;
    private final ProjectRepository projectRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final WorkThreadMarkdown markdown;
    private final ContributionSettlementService contributionSettlementService;

    public WorkThreadService(
            WorkThreadRepository repository,
            ProjectRepository projectRepository,
            CurrentAccountAccess currentAccountAccess,
            WorkThreadMarkdown markdown,
            ContributionSettlementService contributionSettlementService) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.markdown = markdown;
        this.contributionSettlementService = contributionSettlementService;
    }

    public WorkThreadView create(String projectIdOrNo, CreateWorkThreadRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        ProjectEntity project = requireProject(projectIdOrNo);
        requireProjectOwner(project, request.actorAccountId());
        validateCreate(request);
        Instant now = repository.now();
        WorkThreadEntity thread = repository.saveThread(new WorkThreadEntity(
                "wt-" + UUID.randomUUID(),
                "wt-" + now.toEpochMilli(),
                project.id(),
                request.actorAccountId(),
                null,
                blankToNull(request.reviewerAccountId()),
                blankToNull(request.issueUrl()),
                blankToNull(request.repoRef()),
                request.title().trim(),
                request.goal().trim(),
                cleaned(request.deliverables()),
                cleaned(request.acceptanceCriteria()),
                request.taskValue(),
                request.bountyAmountMinor() == null ? 0 : request.bountyAmountMinor(),
                request.bountyToken() == null || request.bountyToken().isBlank() ? "USDC" : request.bountyToken().trim(),
                "open",
                now,
                now,
                null,
                null,
                null));
        return toView(thread);
    }

    public List<WorkThreadView> list(String projectIdOrNo) {
        ProjectEntity project = requireProject(projectIdOrNo);
        // 中文注释：列表视图附带最新 Result，让前端可以直接展示提交和验收状态。
        return repository.listThreadsByProject(project.id()).stream()
                .map(thread -> toView(thread, repository.findLatestResult(thread.id()).map(this::toView).orElse(null)))
                .toList();
    }

    public WorkThreadOverviewView overview(String projectIdOrNo) {
        ProjectEntity project = requireProject(projectIdOrNo);
        String currentAccountId = currentAccountAccess.current().map(account -> account.accountId()).orElse(null);
        List<WorkThreadView> workThreads = list(project.id());
        List<ContributionEntryEntity> contributions = repository.listContributionsByProject(project.id());
        int myShares = currentAccountId == null ? 0 : repository.sumSharesByProjectAndAccount(project.id(), currentAccountId);
        int myBounty = currentAccountId == null ? 0 : repository.sumBountyByProjectAndAccount(project.id(), currentAccountId);
        List<DistributionBatchView> distributions = repository.listDistributionBatches(project.id()).stream()
                .map(batch -> toView(batch, claimable(batch, myShares)))
                .toList();
        int currentClaimable = distributions.stream()
                .filter(batch -> "published".equals(batch.status()))
                .findFirst()
                .map(DistributionBatchView::myClaimableAmountMinor)
                .orElse(0);
        return new WorkThreadOverviewView(
                project.id(),
                project.projectNo(),
                currentAccountId != null && currentAccountId.equals(project.ownerAccountId()),
                repository.findActiveRevenueAddress(project.id()).map(this::toView).orElse(null),
                new ContributionRewardView(myShares, myBounty, "USDC", currentClaimable, "USDC"),
                workThreads,
                contributions.stream().map(this::toView).toList(),
                contributionMembers(contributions),
                distributions);
    }

    public WorkThreadPacketView packet(String idOrNo) {
        WorkThreadEntity thread = requireThread(idOrNo);
        ProjectEntity project = requireProject(thread.projectId());
        return markdown.packet(project, thread);
    }

    public CommandReceipt claim(String idOrNo, ClaimWorkThreadRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        WorkThreadEntity thread = requireThread(idOrNo);
        if (!"open".equals(thread.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work thread cannot be claimed from status " + thread.status());
        }
        Instant now = repository.now();
        WorkThreadEntity claimed = repository.updateThreadState(new WorkThreadEntity(
                thread.id(), thread.threadNo(), thread.projectId(), thread.createdByAccountId(), request.actorAccountId(),
                thread.reviewerAccountId(), thread.issueUrl(), thread.repoRef(), thread.title(), thread.goal(),
                thread.deliverables(), thread.acceptanceCriteria(), thread.taskValue(), thread.bountyAmountMinor(),
                thread.bountyToken(), "running", thread.createdAt(), now, null, null, null));
        return receipt("work_thread", claimed.id(), "running", request.actorAccountId(), Map.of("threadNo", claimed.threadNo()));
    }

    public WorkResultView submitResult(String idOrNo, SubmitWorkThreadResultRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        WorkThreadEntity thread = requireThread(idOrNo);
        if (!"running".equals(thread.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work thread cannot accept result from status " + thread.status());
        }
        if (!request.actorAccountId().equals(thread.assigneeAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only assignee can submit result");
        }
        ParsedSubmission parsed = parseSubmission(thread, request);
        Instant now = repository.now();
        WorkResultEntity result = repository.saveResult(new WorkResultEntity(
                "wtr-" + UUID.randomUUID(),
                "wtr-" + now.toEpochMilli(),
                thread.id(),
                request.actorAccountId(),
                request.resultMarkdown(),
                parsed.summary(),
                parsed.prUrl(),
                parsed.testSummary(),
                parsed.changedFiles(),
                request.evidenceRefs() == null ? List.of() : request.evidenceRefs(),
                request.runtime() == null || request.runtime().isBlank() ? "openclaw" : request.runtime().trim(),
                "submitted",
                now));
        repository.updateThreadState(new WorkThreadEntity(
                thread.id(), thread.threadNo(), thread.projectId(), thread.createdByAccountId(), thread.assigneeAccountId(),
                thread.reviewerAccountId(), thread.issueUrl(), thread.repoRef(), thread.title(), thread.goal(),
                thread.deliverables(), thread.acceptanceCriteria(), thread.taskValue(), thread.bountyAmountMinor(),
                thread.bountyToken(), "submitted", thread.createdAt(), now, now, null, null));
        return toView(result);
    }

    public CommandReceipt review(String idOrNo, ReviewWorkThreadRequest request) {
        currentAccountAccess.requireSameAccount(request.reviewerAccountId());
        WorkThreadEntity thread = requireThread(idOrNo);
        ProjectEntity project = requireProject(thread.projectId());
        requireReviewer(project, thread, request.reviewerAccountId());
        if (!"submitted".equals(thread.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work thread is not submitted");
        }
        String decision = normalizeDecision(request.decision());
        WorkResultEntity result = repository.findLatestResult(thread.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Result required before review"));
        Instant now = repository.now();
        WorkThreadReviewEntity review = repository.saveReview(new WorkThreadReviewEntity(
                "wtrv-" + UUID.randomUUID(),
                "wtrv-" + now.toEpochMilli(),
                thread.id(),
                result.id(),
                request.reviewerAccountId(),
                decision,
                request.reason().trim(),
                now));
        String nextStatus = switch (decision) {
            case "accept" -> "settled";
            case "resubmit" -> "running";
            case "reject" -> "rejected";
            default -> "submitted";
        };
        if ("accept".equals(decision)) {
            contributionSettlementService.settle(thread, result, now);
        }
        repository.updateThreadState(new WorkThreadEntity(
                thread.id(), thread.threadNo(), thread.projectId(), thread.createdByAccountId(), thread.assigneeAccountId(),
                thread.reviewerAccountId(), thread.issueUrl(), thread.repoRef(), thread.title(), thread.goal(),
                thread.deliverables(), thread.acceptanceCriteria(), thread.taskValue(), thread.bountyAmountMinor(),
                thread.bountyToken(), nextStatus, thread.createdAt(), now, thread.submittedAt(),
                "accept".equals(decision) ? now : null, "accept".equals(decision) ? now : null));
        return receipt("work_review", review.reviewNo(), nextStatus, request.reviewerAccountId(), Map.of("decision", decision, "threadNo", thread.threadNo()));
    }

    public ProjectEntity requireProject(String projectIdOrNo) {
        if (projectIdOrNo == null || projectIdOrNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project id is required");
        }
        return projectRepository.findById(projectIdOrNo.trim())
                .or(() -> projectRepository.findByProjectNo(projectIdOrNo.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private WorkThreadEntity requireThread(String idOrNo) {
        return repository.findThread(idOrNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work thread not found"));
    }

    private void requireProjectOwner(ProjectEntity project, String actorAccountId) {
        if (!project.ownerAccountId().equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner required");
        }
    }

    private void requireReviewer(ProjectEntity project, WorkThreadEntity thread, String reviewerAccountId) {
        if (reviewerAccountId.equals(project.ownerAccountId()) || reviewerAccountId.equals(thread.reviewerAccountId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer is not allowed for this work thread");
    }

    private void validateCreate(CreateWorkThreadRequest request) {
        RequestPayloadLimits.requireTextLength("title", request.title(), 160);
        RequestPayloadLimits.requireTextLength("goal", request.goal(), 1000);
        RequestPayloadLimits.requireStringList("deliverables", request.deliverables(), 12, 300);
        RequestPayloadLimits.requireStringList("acceptanceCriteria", request.acceptanceCriteria(), 12, 300);
        if (cleaned(request.deliverables()).isEmpty() || cleaned(request.acceptanceCriteria()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliverables and acceptanceCriteria are required");
        }
        if (request.taskValue() <= 0 || request.taskValue() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskValue must be 1-10000");
        }
    }

    private ParsedSubmission parseSubmission(WorkThreadEntity thread, SubmitWorkThreadResultRequest request) {
        RequestPayloadLimits.requireTextLength("resultMarkdown", request.resultMarkdown(), 12000);
        WorkThreadMarkdown.ParsedResult parsed = markdown.parseResult(request.resultMarkdown());
        String frontmatterThreadId = parsed.frontmatter().getOrDefault("workThreadId", "");
        if (!frontmatterThreadId.isBlank() && !frontmatterThreadId.equals(thread.id()) && !frontmatterThreadId.equals(thread.threadNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "result frontmatter workThreadId does not match");
        }
        String summary = firstNonBlank(request.summary(), parsed.summary());
        String prUrl = firstNonBlank(request.prUrl(), parsed.prUrl());
        String testSummary = firstNonBlank(request.testSummary(), parsed.testSummary());
        List<String> changedFiles = request.changedFiles() == null || request.changedFiles().isEmpty() ? parsed.changedFiles() : request.changedFiles();
        if (summary.length() < 4 || prUrl.isBlank() || testSummary.isBlank() || changedFiles == null || changedFiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "summary, prUrl, testSummary, and changedFiles are required");
        }
        RequestPayloadLimits.requireTextLength("summary", summary, 1000);
        RequestPayloadLimits.requireTextLength("prUrl", prUrl, 500);
        RequestPayloadLimits.requireTextLength("testSummary", testSummary, 1000);
        RequestPayloadLimits.requireStringList("changedFiles", changedFiles, 40, 500);
        RequestPayloadLimits.requireStringList("evidenceRefs", request.evidenceRefs(), 30, 500);
        return new ParsedSubmission(summary, prUrl, testSummary, List.copyOf(changedFiles));
    }

    private static String normalizeDecision(String value) {
        String decision = value == null ? "" : value.trim().toLowerCase();
        return switch (decision) {
            case "accept", "accepted" -> "accept";
            case "resubmit", "revision_requested" -> "resubmit";
            case "reject", "rejected" -> "reject";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be accept, resubmit, or reject");
        };
    }

    private static List<String> cleaned(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? (fallback == null ? "" : fallback.trim()) : preferred.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkThreadView toView(WorkThreadEntity thread) {
        return toView(thread, null);
    }

    private WorkThreadView toView(WorkThreadEntity thread, WorkResultView latestResult) {
        return new WorkThreadView(
                thread.id(), thread.threadNo(), thread.projectId(), thread.createdByAccountId(), thread.assigneeAccountId(), thread.reviewerAccountId(),
                thread.issueUrl(), thread.repoRef(), thread.title(), thread.goal(), thread.deliverables(), thread.acceptanceCriteria(),
                thread.taskValue(), thread.bountyAmountMinor(), thread.bountyToken(), thread.status(),
                iso(thread.createdAt()), iso(thread.updatedAt()), iso(thread.submittedAt()), iso(thread.settledAt()), latestResult);
    }

    private WorkResultView toView(WorkResultEntity result) {
        return new WorkResultView(
                result.id(), result.resultNo(), result.workThreadId(), result.actorAccountId(), result.summary(), result.prUrl(),
                result.testSummary(), result.changedFiles(), result.evidenceRefs(), result.runtime(), result.status(), result.createdAt().toString());
    }

    private ContributionLedgerEntryView toView(ContributionEntryEntity contribution) {
        return new ContributionLedgerEntryView(
                contribution.id(), contribution.projectId(), contribution.workThreadId(), contribution.resultId(), contribution.accountId(),
                contribution.taskValue(), contribution.shares(), contribution.bountyAmountMinor(), contribution.bountyToken(), contribution.status(),
                iso(contribution.createdAt()));
    }

    private ProjectRevenueAddressView toView(ProjectRevenueAddressEntity address) {
        return new ProjectRevenueAddressView(address.id(), address.projectId(), address.chainId(), address.contractAddress(), address.tokenAddress(), address.status());
    }

    private DistributionBatchView toView(DistributionBatchEntity batch, int claimableAmountMinor) {
        return new DistributionBatchView(
                batch.id(), batch.projectId(), batch.period(), batch.totalRevenueMinor(), batch.totalSnapshotShares(),
                batch.merkleRoot(), claimableAmountMinor, "USDC", batch.status(), iso(batch.createdAt()), iso(batch.updatedAt()));
    }

    private List<ContributionMemberView> contributionMembers(List<ContributionEntryEntity> contributions) {
        Map<String, List<ContributionEntryEntity>> byAccount = contributions.stream()
                .filter(contribution -> "settled".equals(contribution.status()))
                .collect(Collectors.groupingBy(ContributionEntryEntity::accountId));
        return byAccount.entrySet().stream()
                .map(entry -> new ContributionMemberView(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(ContributionEntryEntity::shares).sum(),
                        entry.getValue().stream().mapToInt(ContributionEntryEntity::taskValue).sum(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(ContributionEntryEntity::bountyAmountMinor).sum(),
                        entry.getValue().stream().map(ContributionEntryEntity::bountyToken).findFirst().orElse("USDC")))
                .sorted(Comparator.comparingInt(ContributionMemberView::totalShares).reversed())
                .toList();
    }

    private int claimable(DistributionBatchEntity batch, int memberShares) {
        if (batch.totalRevenueMinor() <= 0 || batch.totalSnapshotShares() <= 0 || memberShares <= 0) {
            return 0;
        }
        return (int) Math.floor((double) batch.totalRevenueMinor() * memberShares / batch.totalSnapshotShares());
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private CommandReceipt receipt(String type, String subjectId, String status, String actorAccountId, Map<String, Object> payload) {
        return new CommandReceipt("cr-" + UUID.randomUUID(), type, subjectId, status, new LinkedHashMap<>(payload), actorAccountId, null, null, repository.now());
    }

    private record ParsedSubmission(String summary, String prUrl, String testSummary, List<String> changedFiles) {
    }
}
