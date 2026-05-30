package com.monopolyfun.modules.workthread.service.view;

import java.util.List;

public record DistributionClaimView(
        String batchId,
        String projectId,
        String period,
        String accountId,
        String walletAddress,
        int amountMinor,
        String token,
        List<String> proof,
        String authorization,
        String txHash,
        String status
) {
}
