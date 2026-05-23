package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProjectRoleRepository {
    List<ProjectRoleEntity> findByProjectId(String projectId);

    Map<String, List<ProjectRoleEntity>> findByProjectIds(Collection<String> projectIds);

    List<ProjectRoleEntity> findAssignedRoles(String projectId, String accountId);

    List<ProjectRoleEntity> findAssignedRolesByAccountId(String accountId);

    ProjectRoleEntity assignRole(String projectId, ProjectRoleCode roleCode, String accountId, String assignedByAccountId);

    ProjectRoleEntity vacateRole(String projectId, ProjectRoleCode roleCode, String accountId, String actorAccountId);

    void initializeProjectRoles(String projectId, String ownerAccountId, Instant now);
}
