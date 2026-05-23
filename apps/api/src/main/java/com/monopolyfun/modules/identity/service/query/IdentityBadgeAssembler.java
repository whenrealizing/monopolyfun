package com.monopolyfun.modules.identity.service.query;

import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class IdentityBadgeAssembler {
    private IdentityBadgeAssembler() {
    }

    static List<IdentityBadgeEntity> unify(
            List<IdentityBadgeEntity> persistedBadges,
            List<IdentityBadgeEntity> roleBadges) {
        List<IdentityBadgeEntity> sortedBadges = new ArrayList<>();
        sortedBadges.addAll(persistedBadges);
        sortedBadges.addAll(roleBadges);
        sortedBadges.sort(Comparator
                .comparingInt(IdentityBadgeEntity::weight)
                .reversed()
                .thenComparing(IdentityBadgeEntity::createdAt));

        Map<String, IdentityBadgeEntity> badgesByKindAndCode = new LinkedHashMap<>();
        for (IdentityBadgeEntity badge : sortedBadges) {
            // 中文注释：公开主页和身份中心共用同一去重规则，角色徽章与认证徽章始终保持一套展示协议。
            badgesByKindAndCode.putIfAbsent("%s:%s".formatted(badge.kind(), badge.code()), badge);
        }
        return new ArrayList<>(badgesByKindAndCode.values());
    }
}
