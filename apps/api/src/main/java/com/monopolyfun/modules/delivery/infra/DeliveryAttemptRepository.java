package com.monopolyfun.modules.delivery.infra;

import com.monopolyfun.modules.delivery.domain.DeliveryAttemptEntity;

import java.util.List;
import java.util.Optional;

public interface DeliveryAttemptRepository {
    Optional<DeliveryAttemptEntity> findByProviderIdempotencyKey(String provider, String idempotencyKey);

    Optional<DeliveryAttemptEntity> findLatestByOrderId(String orderId);

    List<DeliveryAttemptEntity> findByOrderId(String orderId);

    DeliveryAttemptEntity save(DeliveryAttemptEntity attempt);

    default List<DeliveryAttemptEntity> findRecent(int limit) {
        return List.of();
    }
}
