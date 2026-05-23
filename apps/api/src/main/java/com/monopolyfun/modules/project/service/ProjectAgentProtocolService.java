package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.api.request.ProjectAgentActionRequest;
import com.monopolyfun.modules.project.api.request.ReviewProjectResultCandidateRequest;
import com.monopolyfun.modules.project.api.request.SkipProjectCandidateWindowRequest;
import com.monopolyfun.modules.project.api.request.SupportProjectResultCandidateRequest;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectAgentProtocolRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.view.ProjectAgentActionCardView;
import com.monopolyfun.modules.project.service.view.ProjectAgentActionResultView;
import com.monopolyfun.modules.project.service.view.ProjectAgentInboxView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateWindowItemView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateWindowView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class ProjectAgentProtocolService {
    private static final int MAX_INBOX_CARDS = 5;
    private static final int MIN_VALIDATORS = 3;
    private static final double STABLE_DELTA = 3.0;
    private static final int STABLE_ROUNDS = 2;
    private static final int MAX_SHARE_POOL = 10_000;
    private static final double BONDING_GAMMA = 3.0;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_SECTION_FIELDS = 12;
    private static final int MAX_SECTION_DEPTH = 3;
    private static final int MAX_LIST_ITEMS = 12;
    private static final int MAX_ARTIFACTS = 8;
    private static final int MAX_TEXT_VALUE_LENGTH = 1_000;

    private final ProjectRepository projectRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ProjectAgentProtocolRepository protocolRepository;
    private final ProjectDevelopmentService developmentService;

    public ProjectAgentProtocolService(
            ProjectRepository projectRepository,
            OrganizationAuthorityService organizationAuthorityService,
            ProjectAgentProtocolRepository protocolRepository,
            ProjectDevelopmentService developmentService) {
        this.projectRepository = projectRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.protocolRepository = protocolRepository;
        this.developmentService = developmentService;
    }

    public ProjectAgentInboxView inbox(String projectNo, String actorAccountId) {
        ProjectEntity project = requireProject(projectNo);
        requireAccess(project.id(), actorAccountId);
        List<PackState> packs = packStates(project.id());
        AgentLevel level = agentLevel(project.id(), actorAccountId);
        List<ProjectAgentActionCardView> cards = new ArrayList<>();
        // 中文注释：Agent inbox 固定输出少量 action card，让 Agent 只消费当前可执行窗口。
        packs.stream()
                .filter(pack -> canRevise(pack, actorAccountId))
                .limit(1)
                .forEach(pack -> cards.add(reviseCard(pack)));
        packs.stream()
                .filter(pack -> canScore(pack, actorAccountId))
                .limit(3)
                .forEach(pack -> cards.add(scoreCard(pack)));
        packs.stream()
                .filter(pack -> canResultReview(pack, actorAccountId))
                .limit(1)
                .forEach(pack -> cards.add(resultReviewCard(pack)));
        packs.stream()
                .filter(pack -> canFinalReview(pack, actorAccountId, level))
                .limit(1)
                .forEach(pack -> cards.add(finalReviewCard(pack)));
        if (cards.size() < MAX_INBOX_CARDS && canSubmitPack(level, packs)) {
            cards.add(submitPackCard());
        }
        if (cards.size() < MAX_INBOX_CARDS) {
            cards.addAll(candidateActionCards(projectNo, actorAccountId, MAX_INBOX_CARDS - cards.size()));
        }
        return new ProjectAgentInboxView(
                Map.of("projectNo", project.projectNo(), "goal", firstText(metadataText(project, "goal"), project.oneSentence(), project.title())),
                Map.of(
                        "level", level.code(),
                        "canVerify", true,
                        "canSubmitPack", canSubmitPack(level, packs),
                        "canClaimWork", level != AgentLevel.NEW),
                cards.stream().limit(MAX_INBOX_CARDS).toList());
    }

    public ProjectAgentActionResultView handleAction(String projectNo, ProjectAgentActionRequest request, String actorAccountId) {
        ProjectEntity project = requireProject(projectNo);
        requireAccess(project.id(), actorAccountId);
        String actionType = requiredText(request.actionType(), "actionType");
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        ProjectAgentActionResultView result = switch (actionType) {
            case "submit_pack" -> submitPack(project, payload, actorAccountId, actionType);
            case "revise_pack" -> revisePack(project, payload, actorAccountId, actionType);
            case "score_review" -> scoreReview(project, payload, actorAccountId, actionType);
            case "challenge_pack" -> challengePack(project, payload, actorAccountId, actionType);
            case "result_review" -> resultReview(project, payload, actorAccountId, actionType);
            case "final_review" -> finalReview(project, payload, actorAccountId, actionType);
            case "support_candidate" -> supportCandidate(project.projectNo(), payload, actorAccountId, actionType);
            case "final_review_candidate" ->
                    finalReviewCandidate(project.projectNo(), payload, actorAccountId, actionType);
            case "skip_candidate" -> skipCandidate(project.projectNo(), payload, actorAccountId, actionType);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported agent action");
        };
        return new ProjectAgentActionResultView(
                result.accepted(),
                result.actionType(),
                result.packId(),
                result.status(),
                result.result(),
                inbox(projectNo, actorAccountId));
    }

    private ProjectAgentActionResultView submitPack(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = "pack-" + UUID.randomUUID();
        Map<String, Object> work = requiredCompactMap(payload.get("work"), "work");
        Map<String, Object> implementation = requiredCompactMap(payload.get("implementation"), "implementation");
        List<Map<String, Object>> artifacts = listOfCompactMaps(payload.get("artifacts"), "artifacts", MAX_ARTIFACTS);
        if (artifacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifacts are required");
        }
        validateImplementationEvidence(implementation, artifacts);
        Map<String, Object> initialImpact = requiredMap(payload.get("initialImpact"), "initialImpact");
        double initialScore = impactScore(initialImpact);
        // 中文注释：Agent 可以提交完整实现包，inbox 只暴露摘要，避免把大 payload 塞进后续上下文。
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("packId", packId);
        pack.put("projectId", project.id());
        pack.put("authorAccountId", actorAccountId);
        pack.put("title", requiredText(payload.get("title"), "title", MAX_TITLE_LENGTH));
        pack.put("summary", requiredText(payload.get("summary"), "summary", MAX_SUMMARY_LENGTH));
        pack.put("work", work);
        pack.put("implementation", implementation);
        pack.put("artifacts", artifacts);
        pack.put("artifactCount", artifacts.size());
        pack.put("initialImpact", initialImpact);
        pack.put("initialImpactScore", round(initialScore));
        pack.put("revision", 0);
        protocolRepository.saveProposalPack(project.id(), packId, actorAccountId, pack);
        return simpleResult(actionType, packId, "scoring", Map.of("initialImpactScore", round(initialScore)));
    }

    private ProjectAgentActionResultView revisePack(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = requiredText(payload.get("packId"), "packId");
        PackState pack = requirePack(project.id(), packId);
        if (!actorAccountId.equals(pack.authorAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only pack author can revise the pack");
        }
        if (!List.of("challenged", "rejected").contains(pack.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pack can be revised after challenge or rejection");
        }
        Map<String, Object> implementation = requiredCompactMap(payload.get("implementation"), "implementation");
        List<Map<String, Object>> artifacts = listOfCompactMaps(payload.get("artifacts"), "artifacts", MAX_ARTIFACTS);
        if (artifacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifacts are required");
        }
        validateImplementationEvidence(implementation, artifacts);
        int nextRevision = pack.revision() + 1;
        Map<String, Object> revision = new LinkedHashMap<>();
        revision.put("packId", packId);
        revision.put("revision", nextRevision);
        revision.put("parentRevision", pack.revision());
        revision.put("accountId", actorAccountId);
        revision.put("reason", requiredText(payload.get("reason"), "reason", MAX_SUMMARY_LENGTH));
        revision.put("summary", optionalLimitedText(payload.get("summary"), MAX_SUMMARY_LENGTH, pack.summary()));
        revision.put("work", payload.containsKey("work") ? requiredCompactMap(payload.get("work"), "work") : pack.work());
        revision.put("implementation", implementation);
        revision.put("artifacts", artifacts);
        revision.put("artifactCount", artifacts.size());
        protocolRepository.savePackEvent(project.id(), packId, actorAccountId, "project_proposal_pack_revision",
                "proposal_pack_revised", "project.agent.revise_pack", revision);
        return simpleResult(actionType, packId, "scoring", Map.of("revision", nextRevision));
    }

    private ProjectAgentActionResultView scoreReview(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = requiredText(payload.get("packId"), "packId");
        PackState pack = requirePack(project.id(), packId);
        if (actorAccountId.equals(pack.authorAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pack author cannot score the same pack");
        }
        Map<String, Object> scores = requiredMap(payload.get("scores"), "scores");
        double impactScore = impactScore(scores);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("packId", packId);
        review.put("accountId", actorAccountId);
        review.put("choice", normalizeChoice(text(payload.get("choice")), "endorse"));
        review.put("revision", pack.revision());
        review.put("scope", score(scores, "scope"));
        review.put("complexity", score(scores, "complexity"));
        review.put("leverage", score(scores, "leverage"));
        review.put("evidence", score(scores, "evidence"));
        review.put("impactScore", round(impactScore));
        review.put("reason", optionalText(payload.get("reason")));
        protocolRepository.savePackEvent(project.id(), packId, actorAccountId, "project_proposal_pack_score_review",
                "proposal_pack_scored", "project.agent.score_review", review);
        PackState updated = requirePack(project.id(), packId);
        return simpleResult(actionType, packId, updated.status(), Map.of("currentImpactScore", round(updated.currentScore())));
    }

    private ProjectAgentActionResultView challengePack(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = requiredText(payload.get("packId"), "packId");
        PackState pack = requirePack(project.id(), packId);
        if (actorAccountId.equals(pack.authorAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pack author cannot challenge the same pack");
        }
        Map<String, Object> challenge = Map.of(
                "packId", packId,
                "accountId", actorAccountId,
                "revision", pack.revision(),
                "reason", requiredText(payload.get("reason"), "reason"),
                "suggestedPatch", payload.containsKey("suggestedPatch") ? compactValue(payload.get("suggestedPatch"), "suggestedPatch", 0) : Map.of());
        protocolRepository.savePackEvent(project.id(), packId, actorAccountId, "project_proposal_pack_challenge",
                "proposal_pack_challenged", "project.agent.challenge_pack", challenge);
        return simpleResult(actionType, packId, "challenged", Map.of("challengeCount", pack.challengeCount() + 1));
    }

    private ProjectAgentActionResultView resultReview(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = requiredText(payload.get("packId"), "packId");
        PackState pack = requirePack(project.id(), packId);
        if (actorAccountId.equals(pack.authorAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pack author cannot certify the same pack");
        }
        String decision = normalizeDecision(text(payload.get("decision")));
        if ("accepted".equals(decision) && !pack.stable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stable score is required before result certification");
        }
        Map<String, Object> review = Map.of(
                "packId", packId,
                "accountId", actorAccountId,
                "revision", pack.revision(),
                "decision", decision,
                "reason", optionalText(payload.get("reason")));
        protocolRepository.savePackEvent(project.id(), packId, actorAccountId, "project_proposal_pack_result_review",
                "proposal_pack_result_reviewed", "project.agent.result_review", review);
        PackState updated = requirePack(project.id(), packId);
        settleAcceptedPack(project.id(), updated);
        return simpleResult(actionType, packId, updated.status(), Map.of("certified", updated.certified()));
    }

    private ProjectAgentActionResultView finalReview(ProjectEntity project, Map<String, Object> payload, String actorAccountId, String actionType) {
        String packId = requiredText(payload.get("packId"), "packId");
        PackState pack = requirePack(project.id(), packId);
        if (actorAccountId.equals(pack.authorAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pack author cannot final-review the same pack");
        }
        String decision = normalizeDecision(text(payload.get("decision")));
        if ("accepted".equals(decision)) {
            if (!pack.stable() || !pack.certified()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Certified stable pack is required before final review");
            }
            validateFinalEvidenceSnapshot(pack, payload);
        }
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("packId", packId);
        review.put("accountId", actorAccountId);
        review.put("revision", pack.revision());
        review.put("decision", decision);
        review.put("reason", optionalText(payload.get("reason")));
        review.put("reviewedHeadSha", optionalText(payload.get("reviewedHeadSha")));
        review.put("reviewedArtifacts", pack.artifactCount());
        protocolRepository.savePackEvent(project.id(), packId, actorAccountId, "project_proposal_pack_final_review",
                "proposal_pack_final_reviewed", "project.agent.final_review", review);
        PackState updated = requirePack(project.id(), packId);
        settleAcceptedPack(project.id(), updated);
        return simpleResult(actionType, packId, updated.status(), Map.of("sharePool", updated.sharePool()));
    }

    private ProjectAgentActionResultView supportCandidate(String projectNo, Map<String, Object> payload, String actorAccountId, String actionType) {
        String taskId = requiredText(payload.get("taskId"), "taskId", 160);
        Integer prNumber = integer(payload.get("prNumber"));
        ProjectResultCandidateView candidate = developmentService.supportCandidate(
                projectNo,
                taskId,
                new SupportProjectResultCandidateRequest(prNumber, optionalText(payload.get("reason"))),
                actorAccountId);
        return simpleResult(actionType, candidate.candidateId(), candidate.consensusStatus(), Map.of("supportCount", candidate.supportCount()));
    }

    private ProjectAgentActionResultView finalReviewCandidate(String projectNo, Map<String, Object> payload, String actorAccountId, String actionType) {
        String taskId = requiredText(payload.get("taskId"), "taskId", 160);
        Integer prNumber = integer(payload.get("prNumber"));
        String decision = normalizeDecision(text(payload.get("decision")));
        ProjectResultCandidateView candidate = developmentService.finalReviewCandidate(
                projectNo,
                taskId,
                new ReviewProjectResultCandidateRequest(prNumber, decision, optionalText(payload.get("reason"))),
                actorAccountId);
        return simpleResult(actionType, candidate.candidateId(), candidate.consensusStatus(), Map.of("finalReviewStatus", candidate.finalReviewStatus()));
    }

    private ProjectAgentActionResultView skipCandidate(String projectNo, Map<String, Object> payload, String actorAccountId, String actionType) {
        String candidateId = requiredText(payload.get("candidateId"), "candidateId", 180);
        ProjectResultCandidateWindowView window = developmentService.skipCandidateWindow(
                projectNo,
                new SkipProjectCandidateWindowRequest(
                        List.of(candidateId),
                        optionalLimitedText(payload.get("reasonCode"), 80, "agent_skip"),
                        optionalText(payload.get("reason")),
                        integer(payload.get("ttlMinutes"))),
                actorAccountId);
        return simpleResult(actionType, candidateId, "skipped", Map.of("remaining", window.current().size()));
    }

    private void settleAcceptedPack(String projectId, PackState pack) {
        if (!"accepted".equals(pack.status()) || protocolRepository.hasShareAllocation(pack.packId())) {
            return;
        }
        // 中文注释：通过 bonding curve 把连续分数转为虚拟股份池，高分 Pack 获得非线性更高权重。
        int pool = Math.max(1, pack.sharePool());
        int authorAmount = (int) Math.round(pool * 0.75);
        int finalAmount = (int) Math.round(pool * 0.05);
        int validatorAmount = Math.max(0, pool - authorAmount - finalAmount);
        saveAllocation(projectId, pack.packId(), pack.authorAccountId(), "author", authorAmount, pack.finalScore());
        String finalReviewer = pack.finalReviewerAccountId();
        if (!blank(finalReviewer)) {
            saveAllocation(projectId, pack.packId(), finalReviewer, "final_reviewer", finalAmount, pack.finalScore());
        }
        Map<String, Double> weights = validatorWeights(pack);
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) {
            return;
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(weights.entrySet());
        int remaining = validatorAmount;
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, Double> entry = entries.get(index);
            int amount = index == entries.size() - 1
                    ? remaining
                    : (int) Math.round(validatorAmount * entry.getValue() / totalWeight);
            if (amount > 0) {
                saveAllocation(projectId, pack.packId(), entry.getKey(), "validator", amount, pack.finalScore());
                remaining -= amount;
            }
        }
    }

    private void saveAllocation(String projectId, String packId, String accountId, String role, int amount, double finalScore) {
        protocolRepository.saveShareLedgerEntry(projectId, packId, accountId, role, amount, (int) Math.round(finalScore));
        Map<String, Object> allocation = Map.of(
                "packId", packId,
                "projectId", projectId,
                "accountId", accountId,
                "role", role,
                "amount", amount,
                "finalImpactScore", round(finalScore));
        protocolRepository.savePackEvent(projectId, packId, accountId, "project_proposal_pack_share_allocation",
                "proposal_pack_shares_allocated", "project.agent.share_allocation", allocation);
    }

    private Map<String, Double> validatorWeights(PackState pack) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map<String, Object> review : pack.scoreReviews()) {
            String accountId = text(review.get("accountId"));
            if (blank(accountId)) {
                continue;
            }
            double score = number(review.get("impactScore"), pack.finalScore());
            weights.put(accountId, 1.0 / (1.0 + Math.abs(score - pack.finalScore())));
        }
        return weights;
    }

    private ProjectAgentActionResultView simpleResult(String actionType, String packId, String status, Map<String, Object> result) {
        return new ProjectAgentActionResultView(true, actionType, packId, status, result, null);
    }

    private boolean canSubmitPack(AgentLevel level, List<PackState> packs) {
        return level != AgentLevel.NEW || packs.isEmpty();
    }

    private boolean canRevise(PackState pack, String actorAccountId) {
        return actorAccountId.equals(pack.authorAccountId()) && List.of("challenged", "rejected").contains(pack.status());
    }

    private boolean canScore(PackState pack, String actorAccountId) {
        return !actorAccountId.equals(pack.authorAccountId())
                && List.of("submitted", "scoring").contains(pack.status())
                && pack.scoreReviews().stream().noneMatch(review -> actorAccountId.equals(text(review.get("accountId"))));
    }

    private boolean canResultReview(PackState pack, String actorAccountId) {
        return !actorAccountId.equals(pack.authorAccountId()) && pack.stable() && !pack.certified();
    }

    private boolean canFinalReview(PackState pack, String actorAccountId, AgentLevel level) {
        return level != AgentLevel.NEW
                && !actorAccountId.equals(pack.authorAccountId())
                && pack.stable()
                && pack.certified()
                && !pack.finalReviewed();
    }

    private ProjectAgentActionCardView submitPackCard() {
        return new ProjectAgentActionCardView(
                "submit-pack",
                "submit_pack",
                "提交带实现的 ProposalPack",
                null,
                Map.of("mode", "proposal_pack_with_implementation"),
                List.of("title", "summary", "work", "implementation", "artifacts", "initialImpact"));
    }

    private ProjectAgentActionCardView reviseCard(PackState pack) {
        return new ProjectAgentActionCardView(
                "revise-" + pack.packId(),
                "revise_pack",
                pack.title(),
                pack.packId(),
                Map.of("summary", pack.summary(), "revision", pack.revision(), "challengeCount", pack.challengeCount()),
                List.of("packId", "reason", "implementation", "artifacts"));
    }

    private ProjectAgentActionCardView scoreCard(PackState pack) {
        return new ProjectAgentActionCardView(
                "score-" + pack.packId(),
                "score_review",
                pack.title(),
                pack.packId(),
                Map.of(
                        "summary", pack.summary(),
                        "currentScore", round(pack.currentScore()),
                        "validators", pack.validatorCount(),
                        "artifactCount", pack.artifactCount()),
                List.of("packId", "choice", "scores", "reason"));
    }

    private ProjectAgentActionCardView resultReviewCard(PackState pack) {
        return new ProjectAgentActionCardView(
                "result-review-" + pack.packId(),
                "result_review",
                pack.title(),
                pack.packId(),
                Map.of("summary", pack.summary(), "score", round(pack.currentScore()), "artifactCount", pack.artifactCount()),
                List.of("packId", "decision", "reason"));
    }

    private ProjectAgentActionCardView finalReviewCard(PackState pack) {
        return new ProjectAgentActionCardView(
                "final-review-" + pack.packId(),
                "final_review",
                pack.title(),
                pack.packId(),
                Map.of(
                        "score", round(pack.finalScore()),
                        "sharePool", pack.sharePool(),
                        "validators", pack.validatorCount(),
                        "codeHeadSha", firstText(pack.codeHeadSha())),
                List.of("packId", "decision", "reason", "reviewedHeadSha"));
    }

    private List<ProjectAgentActionCardView> candidateActionCards(String projectNo, String actorAccountId, int limit) {
        ProjectResultCandidateWindowView window = developmentService.nextCandidateWindow(projectNo, limit, null, actorAccountId);
        return window.current().stream().map(this::candidateActionCard).toList();
    }

    private ProjectAgentActionCardView candidateActionCard(ProjectResultCandidateWindowItemView item) {
        String type = switch (item.nextAction()) {
            case "support" -> "support_candidate";
            case "final_review" -> "final_review_candidate";
            default -> "skip_candidate";
        };
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("taskId", item.taskId());
        context.put("resultType", item.resultType());
        context.put("status", item.candidateStatus());
        context.put("consensus", item.consensusStatus());
        context.put("support", item.supportCount() + "/" + item.supportThreshold());
        context.put("reasonToAct", item.reasonToAct());
        if (item.prNumber() != null) {
            context.put("prNumber", item.prNumber());
        }
        if (!blank(item.headSha())) {
            context.put("headSha", item.headSha());
        }
        return new ProjectAgentActionCardView(
                "candidate-" + item.candidateId(),
                type,
                item.taskTitle(),
                item.candidateId(),
                context,
                "final_review_candidate".equals(type)
                        ? List.of("taskId", "decision", "reason")
                        : List.of("taskId", "reason"));
    }

    private List<PackState> packStates(String projectId) {
        List<Map<String, Object>> packs = protocolRepository.findProposalPacks(projectId);
        List<Map<String, Object>> scores = protocolRepository.findPackEvents(projectId, "project_proposal_pack_score_review");
        List<Map<String, Object>> challenges = protocolRepository.findPackEvents(projectId, "project_proposal_pack_challenge");
        List<Map<String, Object>> revisions = protocolRepository.findPackEvents(projectId, "project_proposal_pack_revision");
        List<Map<String, Object>> resultReviews = protocolRepository.findPackEvents(projectId, "project_proposal_pack_result_review");
        List<Map<String, Object>> finalReviews = protocolRepository.findPackEvents(projectId, "project_proposal_pack_final_review");
        List<PackState> states = new ArrayList<>();
        for (Map<String, Object> pack : packs) {
            String packId = text(pack.get("packId"));
            states.add(new PackState(
                    pack,
                    filterByPack(revisions, packId),
                    filterByPack(scores, packId),
                    filterByPack(challenges, packId),
                    filterByPack(resultReviews, packId),
                    filterByPack(finalReviews, packId)));
        }
        return states.stream()
                .sorted(Comparator.comparing(PackState::statusRank).reversed().thenComparing(PackState::title))
                .toList();
    }

    private PackState requirePack(String projectId, String packId) {
        return packStates(projectId).stream()
                .filter(pack -> packId.equals(pack.packId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ProposalPack not found"));
    }

    private AgentLevel agentLevel(String projectId, String actorAccountId) {
        long scoreReviews = protocolRepository.findPackEvents(projectId, "project_proposal_pack_score_review").stream()
                .filter(review -> actorAccountId.equals(text(review.get("accountId"))))
                .count();
        if (scoreReviews >= 3) {
            return AgentLevel.REVIEWER;
        }
        if (scoreReviews >= 1) {
            return AgentLevel.CONTRIBUTOR;
        }
        return AgentLevel.NEW;
    }

    private List<Map<String, Object>> filterByPack(List<Map<String, Object>> values, String packId) {
        return values.stream().filter(value -> packId.equals(text(value.get("packId")))).toList();
    }

    private List<Map<String, Object>> filterByRevision(List<Map<String, Object>> values, int revision) {
        return values.stream().filter(value -> revision == (int) number(value.get("revision"), 0)).toList();
    }

    private double impactScore(Map<String, Object> scores) {
        return 0.30 * score(scores, "scope")
                + 0.25 * score(scores, "complexity")
                + 0.25 * score(scores, "leverage")
                + 0.20 * score(scores, "evidence");
    }

    private double score(Map<String, Object> scores, String key) {
        double value = number(scores.get(key), -1);
        if (value < 0 || value > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " score must be between 0 and 100");
        }
        return value;
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private ProjectEntity requireProject(String projectNo) {
        if (blank(projectNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectNo is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void requireAccess(String projectId, String actorAccountId) {
        if (!organizationAuthorityService.hasProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_PARTICIPATE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project participation required");
        }
    }

    private Map<String, Object> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, Object> requiredCompactMap(Object value, String field) {
        return compactMap(requiredMap(value, field), field, 0);
    }

    private List<Map<String, Object>> listOfCompactMaps(Object value, String field, int maxItems) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        if (raw.size() > maxItems) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " supports at most " + maxItems + " items");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
                result.add(compactMap(normalized, field, 0));
            }
        }
        return result;
    }

    private Map<String, Object> compactMap(Map<String, Object> raw, String field, int depth) {
        if (raw.size() > MAX_SECTION_FIELDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " supports at most " + MAX_SECTION_FIELDS + " fields");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, compactValue(value, field + "." + key, depth)));
        return result;
    }

    private Object compactValue(Object value, String field, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return limitedText(text, field, MAX_TEXT_VALUE_LENGTH);
        }
        if (value instanceof Map<?, ?> map) {
            if (depth >= MAX_SECTION_DEPTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too deeply nested");
            }
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
            return compactMap(normalized, field, depth + 1);
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_LIST_ITEMS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " supports at most " + MAX_LIST_ITEMS + " items");
            }
            List<Object> values = new ArrayList<>();
            for (Object item : list) {
                values.add(compactValue(item, field, depth + 1));
            }
            return values;
        }
        return limitedText(String.valueOf(value), field, MAX_TEXT_VALUE_LENGTH);
    }

    private void validateImplementationEvidence(Map<String, Object> implementation, List<Map<String, Object>> artifacts) {
        if (!"code".equalsIgnoreCase(text(implementation.get("type")))) {
            return;
        }
        Map<String, Object> pullRequest = artifacts.stream()
                .filter(artifact -> "pull_request".equalsIgnoreCase(text(artifact.get("kind"))))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "code implementation requires pull_request artifact"));
        requiredText(pullRequest.get("url"), "artifacts.pull_request.url", 500);
        requiredText(pullRequest.get("headSha"), "artifacts.pull_request.headSha", 120);
        if (!Boolean.TRUE.equals(pullRequest.get("mergeable"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code pull_request artifact must be mergeable");
        }
        if (!"success".equalsIgnoreCase(text(pullRequest.get("checksConclusion")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code pull_request artifact requires successful checks");
        }
    }

    private void validateFinalEvidenceSnapshot(PackState pack, Map<String, Object> payload) {
        if (!pack.codeImplementation()) {
            return;
        }
        String reviewedHeadSha = requiredText(payload.get("reviewedHeadSha"), "reviewedHeadSha", 120);
        if (!reviewedHeadSha.equals(pack.codeHeadSha())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "reviewedHeadSha must match current code artifact headSha");
        }
    }

    private String requiredText(Object value, String field) {
        String text = text(value);
        if (blank(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return text.trim();
    }

    private String requiredText(Object value, String field, int maxLength) {
        String text = requiredText(value, field);
        return limitedText(text, field, maxLength);
    }

    private String limitedText(String value, String field, int maxLength) {
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " supports at most " + maxLength + " characters");
        }
        return normalized;
    }

    private String optionalText(Object value) {
        String text = text(value);
        return text == null ? "" : text.trim();
    }

    private String optionalLimitedText(Object value, int maxLength, String fallback) {
        String text = text(value);
        if (blank(text)) {
            return fallback;
        }
        return limitedText(text, "text", maxLength);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeChoice(String value, String fallback) {
        String normalized = blank(value) ? fallback : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("endorse", "abstain").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "choice must be endorse or abstain");
        }
        return normalized;
    }

    private String normalizeDecision(String value) {
        String normalized = blank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("accepted", "rejected").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be accepted or rejected");
        }
        return normalized;
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return fallback;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        return blank(text) ? null : Integer.parseInt(text);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return "";
    }

    private String metadataText(ProjectEntity project, String key) {
        Object value = project.metadata() == null ? null : project.metadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private enum AgentLevel {
        NEW("new"),
        CONTRIBUTOR("contributor"),
        REVIEWER("reviewer");

        private final String code;

        AgentLevel(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private final class PackState {
        private final Map<String, Object> pack;
        private final List<Map<String, Object>> revisions;
        private final List<Map<String, Object>> scoreReviews;
        private final List<Map<String, Object>> challenges;
        private final List<Map<String, Object>> resultReviews;
        private final List<Map<String, Object>> finalReviews;

        PackState(
                Map<String, Object> pack,
                List<Map<String, Object>> revisions,
                List<Map<String, Object>> scoreReviews,
                List<Map<String, Object>> challenges,
                List<Map<String, Object>> resultReviews,
                List<Map<String, Object>> finalReviews) {
            this.revisions = revisions;
            this.pack = effectivePack(pack, revisions);
            int currentRevision = revision();
            this.scoreReviews = filterByRevision(scoreReviews, currentRevision);
            this.challenges = filterByRevision(challenges, currentRevision);
            this.resultReviews = filterByRevision(resultReviews, currentRevision);
            this.finalReviews = filterByRevision(finalReviews, currentRevision);
        }

        private Map<String, Object> effectivePack(Map<String, Object> base, List<Map<String, Object>> revisions) {
            if (revisions.isEmpty()) {
                return base;
            }
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(base);
            Map<String, Object> latest = revisions.getLast();
            for (String key : List.of("summary", "work", "implementation", "artifacts", "artifactCount", "revision", "parentRevision", "reason")) {
                if (latest.containsKey(key)) {
                    result.put(key, latest.get(key));
                }
            }
            return result;
        }

        String packId() {
            return text(pack.get("packId"));
        }

        String title() {
            return text(pack.get("title"));
        }

        String summary() {
            return text(pack.get("summary"));
        }

        String authorAccountId() {
            return text(pack.get("authorAccountId"));
        }

        int revision() {
            return (int) number(pack.get("revision"), 0);
        }

        Map<String, Object> work() {
            return requiredMap(pack.get("work"), "work");
        }

        int artifactCount() {
            return (int) number(pack.get("artifactCount"), listOfMaps(pack.get("artifacts")).size());
        }

        boolean codeImplementation() {
            return "code".equalsIgnoreCase(text(requiredMap(pack.get("implementation"), "implementation").get("type")));
        }

        String codeHeadSha() {
            return listOfMaps(pack.get("artifacts")).stream()
                    .filter(artifact -> "pull_request".equalsIgnoreCase(text(artifact.get("kind"))))
                    .map(artifact -> text(artifact.get("headSha")))
                    .filter(value -> !blank(value))
                    .findFirst()
                    .orElse("");
        }

        List<Map<String, Object>> scoreReviews() {
            return scoreReviews;
        }

        int validatorCount() {
            return (int) scoreReviews.stream().map(review -> text(review.get("accountId"))).filter(Objects::nonNull).distinct().count();
        }

        int challengeCount() {
            return challenges.size();
        }

        double currentScore() {
            List<Double> values = scoreReviews.stream().map(review -> number(review.get("impactScore"), 0)).toList();
            return values.isEmpty() ? number(pack.get("initialImpactScore"), 0) : median(values);
        }

        boolean stable() {
            if (validatorCount() < MIN_VALIDATORS) {
                return false;
            }
            // 中文注释：连续中位数收敛代表验证者分歧变小，后续认证才可以推进到结算。
            List<Double> runningScores = new ArrayList<>();
            List<Double> medians = new ArrayList<>();
            for (Map<String, Object> review : scoreReviews) {
                runningScores.add(number(review.get("impactScore"), 0));
                medians.add(median(runningScores));
            }
            if (medians.size() <= STABLE_ROUNDS) {
                return false;
            }
            int stable = 0;
            for (int index = 1; index < medians.size(); index++) {
                if (Math.abs(medians.get(index) - medians.get(index - 1)) <= STABLE_DELTA) {
                    stable++;
                } else {
                    stable = 0;
                }
            }
            return stable >= STABLE_ROUNDS;
        }

        boolean certified() {
            return resultReviews.stream().anyMatch(review -> "accepted".equals(text(review.get("decision"))));
        }

        boolean finalReviewed() {
            return finalReviews.stream().anyMatch(review -> "accepted".equals(text(review.get("decision"))));
        }

        boolean rejected() {
            return resultReviews.stream().anyMatch(review -> "rejected".equals(text(review.get("decision"))))
                    || finalReviews.stream().anyMatch(review -> "rejected".equals(text(review.get("decision"))));
        }

        String finalReviewerAccountId() {
            return finalReviews.stream()
                    .filter(review -> "accepted".equals(text(review.get("decision"))))
                    .map(review -> text(review.get("accountId")))
                    .filter(value -> !blank(value))
                    .findFirst()
                    .orElse(null);
        }

        double finalScore() {
            return currentScore();
        }

        int sharePool() {
            double normalized = Math.max(0, Math.min(100, finalScore())) / 100.0;
            return (int) Math.round(MAX_SHARE_POOL * Math.pow(normalized, BONDING_GAMMA));
        }

        String status() {
            if (rejected()) {
                return "rejected";
            }
            if (challengeCount() > 0) {
                return "challenged";
            }
            if (stable() && certified() && finalReviewed()) {
                return "accepted";
            }
            if (stable() && certified()) {
                return "certified";
            }
            if (stable()) {
                return "score_stable";
            }
            return "scoring";
        }

        int statusRank() {
            return switch (status()) {
                case "score_stable", "certified" -> 100;
                case "scoring" -> 80;
                case "challenged" -> 40;
                case "rejected" -> 30;
                case "accepted" -> 20;
                default -> 0;
            };
        }
    }
}
