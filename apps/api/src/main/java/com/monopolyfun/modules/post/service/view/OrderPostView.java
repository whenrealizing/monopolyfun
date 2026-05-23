package com.monopolyfun.modules.post.service.view;

public record OrderPostView(
        String kind,
        String id,
        String title,
        String summary,
        String deliveryStandard,
        String settlementSummary,
        String inventorySummary,
        String status
) {
}
