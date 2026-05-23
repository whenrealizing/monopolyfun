package com.monopolyfun.modules.share.infra;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestEntity;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShareReleaseRequestRepository {
    Optional<ShareReleaseRequestEntity> findById(String id);

    Optional<ShareReleaseRequestEntity> findByOrderId(String orderId);

    List<ShareReleaseRequestEntity> findPendingForRoleAssignee(String accountId);

    ShareReleaseRequestEntity save(ShareReleaseRequestEntity request);

    ShareReleaseRequestEntity markResolved(String requestId, ShareReleaseRequestStatus status, List<ProjectRoleCode> approvedRoles, Instant resolvedAt);
}
