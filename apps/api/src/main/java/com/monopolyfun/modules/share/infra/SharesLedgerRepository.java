package com.monopolyfun.modules.share.infra;

import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.util.List;

public interface SharesLedgerRepository {
    List<SharesLedgerEntryEntity> findByMarketId(String marketId);

    PageResult<SharesLedgerEntryEntity> findByMarketId(String marketId, PageQuery pageQuery);

    List<SharesLedgerEntryEntity> findByAccountId(String accountId);

    PageResult<SharesLedgerEntryEntity> findByAccountId(String accountId, PageQuery pageQuery);

    boolean existsByOrderId(String orderId);

    SharesLedgerEntryEntity save(SharesLedgerEntryEntity entry);

    boolean saveIfAbsent(SharesLedgerEntryEntity entry);
}
