package com.monopolyfun.platform.command;

import java.time.Instant;

public record CommandContext(
        String actorAccountId,
        String traceId,
        Instant startedAt
) {
}
