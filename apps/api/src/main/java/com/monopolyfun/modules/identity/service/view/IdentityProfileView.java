package com.monopolyfun.modules.identity.service.view;

import java.util.List;

public record IdentityProfileView(
        AccountSummary account,
        boolean verified,
        long verifiedFactCount,
        List<IdentityBadgeView> badges,
        List<IdentityLinkedAccountView> linkedAccounts,
        IdentityDisplaySkinView displaySkin,
        List<IdentityDisplaySkinView> displaySkinOptions
) {
}
