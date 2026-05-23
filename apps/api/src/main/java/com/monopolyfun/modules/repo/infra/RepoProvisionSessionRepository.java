package com.monopolyfun.modules.repo.infra;

import com.monopolyfun.modules.repo.domain.RepoProvisionSessionEntity;

import java.util.Optional;

public interface RepoProvisionSessionRepository {
    Optional<RepoProvisionSessionEntity> findById(String id);

    RepoProvisionSessionEntity save(RepoProvisionSessionEntity session);
}
