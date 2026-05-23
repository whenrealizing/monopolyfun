package com.monopolyfun.shared.command;

import java.time.Instant;
import java.util.Map;

public record CommandReceipt(
        String id,
        String type,
        String subjectId,
        String status,
        Map<String, Object> payload,
        String actorAccountId,
        String traceId,
        String auditId,
        Instant createdAt
) {
}
