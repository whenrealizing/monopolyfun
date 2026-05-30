package com.monopolyfun.modules.workthread.service.view;

public record RevenueAutomationView(
        String chainId,
        String chainName,
        String asset,
        String tokenType,
        String tokenAddress,
        boolean configured,
        boolean userPromptRequired,
        int nextDistributionRevenueMinor,
        String pricingModel
) {
}
