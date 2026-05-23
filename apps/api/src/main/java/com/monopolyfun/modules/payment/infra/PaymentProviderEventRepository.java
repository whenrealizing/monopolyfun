package com.monopolyfun.modules.payment.infra;

import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;

import java.util.List;
import java.util.Optional;

public interface PaymentProviderEventRepository {
    Optional<PaymentProviderEventEntity> findByProviderEventId(String provider, String providerEventId);

    PaymentProviderEventEntity save(PaymentProviderEventEntity event);

    default List<PaymentProviderEventEntity> findRecent(int limit) {
        return List.of();
    }
}
