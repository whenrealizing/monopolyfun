package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.infra.IdentityBadgeRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IdentityBadgeProjector {
    private final IdentityBadgeRepository identityBadgeRepository;

    public IdentityBadgeProjector(IdentityBadgeRepository identityBadgeRepository) {
        this.identityBadgeRepository = identityBadgeRepository;
    }

    public void project(AccountEntity account, List<IdentityFactEntity> facts) {
        Instant now = Instant.now();
        List<IdentityBadgeEntity> badges = new ArrayList<>();

        for (IdentityFactEntity fact : facts) {
            // 中文注释：badge 只从当前有效认证事实生成，过期或撤销的事实会在投影重建时被移除。
            if (!IdentityFactStatus.isActiveVerified(fact, now)) {
                continue;
            }
            IdentityCertifierCatalog.badgeSpec(fact.certifierId()).ifPresent(spec -> {
                // 中文注释：认证 Badge 由 certifier catalog 统一配置，新增平台时避免在投影逻辑继续堆分支。
                badges.add(new IdentityBadgeEntity(
                        badgeId(account.id(), "verified", spec.code(), fact.id()),
                        account.id(),
                        "verified",
                        spec.code(),
                        spec.label(),
                        spec.icon(),
                        fact.certifierId(),
                        fact.id(),
                        spec.weight(),
                        now,
                        now));
            });
        }

        badges.add(buildTenureBadge(account, now));
        identityBadgeRepository.replaceForAccount(account.id(), badges);
    }

    private IdentityBadgeEntity buildTenureBadge(AccountEntity account, Instant now) {
        long days = Math.max(0, Duration.between(account.createdAt(), now).toDays());
        // 中文注释：账号资历属于统一 badges 的 tenure 类，身份页只需要消费一个 badges 数组。
        if (days >= 365) {
            return new IdentityBadgeEntity(badgeId(account.id(), "tenure", "tenure_veteran", "native"), account.id(), "tenure", "tenure_veteran", "老手", "flame", null, null, 15, now, now);
        }
        if (days >= 30) {
            return new IdentityBadgeEntity(badgeId(account.id(), "tenure", "tenure_member", "native"), account.id(), "tenure", "tenure_member", "成员", "clock", null, null, 5, now, now);
        }
        return new IdentityBadgeEntity(badgeId(account.id(), "tenure", "tenure_newcomer", "native"), account.id(), "tenure", "tenure_newcomer", "萌新", "sprout", null, null, 1, now, now);
    }

    private String badgeId(String accountId, String kind, String code, String sourceKey) {
        // 中文注释：badge id 固定为业务键，重建投影时保持引用稳定。
        return "ibadge:%s:%s:%s:%s".formatted(accountId, kind, code, sourceKey);
    }
}
