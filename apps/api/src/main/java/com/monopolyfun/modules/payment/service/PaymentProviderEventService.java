package com.monopolyfun.modules.payment.service;

import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.infra.PaymentProviderEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentProviderEventService {
    private final PaymentProviderEventRepository paymentProviderEventRepository;

    public PaymentProviderEventService(PaymentProviderEventRepository paymentProviderEventRepository) {
        this.paymentProviderEventRepository = paymentProviderEventRepository;
    }

    @Transactional
    public ProviderEventRecord recordOnce(
            String provider,
            String providerEventId,
            String paymentIntentId,
            String providerPaymentRef,
            String txHash,
            String status,
            Map<String, Object> payload) {
        String resolvedProvider = requireText(provider, "provider");
        String resolvedEventId = requireText(providerEventId, "providerEventId");
        var existing = paymentProviderEventRepository.findByProviderEventId(resolvedProvider, resolvedEventId);
        if (existing.isPresent()) {
            return new ProviderEventRecord(existing.get(), true);
        }
        // 中文注释：provider callback 先登记唯一事件，再更新资金状态，防止重放导致重复发货或重复记账。
        PaymentProviderEventEntity saved = paymentProviderEventRepository.save(new PaymentProviderEventEntity(
                "payment-provider-event-" + UUID.randomUUID(),
                resolvedProvider,
                resolvedEventId,
                requireText(paymentIntentId, "paymentIntentId"),
                providerPaymentRef,
                txHash,
                requireText(status, "status"),
                payload == null ? Map.of() : Map.copyOf(payload),
                Instant.now()));
        return new ProviderEventRecord(saved, false);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public record ProviderEventRecord(PaymentProviderEventEntity event, boolean duplicate) {
    }
}
