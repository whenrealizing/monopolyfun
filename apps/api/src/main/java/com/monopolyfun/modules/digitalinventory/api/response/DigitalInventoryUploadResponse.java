package com.monopolyfun.modules.digitalinventory.api.response;

import java.util.List;

public record DigitalInventoryUploadResponse(
        String itemId,
        int uploaded,
        List<String> inventoryItemIds,
        DigitalInventorySummaryView summary
) {
}
