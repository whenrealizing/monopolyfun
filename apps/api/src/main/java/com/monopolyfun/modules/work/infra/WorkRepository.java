package com.monopolyfun.modules.work.infra;

import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkRepository {
    void upsertItem(WorkItemEntity item);

    List<WorkItemEntity> findItemsByAccountId(String accountId);

    List<WorkItemEntity> findSubmittedProjectRoleItems();

    int releaseExpiredClaims(Instant now);

    int renewClaimLease(String accountId, String itemNo, Instant claimExpiresAt, Instant now);

    int closeStaleSourceItems(String accountId, Set<String> sourceTypes, Set<String> activeItemNos, String reason);

    int closeOpenItemsBySource(String sourceType, String sourceId, String reason);

    List<WorkItemEntity> findItemsBySource(String sourceType, String sourceId);

    Optional<WorkItemEntity> findItemByNoOrId(String itemNoOrId);

    Optional<WorkRunEntity> findRunByItemAndActor(String workItemId, String actorAccountId);

    Optional<WorkRunEntity> findRunByItemId(String workItemId);

    Optional<WorkRunEntity> findRunByNoOrId(String runNoOrId);

    WorkRunEntity saveRun(WorkRunEntity run);

    WorkItemEntity saveItem(WorkItemEntity item);

    WorkReceiptEntity saveReceipt(WorkReceiptEntity receipt);

    Optional<WorkReceiptEntity> findLatestReceiptByRunId(String workRunId);

    WorkReviewEntity saveReview(WorkReviewEntity review);

    Optional<WorkReviewEntity> findReviewByRunId(String workRunId);

    void saveEvent(WorkEventEntity event);
}
