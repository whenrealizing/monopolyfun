package com.monopolyfun.modules.repo.api.response;

public record GitHubAppWebhookResponse(
        String status,
        String event,
        String deliveryId,
        int renewedClaims
) {
}
