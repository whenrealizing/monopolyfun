package com.monopolyfun.modules.identity.service.view;

import java.util.Map;

public record IdentityCertifierView(
        String id,
        String name,
        String provider,
        String verificationMethod,
        String description,
        String trustLevel,
        String badgeCode,
        Integer expiresInDays,
        Map<String, Object> startInputSchema,
        Map<String, Object> completeInputSchema
) {
}
