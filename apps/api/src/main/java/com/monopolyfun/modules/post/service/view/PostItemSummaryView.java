package com.monopolyfun.modules.post.service.view;

import java.math.BigDecimal;

public record PostItemSummaryView(
        long itemCount,
        long openItemCount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer totalQuantity,
        Integer remainingQuantity,
        String currency
) {
}
