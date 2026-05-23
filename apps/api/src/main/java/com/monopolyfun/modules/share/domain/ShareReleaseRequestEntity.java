package com.monopolyfun.modules.share.domain;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ShareReleaseRequestEntity(
        String id,
        ShareIssuerType issuerType,
        String issuerId,
        String marketId,
        String projectId,
        String orderId,
        String proofId,
        String accountId,
        int amount,
        int curveSlot,
        ShareReleaseRequestStatus status,
        List<ProjectRoleCode> requiredRoleCodes,
        List<ProjectRoleCode> approvedRoleCodes,
        List<ProjectRoleCode> skippedRoleCodes,
        String requestedByAccountId,
        Instant resolvedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isResolved() {
        return status == ShareReleaseRequestStatus.APPROVED || status == ShareReleaseRequestStatus.SKIPPED;
    }
}
