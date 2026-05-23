package com.monopolyfun;

import com.monopolyfun.shared.id.BusinessIdService;
import com.monopolyfun.shared.id.BusinessIdType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessIdServiceTest {
    @Test
    void createsInternalIdAndDisplayNoFromSingleEntryPoint() {
        AtomicLong sequence = new AtomicLong();
        BusinessIdService service = new BusinessIdService((type, bizDate) -> sequence.incrementAndGet());

        var orderIds = service.next(BusinessIdType.ORDER);
        var paymentIds = service.next(BusinessIdType.PAYMENT);

        assertTrue(orderIds.id().startsWith("order-"));
        assertTrue(orderIds.displayNo().matches("MF\\d{6}ORD000001[0-9A-Z]"));
        assertTrue(paymentIds.id().startsWith("pay-"));
        assertTrue(paymentIds.displayNo().matches("MF\\d{6}PAY000002[0-9A-Z]"));
        assertEquals(2, sequence.get());
    }
}
