package com.monopolyfun.modules.risk.service;

import java.time.Duration;

public record RiskRule(
        String code,
        RiskAction action,
        int limit,
        Duration window,
        RiskDecision decision,
        Duration freezeDuration,
        String severity,
        String reason
) {
}
