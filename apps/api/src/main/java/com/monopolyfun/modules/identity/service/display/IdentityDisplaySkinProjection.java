package com.monopolyfun.modules.identity.service.display;

import com.monopolyfun.modules.identity.service.view.IdentityDisplaySkinView;

import java.util.List;

public record IdentityDisplaySkinProjection(
        IdentityDisplaySkinView selected,
        List<IdentityDisplaySkinView> candidates
) {
}
