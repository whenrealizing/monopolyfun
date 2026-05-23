package com.monopolyfun.modules.share.infra;

import com.monopolyfun.modules.share.domain.ShareSettlementHoldEntity;

import java.util.List;
import java.util.Optional;

public interface ShareSettlementHoldRepository {
    Optional<ShareSettlementHoldEntity> findByOrderId(String orderId);

    List<ShareSettlementHoldEntity> findByAccountId(String accountId);

    ShareSettlementHoldEntity save(ShareSettlementHoldEntity hold);
}
