package com.monopolyfun.platform.agent.openapi;

import java.util.Map;

public record AgentCapabilityDecision(
        String capability,
        boolean allowed,
        String blockedReason
) {
    public static AgentCapabilityDecision allowed(String capability) {
        return new AgentCapabilityDecision(capability, true, null);
    }

    public static AgentCapabilityDecision blocked(String capability, String reason) {
        return new AgentCapabilityDecision(capability, false, reason);
    }

    public Map<String, Object> blockedCapability() {
        return Map.of("capability", capability, "reason", blockedReason == null ? "blocked" : blockedReason);
    }
}
