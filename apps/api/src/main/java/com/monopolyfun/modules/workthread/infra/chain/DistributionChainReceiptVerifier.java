package com.monopolyfun.modules.workthread.infra.chain;

import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;

public interface DistributionChainReceiptVerifier {
    void verifyClaim(ProjectRevenueAddressEntity revenueAddress, DistributionBatchEntity batch, DistributionClaimEntity claim, String txHash);
}
