package com.monopolyfun.modules.settlement.infra;

import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;

import java.util.List;
import java.util.Optional;

public interface SettlementEventRepository {
    Optional<SettlementEventEntity> findByUniqueKey(String orderId, String eventType, String idempotencyKey);

    SettlementEventEntity save(SettlementEventEntity event);

    List<SettlementEventEntity> findByOrderId(String orderId);

    default List<SettlementEventEntity> findByProjectId(String projectId) {
        return List.of();
    }

    default List<SettlementEventEntity> findRecent(int limit) {
        return List.of();
    }
}
