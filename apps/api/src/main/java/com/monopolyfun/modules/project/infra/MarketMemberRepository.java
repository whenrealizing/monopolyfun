package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.MarketMemberEntity;

import java.util.List;
import java.util.Optional;

public interface MarketMemberRepository {
    List<MarketMemberEntity> findByMarketId(String marketId);

    Optional<MarketMemberEntity> findByMarketIdAndAccountId(String marketId, String accountId);

    MarketMemberEntity save(MarketMemberEntity member);

    void deleteByMarketIdAndAccountId(String marketId, String accountId);
}
