package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;

import java.time.Instant;

public final class IdentityFactStatus {
    private IdentityFactStatus() {
    }

    public static boolean isActiveVerified(IdentityFactEntity fact, Instant now) {
        // 中文注释：认证、展示身份和 badge 共用同一有效性判断，避免过期事实在不同界面显示不一致。
        return "verified".equals(fact.status())
                && fact.revokedAt() == null
                && (fact.expiresAt() == null || fact.expiresAt().isAfter(now));
    }
}
