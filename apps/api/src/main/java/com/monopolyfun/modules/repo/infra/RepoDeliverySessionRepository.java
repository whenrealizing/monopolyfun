package com.monopolyfun.modules.repo.infra;

import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;

import java.time.Instant;
import java.util.Optional;

public interface RepoDeliverySessionRepository {
    Optional<RepoDeliverySessionEntity> findById(String id);

    Optional<RepoDeliverySessionEntity> findActiveByOrderNo(String orderNo);

    Optional<RepoDeliverySessionEntity> findActiveByRepoUrlAndHeadBranch(String repoUrl, String headBranch);

    int countCreatedByAccountSince(String accountId, Instant since);

    int countCreatedByProjectSince(String projectNo, Instant since);

    RepoDeliverySessionEntity save(RepoDeliverySessionEntity session);
}
