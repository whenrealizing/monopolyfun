package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.ProjectCiCheckEntity;
import com.monopolyfun.modules.project.domain.ProjectPrLinkEntity;
import com.monopolyfun.modules.project.domain.ProjectRepoBindingEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ProjectDevelopmentRepository {
    ProjectRepoBindingEntity saveRepoBinding(
            String projectId,
            String provider,
            String repoUrl,
            String repoOwner,
            String repoName,
            String defaultBranch,
            String installationId,
            String createdByAccountId);

    List<ProjectRepoBindingEntity> findRepoBindings(String projectId);

    ProjectPrLinkEntity savePrLink(
            String projectId,
            String validationTaskId,
            String repoUrl,
            int prNumber,
            String prUrl,
            String headSha,
            String baseBranch,
            String branchName,
            String state,
            Map<String, Object> rawPayload);

    ProjectCiCheckEntity saveCiCheck(
            String projectId,
            String validationTaskId,
            Integer prNumber,
            String checkName,
            String status,
            String conclusion,
            String detailsUrl,
            Map<String, Object> rawPayload);

    List<ProjectPrLinkEntity> findPrLinks(String projectId);

    List<ProjectCiCheckEntity> findCiChecks(String projectId);

    List<ProjectCiCheckEntity> findActionableCiChecks(String accountId);

    List<ProjectPrLinkEntity> findActionablePrLinks(String accountId);

    List<Map<String, Object>> findCandidateSupports(String projectId);

    List<Map<String, Object>> findCandidateFinalReviews(String projectId);

    boolean hasCandidateSupport(String candidateId, String accountId);

    void saveCandidateSupport(String candidateId, String projectId, String taskId, Integer prNumber, String headSha, String accountId, int weight, String reason);

    void saveCandidateFinalReview(String candidateId, String projectId, String taskId, Integer prNumber, String reviewedCommitSha, String accountId, String decision, String reason);

    List<Map<String, Object>> findActiveCandidateWindowSkips(String projectId, String accountId, Instant now);

    void saveCandidateWindowSkip(String projectId, String candidateId, String accountId, String reasonCode, String reason, Instant expiresAt);
}
