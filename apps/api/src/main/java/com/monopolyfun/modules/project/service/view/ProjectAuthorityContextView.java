package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.util.List;
import java.util.Set;

public record ProjectAuthorityContextView(
        String accountId,
        String projectId,
        List<ProjectRoleCode> roleCodes,
        Set<String> capabilities
) {
}
