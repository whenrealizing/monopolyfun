package com.monopolyfun.modules.identity.service.display;

public record IdentityDisplaySkinPreference(
        String source,
        String certifierId
) {
    public static IdentityDisplaySkinPreference nativeSkin() {
        return new IdentityDisplaySkinPreference("native", null);
    }
}
