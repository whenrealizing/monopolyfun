package com.monopolyfun.modules.payment.infra;

import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentIntentRepository {
    Optional<PaymentIntentEntity> findById(String id);

    Optional<PaymentIntentEntity> findByPaymentNo(String paymentNo);

    Optional<PaymentIntentEntity> findByOrderId(String orderId);

    default Map<String, PaymentIntentEntity> findByOrderIds(List<String> orderIds) {
        return orderIds.stream()
                .distinct()
                .map(orderId -> findByOrderId(orderId).map(intent -> Map.entry(orderId, intent)))
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    List<PaymentIntentEntity> findAll();

    java.util.Map<String, Long> countByStatus();

    List<PaymentIntentEntity> findRecent(int limit);

    PaymentIntentEntity save(PaymentIntentEntity paymentIntent);
}
