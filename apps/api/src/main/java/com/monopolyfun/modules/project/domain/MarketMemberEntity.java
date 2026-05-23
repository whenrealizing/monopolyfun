package com.monopolyfun.modules.project.domain;

import java.time.Instant;

public record MarketMemberEntity(
        String id,
        String marketId,
        String accountId,
        MarketMemberRole role,
        Instant createdAt
) {
}
