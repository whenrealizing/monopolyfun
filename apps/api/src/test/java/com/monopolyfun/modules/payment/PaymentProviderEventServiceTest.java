package com.monopolyfun.modules.payment;

import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.infra.PaymentProviderEventRepository;
import com.monopolyfun.modules.payment.service.PaymentProviderEventService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentProviderEventServiceTest {
    @Test
    void recordsProviderCallbackOnceByProviderEventId() {
        InMemoryPaymentProviderEventRepository repository = new InMemoryPaymentProviderEventRepository();
        PaymentProviderEventService service = new PaymentProviderEventService(repository);

        var first = service.recordOnce("okx", "payment-1:tx-1:CAPTURED", "intent-1", "payment-1", "tx-1", "CAPTURED", Map.of("source", "test"));
        var second = service.recordOnce("okx", "payment-1:tx-1:CAPTURED", "intent-1", "payment-1", "tx-1", "CAPTURED", Map.of("source", "retry"));

        assertFalse(first.duplicate());
        assertTrue(second.duplicate());
        assertSame(first.event(), second.event());
    }

    private static final class InMemoryPaymentProviderEventRepository implements PaymentProviderEventRepository {
        private final Map<String, PaymentProviderEventEntity> events = new HashMap<>();

        @Override
        public Optional<PaymentProviderEventEntity> findByProviderEventId(String provider, String providerEventId) {
            return Optional.ofNullable(events.get(provider + ":" + providerEventId));
        }

        @Override
        public PaymentProviderEventEntity save(PaymentProviderEventEntity event) {
            events.put(event.provider() + ":" + event.providerEventId(), event);
            return event;
        }
    }
}
