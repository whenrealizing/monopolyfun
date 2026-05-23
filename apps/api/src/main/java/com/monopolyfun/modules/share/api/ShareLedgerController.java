package com.monopolyfun.modules.share.api;

import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.modules.share.infra.SharesLedgerRepository;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ShareLedgerController {
    private final SharesLedgerRepository sharesLedgerRepository;

    public ShareLedgerController(SharesLedgerRepository sharesLedgerRepository) {
        this.sharesLedgerRepository = sharesLedgerRepository;
    }

    @GetMapping("/markets/{marketId}/shares-ledger")
    public PageResult<SharesLedgerEntryEntity> getMarketSharesLedger(
            @PathVariable String marketId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        return sharesLedgerRepository.findByMarketId(marketId, PageQuery.of(limit, cursor));
    }

    @GetMapping("/accounts/{accountId}/shares-ledger")
    public PageResult<SharesLedgerEntryEntity> getAccountSharesLedger(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        return sharesLedgerRepository.findByAccountId(accountId, PageQuery.of(limit, cursor));
    }
}
