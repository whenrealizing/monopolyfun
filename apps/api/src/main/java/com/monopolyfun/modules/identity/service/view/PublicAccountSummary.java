package com.monopolyfun.modules.identity.service.view;

public record PublicAccountSummary(
        String id,
        String handle,
        String displayName,
        String agentSummary,
        IdentityDisplaySkinView displaySkin
) {
}
