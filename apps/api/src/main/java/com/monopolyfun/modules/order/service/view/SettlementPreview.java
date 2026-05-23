package com.monopolyfun.modules.order.service.view;

import java.math.BigDecimal;

public record SettlementPreview(
        String settlementType,
        BigDecimal amount,
        String accountId,
        boolean willMintShares,
        String description
) {
}
