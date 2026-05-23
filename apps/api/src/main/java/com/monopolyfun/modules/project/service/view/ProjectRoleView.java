package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.time.Instant;

public record ProjectRoleView(
        String projectId,
        ProjectRoleCode roleCode,
        String accountId,
        String assignedByAccountId,
        Instant assignedAt,
        Instant updatedAt
) {
}
