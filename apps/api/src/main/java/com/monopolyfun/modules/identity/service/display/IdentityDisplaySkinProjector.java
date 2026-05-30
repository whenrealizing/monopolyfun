package com.monopolyfun.modules.identity.service.display;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.service.verification.IdentityFactStatus;
import com.monopolyfun.modules.identity.service.view.IdentityDisplaySkinView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IdentityDisplaySkinProjector {
    private static final String SOURCE_VERIFIED_IDENTITY = "verified_identity";
    private static final String SOURCE_EXTERNAL_IDENTITY = "external_identity";
    private static final String SOURCE_NATIVE = "native";

    public IdentityDisplaySkinProjection project(AccountEntity account, List<IdentityFactEntity> facts) {
        List<IdentityDisplaySkinView> candidates = buildCandidates(account, facts);
        IdentityDisplaySkinPreference preference = readPreference(account.metadata());
        IdentityDisplaySkinView selected = selectCandidate(candidates, preference);
        List<IdentityDisplaySkinView> selectedCandidates = candidates.stream()
                .map(candidate -> select(candidate, matches(candidate, selected)))
                .toList();
        return new IdentityDisplaySkinProjection(selected, selectedCandidates);
    }

    public IdentityDisplaySkinPreference readPreference(Map<String, Object> metadata) {
        Object raw = metadata == null ? null : metadata.get("displaySkin");
        if (!(raw instanceof Map<?, ?> map)) {
            return IdentityDisplaySkinPreference.nativeSkin();
        }
        String source = stringValue(map.get("source"));
        String certifierId = trimToNull(stringValue(map.get("certifierId")));
        if (SOURCE_VERIFIED_IDENTITY.equals(source) && certifierId != null) {
            return new IdentityDisplaySkinPreference(SOURCE_VERIFIED_IDENTITY, certifierId);
        }
        return IdentityDisplaySkinPreference.nativeSkin();
    }

    public Map<String, Object> writePreference(Map<String, Object> metadata, IdentityDisplaySkinPreference preference) {
        Map<String, Object> next = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        Map<String, Object> displaySkin = new LinkedHashMap<>();
        // 中文注释：metadata 只保存皮肤来源和认证器 id，展示字段始终由当前认证事实实时投影。
        displaySkin.put("source", SOURCE_VERIFIED_IDENTITY.equals(preference.source()) ? SOURCE_VERIFIED_IDENTITY : SOURCE_NATIVE);
        displaySkin.put("certifierId", SOURCE_VERIFIED_IDENTITY.equals(preference.source()) ? preference.certifierId() : null);
        next.put("displaySkin", displaySkin);
        return next;
    }

    public boolean hasSelectableCertifier(AccountEntity account, List<IdentityFactEntity> facts, String certifierId) {
        return buildCandidates(account, facts).stream()
                .anyMatch(candidate -> candidate.verified()
                        && SOURCE_VERIFIED_IDENTITY.equals(candidate.source())
                        && certifierId.equals(candidate.certifierId()));
    }

    private List<IdentityDisplaySkinView> buildCandidates(AccountEntity account, List<IdentityFactEntity> facts) {
        List<IdentityDisplaySkinView> candidates = new ArrayList<>();
        candidates.add(nativeCandidate(account));
        facts.stream()
                .filter(fact -> SOURCE_EXTERNAL_IDENTITY.equals(fact.factType()))
                .sorted(Comparator
                        .comparing((IdentityFactEntity fact) -> activeVerifiedFact(fact) ? 0 : 1)
                        .thenComparing(IdentityFactEntity::verifiedAt, Comparator.reverseOrder()))
                .forEach(fact -> {
                    IdentityDisplaySkinView candidate = certifierCandidate(fact, activeVerifiedFact(fact));
                    if (candidate != null && candidates.stream().noneMatch(existing -> matchesExternalIdentity(existing, candidate))) {
                        candidates.add(candidate);
                    }
                });
        return candidates;
    }

    private IdentityDisplaySkinView nativeCandidate(AccountEntity account) {
        String handle = normalizeHandle(account.handle());
        String avatarUrl = trimToNull(stringValue(account.metadata() == null ? null : account.metadata().get("avatarUrl")));
        return new IdentityDisplaySkinView(
                SOURCE_NATIVE,
                null,
                "monopolyfun",
                null,
                account.displayName(),
                handle,
                avatarUrl,
                "/identity",
                "native",
                false,
                false);
    }

    private IdentityDisplaySkinView certifierCandidate(IdentityFactEntity fact, boolean activeVerified) {
        Map<String, Object> payload = fact.payload() == null ? Map.of() : fact.payload();
        String handle = trimToNull(stringValue(payload.get("handle")));
        String displayName = trimToNull(stringValue(payload.get("displayName")));
        String displayHandle = handle == null ? normalizeHandle(fact.platformUserId()) : normalizeHandle(handle);
        String resolvedDisplayName = displayName == null ? displayHandle : displayName;
        if (displayHandle == null || resolvedDisplayName == null) {
            return null;
        }
        return new IdentityDisplaySkinView(
                activeVerified ? SOURCE_VERIFIED_IDENTITY : SOURCE_EXTERNAL_IDENTITY,
                fact.certifierId(),
                fact.provider(),
                fact.platformUserId(),
                resolvedDisplayName,
                displayHandle,
                trimToNull(stringValue(payload.get("avatarUrl"))),
                trimToNull(stringValue(payload.get("profileUrl"))),
                themeKey(fact.provider(), fact.certifierId()),
                activeVerified,
                false);
    }

    private IdentityDisplaySkinView selectCandidate(List<IdentityDisplaySkinView> candidates, IdentityDisplaySkinPreference preference) {
        if (SOURCE_VERIFIED_IDENTITY.equals(preference.source()) && preference.certifierId() != null) {
            return candidates.stream()
                    .filter(candidate -> SOURCE_VERIFIED_IDENTITY.equals(candidate.source()))
                    .filter(candidate -> preference.certifierId().equals(candidate.certifierId()))
                    .findFirst()
                    .map(candidate -> select(candidate, true))
                    .orElseGet(() -> select(candidates.get(0), true));
        }
        return select(candidates.get(0), true);
    }

    private IdentityDisplaySkinView select(IdentityDisplaySkinView candidate, boolean selected) {
        return new IdentityDisplaySkinView(
                candidate.source(),
                candidate.certifierId(),
                candidate.provider(),
                candidate.platformUserId(),
                candidate.displayName(),
                candidate.displayHandle(),
                candidate.avatarUrl(),
                candidate.profileUrl(),
                candidate.themeKey(),
                candidate.verified(),
                selected);
    }

    private boolean activeVerifiedFact(IdentityFactEntity fact) {
        // 中文注释：展示皮肤只能来自仍有效的认证事实，撤销或过期后会自动退回默认皮肤。
        return IdentityFactStatus.isActiveVerified(fact, Instant.now());
    }

    private boolean matches(IdentityDisplaySkinView left, IdentityDisplaySkinView right) {
        return left.source().equals(right.source())
                && java.util.Objects.equals(left.certifierId(), right.certifierId());
    }

    private boolean matchesExternalIdentity(IdentityDisplaySkinView left, IdentityDisplaySkinView right) {
        // 中文注释：同一外部账号只保留一个候选，active verified 排序在前，未认证候选只作为认证提醒入口。
        return !SOURCE_NATIVE.equals(left.source())
                && !SOURCE_NATIVE.equals(right.source())
                && java.util.Objects.equals(left.certifierId(), right.certifierId())
                && java.util.Objects.equals(left.platformUserId(), right.platformUserId());
    }

    private String themeKey(String provider, String certifierId) {
        String raw = ((provider == null ? "" : provider) + " " + (certifierId == null ? "" : certifierId)).toLowerCase();
        if (raw.equals("x") || raw.startsWith("x ") || raw.contains("twitter") || raw.contains("x.com")) return "x";
        if (raw.contains("tiktok")) return "tiktok";
        return "certifier";
    }

    private String normalizeHandle(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceFirst("^@+", "");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
