package com.monopolyfun.modules.order.infra;

import com.monopolyfun.modules.order.domain.ProofEntity;

import java.util.List;
import java.util.Optional;

public interface ProofRepository {
    List<ProofEntity> findAll();

    List<ProofEntity> findBySubmittedByAccountId(String accountId, int limit);

    Optional<ProofEntity> findById(String id);

    ProofEntity save(ProofEntity proof);
}
