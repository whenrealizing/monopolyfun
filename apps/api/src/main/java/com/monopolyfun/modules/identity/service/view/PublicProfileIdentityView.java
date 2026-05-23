package com.monopolyfun.modules.identity.service.view;

import java.util.List;

public record PublicProfileIdentityView(
        PublicAccountSummary account,
        boolean verified,
        long verifiedFactCount,
        List<IdentityBadgeView> badges,
        List<IdentityLinkedAccountView> linkedAccounts,
        IdentityDisplaySkinView displaySkin
) {
}
