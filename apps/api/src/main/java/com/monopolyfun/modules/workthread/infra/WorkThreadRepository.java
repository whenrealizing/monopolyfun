package com.monopolyfun.modules.workthread.infra;

import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadReviewEntity;

import java.time.Instant;
import java.util.Optional;

public interface WorkThreadRepository {
    WorkThreadEntity saveThread(WorkThreadEntity thread);

    Optional<WorkThreadEntity> findThread(String idOrNo);

    WorkThreadEntity updateThreadState(WorkThreadEntity thread);

    WorkResultEntity saveResult(WorkResultEntity result);

    Optional<WorkResultEntity> findLatestResult(String workThreadId);

    WorkThreadReviewEntity saveReview(WorkThreadReviewEntity review);

    ContributionEntryEntity saveContribution(ContributionEntryEntity contribution);

    void saveSharesLedgerEntry(ContributionEntryEntity contribution, int curveSlot);

    int countSettledContributionsByProject(String projectId);

    int sumSharesByProject(String projectId);

    int sumSharesByProjectAndAccount(String projectId, String accountId);

    int sumBountyByProjectAndAccount(String projectId, String accountId);

    ProjectRevenueAddressEntity saveRevenueAddress(ProjectRevenueAddressEntity address);

    Optional<ProjectRevenueAddressEntity> findActiveRevenueAddress(String projectId);

    DistributionBatchEntity saveDistributionBatch(DistributionBatchEntity batch);

    Optional<DistributionBatchEntity> findDistributionBatch(String projectId, String period);

    DistributionClaimEntity saveDistributionClaim(DistributionClaimEntity claim);

    Optional<DistributionClaimEntity> findDistributionClaim(String batchId, String accountId);

    Instant now();
}
