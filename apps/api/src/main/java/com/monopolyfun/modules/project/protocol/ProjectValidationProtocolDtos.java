package com.monopolyfun.modules.project.protocol;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ProjectValidationProtocolDtos {
    private ProjectValidationProtocolDtos() {
    }

    public record ProofRequestDraft(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String intent,
            List<Map<String, Object>> evidenceRequirements,
            List<Map<String, Object>> acceptanceSignals,
            @Size(max = 40) String riskLevel,
            Map<String, Object> metadata
    ) {
    }

    public record CreateLaunchRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 2000) String hypothesis,
            List<@Valid ProofRequestDraft> proofRequests,
            @Size(max = 80) String parentLaunchId,
            List<Map<String, Object>> sourceRefs,
            Map<String, Object> metadata
    ) {
    }

    public record UpdateLaunchRequest(
            @Size(max = 160) String title,
            @Size(max = 2000) String hypothesis,
            Map<String, Object> metadata
    ) {
    }

    public record CreateProofRequestRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String intent,
            List<Map<String, Object>> evidenceRequirements,
            List<Map<String, Object>> acceptanceSignals,
            @Size(max = 40) String riskLevel,
            Map<String, Object> metadata
    ) {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String intent,
            List<String> linkedProofRequestIds,
            @NotBlank @Size(max = 2000) String deliverable,
            List<String> acceptanceCriteria,
            List<Map<String, Object>> suggestedEvidence,
            Map<String, Object> rewardPreview,
            @Size(max = 200) String templateRef,
            List<String> tags,
            Map<String, Object> metadata
    ) {
    }

    public record SubmitProofRequest(
            @NotBlank @Size(max = 2000) String summary,
            List<Map<String, Object>> evidenceItems,
            List<String> linkedProofRequestIds,
            @Size(max = 2000) String notes,
            Map<String, Object> metadata
    ) {
    }

    public record ReviewProofRequest(
            @NotBlank @Size(max = 40) String result,
            @Size(max = 2000) String reason,
            @Size(max = 40) String validationMode,
            BigDecimal stakedShares,
            List<Map<String, Object>> requestedEvidence,
            List<String> riskFlags,
            Map<String, Object> scoreInputs,
            Map<String, Object> metadata
    ) {
    }

    public record ProofValidationStatsView(
            int participantCount,
            int minParticipantCount,
            int ordinaryValidationCount,
            int stakedValidationCount,
            BigDecimal stakedShares,
            BigDecimal effectiveValidationCount,
            BigDecimal minEffectiveValidationCount,
            BigDecimal sharesPerEffectiveValidator,
            boolean finalized
    ) {
    }

    public record CreateFeedbackRequest(
            @Size(max = 80) String launchId,
            @NotBlank @Size(max = 80) String subjectType,
            @NotBlank @Size(max = 200) String subjectId,
            @NotBlank @Size(max = 1000) String intent,
            @NotBlank @Size(max = 2000) String reason,
            List<Map<String, Object>> evidence,
            @Size(max = 1000) String suggestedAction,
            Map<String, Object> metadata
    ) {
    }

    public record ResolveFeedbackRequest(
            @NotBlank @Size(max = 40) String status,
            @Size(max = 2000) String resolution,
            Map<String, Object> metadata
    ) {
    }

    public record SettleLaunchRequest(
            @Size(max = 2000) String reason,
            Map<String, Object> scoreSnapshot,
            Map<String, Object> curveSnapshot,
            Map<String, Object> rewardSnapshot,
            Map<String, Object> metadata
    ) {
    }

    public record LaunchView(
            String id,
            String projectId,
            String title,
            String hypothesis,
            String status,
            int version,
            String parentLaunchId,
            List<Map<String, Object>> sourceRefs,
            Map<String, Object> metadata,
            String createdByAccountId,
            String publishedByAccountId,
            String settledByAccountId,
            Instant createdAt,
            Instant publishedAt,
            Instant settledAt,
            Instant updatedAt,
            List<ProofRequestView> proofRequests
    ) {
    }

    public record ProofRequestView(
            String id,
            String launchId,
            String title,
            String intent,
            List<Map<String, Object>> evidenceRequirements,
            List<Map<String, Object>> acceptanceSignals,
            String riskLevel,
            int version,
            String parentVersionId,
            String status,
            Map<String, Object> metadata,
            String createdByAccountId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TaskView(
            String id,
            String projectId,
            String launchId,
            String title,
            String intent,
            List<String> linkedProofRequestIds,
            String deliverable,
            List<String> acceptanceCriteria,
            List<Map<String, Object>> suggestedEvidence,
            Map<String, Object> rewardPreview,
            String templateRef,
            String status,
            String subStatus,
            List<String> tags,
            Map<String, Object> metadata,
            String createdByAccountId,
            String claimedByAccountId,
            Instant createdAt,
            Instant claimedAt,
            Instant updatedAt
    ) {
    }

    public record ProofView(
            String id,
            String projectId,
            String launchId,
            String taskId,
            String summary,
            List<Map<String, Object>> evidenceItems,
            List<String> linkedProofRequestIds,
            String notes,
            String status,
            ProofValidationStatsView validationStats,
            Map<String, Object> metadata,
            String submittedByAccountId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ReviewQueueItemView(
            ProofView proof,
            String launchTitle,
            String launchStatus,
            String taskTitle,
            String taskStatus,
            String submittedByAccountId,
            Instant submittedAt,
            Map<String, Object> reviewRewardPreview
    ) {
    }

    public record FeedbackView(
            String id,
            String projectId,
            String launchId,
            String subjectType,
            String subjectId,
            String intent,
            String reason,
            List<Map<String, Object>> evidence,
            String suggestedAction,
            String status,
            Map<String, Object> metadata,
            String createdByAccountId,
            String resolvedByAccountId,
            Instant createdAt,
            Instant resolvedAt,
            Instant updatedAt
    ) {
    }

    public record RewardView(
            String id,
            String projectId,
            String launchId,
            String taskId,
            String proofId,
            String recipientAccountId,
            String status,
            BigDecimal contributionWeight,
            Map<String, Object> rewardSnapshot,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
