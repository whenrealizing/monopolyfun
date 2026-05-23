package com.monopolyfun.modules.digitalinventory.api.response;

public record DigitalInventorySummaryView(
        String itemId,
        int available,
        int reserved,
        int delivered,
        int voided,
        int total
) {
}
