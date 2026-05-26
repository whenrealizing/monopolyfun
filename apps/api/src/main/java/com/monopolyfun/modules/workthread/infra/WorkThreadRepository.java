package com.monopolyfun.modules.workthread.infra;

import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.DistributionEntitlementEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadReviewEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkThreadRepository {
    WorkThreadEntity saveThread(WorkThreadEntity thread);

    Optional<WorkThreadEntity> findThread(String idOrNo);

    List<WorkThreadEntity> listThreadsByProject(String projectId);

    WorkThreadEntity updateThreadState(WorkThreadEntity thread, String expectedStatus);

    WorkResultEntity saveResult(WorkResultEntity result);

    Optional<WorkResultEntity> findLatestResult(String workThreadId);

    WorkResultEntity updateResultStatus(String resultId, String status);

    WorkThreadReviewEntity saveReview(WorkThreadReviewEntity review);

    ContributionEntryEntity saveContribution(ContributionEntryEntity contribution);

    List<ContributionEntryEntity> listContributionsByProject(String projectId);

    void saveSharesLedgerEntry(ContributionEntryEntity contribution, int curveSlot);

    int countSettledContributionsByProject(String projectId);

    int sumSharesByProject(String projectId);

    int sumSharesByProjectAndAccount(String projectId, String accountId);

    int sumBountyByProjectAndAccount(String projectId, String accountId);

    ProjectRevenueAddressEntity saveRevenueAddress(ProjectRevenueAddressEntity address);

    Optional<ProjectRevenueAddressEntity> findActiveRevenueAddress(String projectId);

    DistributionBatchEntity saveDistributionBatch(DistributionBatchEntity batch);

    void saveDistributionEntitlements(List<DistributionEntitlementEntity> entitlements);

    Optional<DistributionBatchEntity> findDistributionBatch(String projectId, String period);

    List<DistributionBatchEntity> listDistributionBatches(String projectId);

    List<DistributionEntitlementEntity> listDistributionEntitlements(String batchId);

    Optional<DistributionEntitlementEntity> findDistributionEntitlement(String batchId, String accountId);

    DistributionClaimEntity saveDistributionClaim(DistributionClaimEntity claim);

    DistributionClaimEntity submitDistributionClaimTx(String batchId, String accountId, String txHash, Instant now);

    DistributionClaimEntity confirmDistributionClaimTx(String batchId, String accountId, String txHash, Instant now);

    Optional<DistributionClaimEntity> findDistributionClaim(String batchId, String accountId);

    boolean hasDistributionClaim(String batchId, String accountId);

    Instant now();
}
