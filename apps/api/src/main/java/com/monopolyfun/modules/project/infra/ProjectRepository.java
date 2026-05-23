package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    List<ProjectEntity> findAll();

    PageResult<ProjectEntity> findPublicChildren(String status, String q, String sort, PageQuery pageQuery);

    List<ProjectEntity> findByIds(Collection<String> ids);

    default List<ProjectEntity> findPublicByOwnerAccountId(String ownerAccountId, int limit) {
        return findByOwnerAccountId(ownerAccountId, limit).stream()
                .filter(project -> {
                    Object visibility = project.metadata() == null ? null : project.metadata().get("visibility");
                    return visibility == null || "market_public".equals(String.valueOf(visibility));
                })
                .limit(Math.max(1, limit))
                .toList();
    }

    List<ProjectEntity> findByOwnerAccountId(String ownerAccountId, int limit);

    List<ProjectEntity> findWorkbenchCandidates(String accountId, int limit);

    List<ProjectEntity> findOwnerHandoffCandidates(Instant inactiveBefore);

    Optional<ProjectEntity> findById(String id);

    Optional<ProjectEntity> findByProjectNo(String projectNo);

    Optional<ProjectEntity> findRootProject();

    ProjectEntity save(ProjectEntity project);
}
