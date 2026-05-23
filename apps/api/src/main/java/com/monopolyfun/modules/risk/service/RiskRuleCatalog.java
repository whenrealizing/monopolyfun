package com.monopolyfun.modules.risk.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RiskRuleCatalog {
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_DAY = Duration.ofHours(24);
    private final Map<RiskAction, RiskRule> actionRules = new EnumMap<>(RiskAction.class);

    public RiskRuleCatalog() {
        actionRules.put(RiskAction.PUBLISH_POST, new RiskRule(
                "publish_post_frequency",
                RiskAction.PUBLISH_POST,
                20,
                TEN_MINUTES,
                RiskDecision.FREEZE_ACCOUNT,
                ONE_DAY,
                "high",
                "Too many publish attempts"));
        actionRules.put(RiskAction.CREATE_POST_ITEM, new RiskRule(
                "create_post_item_frequency",
                RiskAction.CREATE_POST_ITEM,
                50,
                TEN_MINUTES,
                RiskDecision.FREEZE_ACCOUNT,
                ONE_DAY,
                "high",
                "Too many post item creation attempts"));
        actionRules.put(RiskAction.CLAIM_POST_ITEM, new RiskRule(
                "claim_post_item_frequency",
                RiskAction.CLAIM_POST_ITEM,
                30,
                TEN_MINUTES,
                RiskDecision.FREEZE_ACCOUNT,
                ONE_DAY,
                "high",
                "Too many post item claim attempts"));
        actionRules.put(RiskAction.UPLOAD_PRESIGN, new RiskRule(
                "upload_presign_frequency_watch",
                RiskAction.UPLOAD_PRESIGN,
                20,
                TEN_MINUTES,
                RiskDecision.WATCH,
                Duration.ZERO,
                "high",
                "Too many upload presign attempts"));
    }

    public Optional<RiskRule> ruleFor(RiskAction action) {
        return Optional.ofNullable(actionRules.get(action));
    }
}
