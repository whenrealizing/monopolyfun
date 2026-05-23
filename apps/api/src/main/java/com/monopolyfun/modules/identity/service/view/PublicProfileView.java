package com.monopolyfun.modules.identity.service.view;

public record PublicProfileView(
        PublicProfileIdentityView profile,
        PublicProfileActivityView activity
) {
}
