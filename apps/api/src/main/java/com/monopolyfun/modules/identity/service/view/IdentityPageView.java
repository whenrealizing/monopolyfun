package com.monopolyfun.modules.identity.service.view;

import java.util.List;

public record IdentityPageView(
        IdentityProfileView profile,
        IdentityActivityView activity,
        List<IdentityCertifierView> certifiers,
        List<IdentityVerificationChallengeView> challenges
) {
}
