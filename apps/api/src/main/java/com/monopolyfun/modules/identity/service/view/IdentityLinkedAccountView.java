package com.monopolyfun.modules.identity.service.view;

public record IdentityLinkedAccountView(
        String certifierId,
        String provider,
        String platformUserId,
        String handle,
        String displayName,
        String avatarUrl,
        String profileUrl,
        String verifiedAt
) {
}
