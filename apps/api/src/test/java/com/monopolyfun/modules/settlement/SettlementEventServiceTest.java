package com.monopolyfun.modules.settlement;

import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.modules.settlement.service.SettlementEventService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SettlementEventServiceTest {
    @Test
    void recordsSettlementEventOncePerOrderTypeAndIdempotencyKey() {
        InMemorySettlementEventRepository repository = new InMemorySettlementEventRepository();
        SettlementEventService service = new SettlementEventService(repository);

        SettlementEventEntity first = service.recordOnce("order-1", "intent-1", "payment_captured", "capture-1", 100, "USD", "acct-1", Map.of("provider", "okx"));
        SettlementEventEntity second = service.recordOnce("order-1", "intent-1", "payment_captured", "capture-1", 100, "USD", "acct-1", Map.of("provider", "okx"));

        assertSame(first, second);
        assertEquals(1, repository.findByOrderId("order-1").size());
    }

    private static final class InMemorySettlementEventRepository implements SettlementEventRepository {
        private final Map<String, SettlementEventEntity> events = new HashMap<>();

        @Override
        public Optional<SettlementEventEntity> findByUniqueKey(String orderId, String eventType, String idempotencyKey) {
            return Optional.ofNullable(events.get(key(orderId, eventType, idempotencyKey)));
        }

        @Override
        public SettlementEventEntity save(SettlementEventEntity event) {
            events.put(key(event.orderId(), event.eventType(), event.idempotencyKey()), event);
            return event;
        }

        @Override
        public List<SettlementEventEntity> findByOrderId(String orderId) {
            return new ArrayList<>(events.values()).stream()
                    .filter(event -> event.orderId().equals(orderId))
                    .toList();
        }

        private String key(String orderId, String eventType, String idempotencyKey) {
            return orderId + ":" + eventType + ":" + idempotencyKey;
        }
    }
}
