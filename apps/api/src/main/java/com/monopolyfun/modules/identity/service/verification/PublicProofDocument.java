package com.monopolyfun.modules.identity.service.verification;

import java.time.Instant;

public record PublicProofDocument(
        String authorHandle,
        String displayName,
        String profileUrl,
        String text,
        Instant publishedAt,
        Instant observedAt,
        String canonicalUrl
) {
}
