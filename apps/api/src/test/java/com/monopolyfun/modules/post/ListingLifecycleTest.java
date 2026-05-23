package com.monopolyfun;

import com.monopolyfun.modules.post.domain.ListingAction;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingLifecycleTest {
    @Test
    void listingLifecycleTransitionsStayInsideEntity() {
        LifecycleContext createContext = new LifecycleContext("acct-lead", "trace-3", Instant.parse("2026-05-04T10:00:00Z"), Map.of());
        var created = ListingEntity.create(
                "listing-1",
                "mkt-1",
                ListingKind.WORK,
                null,
                "Build report page",
                "quest",
                "quest/report-page",
                "Preview plus notes",
                "Preview URL required",
                "Accepted work order mints shares",
                2,
                SettlementType.SHARES,
                "acct-lead",
                ListingStatus.OPEN,
                createContext);
        assertEquals(ListingStatus.OPEN, created.entity().status());
        assertEquals(ListingAction.CREATE, created.transition().action());

        LifecycleContext pauseContext = new LifecycleContext("acct-worker", "trace-3", Instant.parse("2026-05-04T10:05:00Z"), Map.of("reason", "inventory_full"));
        var paused = created.entity().withActiveOrdersCount(2).pause(pauseContext);
        assertEquals(ListingStatus.PAUSED, paused.entity().status());
        assertEquals(ListingAction.PAUSE, paused.transition().action());

        LifecycleContext reopenContext = new LifecycleContext("acct-lead", "trace-3", Instant.parse("2026-05-04T10:10:00Z"), Map.of("reason", "capacity_released"));
        var reopened = paused.entity().withActiveOrdersCount(1).reopen(reopenContext);
        assertEquals(ListingStatus.OPEN, reopened.entity().status());
        assertEquals(ListingAction.REOPEN, reopened.transition().action());

        LifecycleContext closeContext = new LifecycleContext("acct-lead", "trace-3", Instant.parse("2026-05-04T10:15:00Z"), Map.of("reason", "manual_close"));
        var closed = reopened.entity().close(closeContext);
        assertEquals(ListingStatus.CLOSED, closed.entity().status());
        assertEquals(ListingAction.CLOSE, closed.transition().action());
    }
}
