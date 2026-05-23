package com.monopolyfun.modules.share.service;

import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.project.infra.ProjectSharePoolRepository;
import com.monopolyfun.modules.share.service.view.ProjectSharesView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@Transactional
public class ProjectSharePoolService {
    private final ProjectSharePoolRepository projectSharePoolRepository;

    public ProjectSharePoolService(ProjectSharePoolRepository projectSharePoolRepository) {
        this.projectSharePoolRepository = projectSharePoolRepository;
    }

    public ProjectSharePoolEntity initialize(String projectId, String marketId, Instant now) {
        return projectSharePoolRepository.findByProjectId(projectId)
                .orElseGet(() -> projectSharePoolRepository.save(new ProjectSharePoolEntity(
                        projectId,
                        marketId,
                        PostItemSupport.PROJECT_SHARE_TOTAL,
                        0,
                        0,
                        PostItemSupport.PROJECT_TASK_BUDGET,
                        0,
                        0,
                        PostItemSupport.PROJECT_RESERVE_BUDGET,
                        0,
                        PostItemSupport.INITIAL_BASE_REWARD,
                        PostItemSupport.REWARD_DECAY,
                        PostItemSupport.MIN_BASE_REWARD,
                        now,
                        now)));
    }

    public ProjectSharePoolEntity requireByProjectId(String projectId) {
        return projectSharePoolRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project share pool not found"));
    }

    public ProjectSharePoolEntity requireByMarketId(String marketId) {
        return projectSharePoolRepository.findByMarketId(marketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project share pool not found"));
    }

    public ProjectSharePoolEntity reserveTask(String projectId, int amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project task share pool is exhausted");
        }
        // 中文注释：任务领取阶段用单条 SQL 原子占用 shares，避免多个 claim 同时透支任务池。
        return projectSharePoolRepository.reserveTask(projectId, amount);
    }

    public ProjectSharePoolEntity releaseTaskReservation(String marketId, int amount) {
        if (amount <= 0) {
            return requireByMarketId(marketId);
        }
        return projectSharePoolRepository.releaseTaskReservationByMarketId(marketId, amount);
    }

    public ProjectSharePoolEntity mintTask(String marketId, int amount, int curveSlot) {
        return projectSharePoolRepository.mintTaskByMarketId(marketId, amount, curveSlot + 1);
    }

    public ProjectSharesView viewByMarketId(String marketId) {
        ProjectSharePoolEntity pool = requireByMarketId(marketId);
        return view(pool);
    }

    public ProjectSharesView viewByProjectId(String projectId) {
        ProjectSharePoolEntity pool = requireByProjectId(projectId);
        return view(pool);
    }

    private ProjectSharesView view(ProjectSharePoolEntity pool) {
        return new ProjectSharesView(
                pool.marketId(),
                pool.shareTotal(),
                pool.shareMinted(),
                pool.shareReserved(),
                pool.shareRemaining(),
                pool.taskBudget(),
                pool.taskMinted(),
                pool.taskReserved(),
                pool.taskRemaining(),
                pool.reserveBudget(),
                pool.nextCurveSlot(),
                pool.currentBaseReward());
    }
}
