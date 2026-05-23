package com.monopolyfun.modules.risk.infra;

import com.monopolyfun.modules.risk.domain.RiskEventEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RiskEventRepository {
    RiskEventEntity save(RiskEventEntity event);

    List<RiskEventEntity> findAll();

    java.util.Map<String, Long> countBySeverity();

    List<RiskEventEntity> findRecent(int limit);

    List<RiskEventEntity> findRecentByAccount(String accountId, int limit);

    default Map<String, List<RiskEventEntity>> findRecentByAccounts(Collection<String> accountIds, int limitPerAccount) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return accountIds.stream().collect(java.util.stream.Collectors.toMap(
                accountId -> accountId,
                accountId -> findRecentByAccount(accountId, limitPerAccount)));
    }
}
