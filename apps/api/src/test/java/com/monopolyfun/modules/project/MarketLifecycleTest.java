package com.monopolyfun;

import com.monopolyfun.modules.project.domain.MarketAction;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketLifecycleTest {
    @Test
    void marketLifecycleTransitionsStayInsideEntity() {
        LifecycleContext createContext = new LifecycleContext("acct-lead", "trace-2", Instant.parse("2026-05-04T10:00:00Z"), Map.of());
        var created = MarketEntity.create(
                "mkt-1",
                "AI Finance Company",
                "Summary",
                "Goal",
                "acct-lead",
                "github/example",
                "https://example.test",
                SettlementType.SHARES,
                createContext);
        assertEquals(MarketStatus.ACTIVE, created.entity().status());
        assertEquals(MarketAction.CREATE, created.transition().action());

        LifecycleContext stallContext = new LifecycleContext("acct-lead", "trace-2", Instant.parse("2026-05-04T10:10:00Z"), Map.of("note", "pause"));
        var stalled = created.entity().stall(stallContext);
        assertEquals(MarketStatus.STALLED, stalled.entity().status());

        LifecycleContext activateContext = new LifecycleContext("acct-lead", "trace-2", Instant.parse("2026-05-04T10:20:00Z"), Map.of("note", "resume"));
        var reactivated = stalled.entity().activate(activateContext);
        assertEquals(MarketStatus.ACTIVE, reactivated.entity().status());
    }
}
