package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;

import java.util.List;

public interface IdentityBadgeRepository {
    List<IdentityBadgeEntity> findByAccountId(String accountId);

    void replaceForAccount(String accountId, List<IdentityBadgeEntity> badges);
}
