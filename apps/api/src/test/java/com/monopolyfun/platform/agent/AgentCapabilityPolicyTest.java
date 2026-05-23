package com.monopolyfun.platform.agent;

import com.monopolyfun.platform.agent.openapi.AgentCapabilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityPolicyTest {
    @Test
    void casbinFactsProduceAllowedAndBlockedDecisions() {
        AgentCapabilityPolicy policy = new AgentCapabilityPolicy();
        List<AgentCapabilityPolicy.AgentCapabilityRule> rules = List.of(new AgentCapabilityPolicy.AgentCapabilityRule(
                "offer.create_item",
                List.of(
                        new AgentCapabilityPolicy.FactRequirement("login", "login_required"),
                        new AgentCapabilityPolicy.FactRequirement("owner", "account_not_owner"))));

        var allowed = policy.decide(rules, Set.of("login", "owner")).getFirst();
        var blocked = policy.decide(rules, Set.of("login")).getFirst();

        assertTrue(allowed.allowed());
        assertFalse(blocked.allowed());
        assertEquals("account_not_owner", blocked.blockedReason());
    }
}
