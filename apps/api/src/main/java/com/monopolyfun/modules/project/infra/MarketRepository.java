package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.MarketEntity;

import java.util.List;
import java.util.Optional;

public interface MarketRepository {
    List<MarketEntity> findAll();

    java.util.Map<String, Long> countByStatus();

    Optional<MarketEntity> findById(String id);

    MarketEntity save(MarketEntity market);
}
