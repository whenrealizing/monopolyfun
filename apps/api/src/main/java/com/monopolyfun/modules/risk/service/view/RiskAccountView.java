package com.monopolyfun.modules.risk.service.view;

import java.time.Instant;
import java.util.List;

public record RiskAccountView(
        String accountId,
        String handle,
        String displayName,
        String status,
        String riskLevel,
        Instant frozenUntil,
        String riskReason,
        Instant riskUpdatedAt,
        List<RiskEventView> recentEvents
) {
}
