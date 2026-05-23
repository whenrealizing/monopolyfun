package com.monopolyfun;

import com.monopolyfun.modules.order.domain.OrderAction;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderLifecycleTest {
    @Test
    void orderLifecycleTransitionsStayInsideEntity() {
        LifecycleContext claimContext = new LifecycleContext("acct-worker", "trace-1", Instant.parse("2026-05-04T10:00:00Z"), Map.of());
        var claimed = OrderEntity.claim(
                "order-1",
                "MF260505ORD000001X",
                "mkt-1",
                "listing-1",
                ListingKind.WORK,
                null,
                null,
                null,
                "acct-worker",
                SettlementType.SHARES,
                BigDecimal.valueOf(500),
                "nonce-test",
                List.of("preview opens"),
                "preview proof",
                "shares settlement",
                Map.of("deliverableSpec", "preview"),
                Map.of("settlementSpec", "shares"),
                Map.of("executionMode", "agent"),
                claimContext);
        assertEquals(OrderStatus.CLAIMED, claimed.entity().status());
        assertEquals(OrderAction.CLAIM, claimed.transition().action());

        LifecycleContext proofContext = new LifecycleContext("acct-worker", "trace-1", Instant.parse("2026-05-04T10:05:00Z"), Map.of());
        var delivered = claimed.entity().submitProof("proof-1", "acct-worker", proofContext);
        assertEquals(OrderStatus.DELIVERED, delivered.entity().status());
        assertEquals("waiting_lead_acceptance", delivered.entity().displayPhase());

        LifecycleContext acceptContext = new LifecycleContext("acct-lead", "trace-1", Instant.parse("2026-05-04T10:10:00Z"), Map.of("note", "ok"));
        var settled = delivered.entity().openAcceptanceWindow("acct-lead", "accepted", Instant.parse("2026-05-05T10:10:00Z"), acceptContext);
        assertEquals(OrderStatus.ACCEPTED_OPEN, settled.entity().status());
        assertEquals(OrderAction.ACCEPT, settled.transition().action());
        assertEquals(OrderEntity.DISPUTE_WINDOW_OPEN, settled.entity().disputeWindowStatus());

        LifecycleContext disputeContext = new LifecycleContext("acct-lead", "trace-2", Instant.parse("2026-05-04T10:15:00Z"), Map.of());
        var disputed = settled.entity().openDispute("needs review", "listing-review-1", disputeContext);
        assertEquals(OrderEntity.REVIEW_STATUS_OPEN, disputed.entity().reviewStatus());
        assertEquals(OrderEntity.DISPUTE_WINDOW_CLOSED, disputed.entity().disputeWindowStatus());

        LifecycleContext reviewAcceptContext = new LifecycleContext("acct-lead", "trace-2", Instant.parse("2026-05-04T10:20:00Z"), Map.of("note", "accepted"));
        var resolved = disputed.entity().finalizeAccepted("acct-lead", "accepted_by_review", reviewAcceptContext);
        assertEquals(OrderStatus.FINAL_ACCEPTED, resolved.entity().status());
        assertEquals(OrderEntity.REVIEW_STATUS_RESOLVED, resolved.entity().reviewStatus());
        assertEquals("normal", resolved.entity().riskLevel());
    }
}
