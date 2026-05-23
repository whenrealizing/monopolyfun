package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;

import java.util.Optional;

public interface ProjectSharePoolRepository {
    Optional<ProjectSharePoolEntity> findByProjectId(String projectId);

    Optional<ProjectSharePoolEntity> findByMarketId(String marketId);

    ProjectSharePoolEntity save(ProjectSharePoolEntity pool);

    ProjectSharePoolEntity reserveTask(String projectId, int amount);

    ProjectSharePoolEntity releaseTaskReservationByMarketId(String marketId, int amount);

    ProjectSharePoolEntity mintTaskByMarketId(String marketId, int amount, int nextCurveSlot);
}
