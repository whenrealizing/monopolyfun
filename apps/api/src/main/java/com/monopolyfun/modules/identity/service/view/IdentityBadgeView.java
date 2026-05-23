package com.monopolyfun.modules.identity.service.view;

public record IdentityBadgeView(
        String code,
        String label,
        String kind,
        String icon,
        int weight
) {
}
