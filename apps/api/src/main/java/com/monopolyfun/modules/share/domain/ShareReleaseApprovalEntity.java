package com.monopolyfun.modules.share.domain;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.time.Instant;

public record ShareReleaseApprovalEntity(
        String id,
        String requestId,
        ProjectRoleCode roleCode,
        String approverAccountId,
        Instant createdAt
) {
}
