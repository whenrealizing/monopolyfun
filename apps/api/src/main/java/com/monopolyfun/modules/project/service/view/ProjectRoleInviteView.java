package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.time.Instant;

public record ProjectRoleInviteView(
        String projectNo,
        ProjectRoleCode roleCode,
        String accountId,
        String invitedByAccountId,
        String workItemNo,
        String status,
        Instant createdAt
) {
}
