package com.monopolyfun.modules.workthread.service.view;

public record ProjectRevenueAddressView(
        String id,
        String projectId,
        String chainId,
        String contractAddress,
        String tokenAddress,
        String status
) {
}
