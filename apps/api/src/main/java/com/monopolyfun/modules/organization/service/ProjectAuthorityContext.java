package com.monopolyfun.modules.organization.service;

import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.util.List;
import java.util.Set;

public record ProjectAuthorityContext(
        String accountId,
        String projectId,
        List<ProjectRoleCode> roleCodes,
        Set<ProjectCapability> capabilities
) {
    public boolean has(ProjectCapability capability) {
        return capabilities.contains(capability);
    }
}
