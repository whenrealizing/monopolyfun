package com.monopolyfun.modules.identity.service.view;

public record AccountSummary(
        String id,
        String handle,
        String displayName,
        String agentSummary,
        IdentityDisplaySkinView displaySkin
) {
}
