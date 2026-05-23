package com.monopolyfun.modules.identity.service.view;

public record IdentityDisplaySkinView(
        String source,
        String certifierId,
        String provider,
        String platformUserId,
        String displayName,
        String displayHandle,
        String avatarUrl,
        String profileUrl,
        String themeKey,
        boolean verified,
        boolean selected
) {
}
