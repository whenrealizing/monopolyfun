package com.monopolyfun.modules.settlement.service;

import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SettlementEventService {
    private final SettlementEventRepository settlementEventRepository;

    public SettlementEventService(SettlementEventRepository settlementEventRepository) {
        this.settlementEventRepository = settlementEventRepository;
    }

    @Transactional
    public SettlementEventEntity recordOnce(
            String orderId,
            String paymentIntentId,
            String eventType,
            String idempotencyKey,
            Integer amountMinor,
            String currency,
            String actorAccountId,
            Map<String, Object> payload) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required for settlement event");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required for settlement event");
        }
        String resolvedKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? eventType + ":" + orderId
                : idempotencyKey.trim();
        var existing = settlementEventRepository.findByUniqueKey(orderId, eventType.trim(), resolvedKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 中文注释：结算事件用数据库唯一键承接重放，所有资金和发货副作用都有同一条可审计事实。
        return settlementEventRepository.save(new SettlementEventEntity(
                "settlement-event-" + UUID.randomUUID(),
                orderId,
                paymentIntentId,
                eventType.trim(),
                resolvedKey,
                amountMinor,
                currency,
                actorAccountId,
                payload == null ? Map.of() : Map.copyOf(payload),
                Instant.now()));
    }
}
