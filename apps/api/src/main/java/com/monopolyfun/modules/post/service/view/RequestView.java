package com.monopolyfun.modules.post.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RequestView(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String id,
        String requestNo,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String actorAccountId,
        String actorHandle,
        String title,
        String description,
        String deliveryStandard,
        BigDecimal budgetAmount,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient,
        String inventoryPolicy,
        Integer stockTotal,
        int stockFilled,
        String status,
        String tradeStatus,
        String visibility,
        Instant deadlineAt,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        PostItemSummaryView itemSummary,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String resourceKey,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> capabilities,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> blockedCapabilities
) {
    public RequestView(
            String id,
            String requestNo,
            String actorAccountId,
            String actorHandle,
            String title,
            String description,
            String deliveryStandard,
            BigDecimal budgetAmount,
            String currency,
            String paymentMethod,
            String paymentProfile,
            String paymentNetwork,
            String paymentAsset,
            String paymentRecipient,
            String inventoryPolicy,
            Integer stockTotal,
            int stockFilled,
            String status,
            String tradeStatus,
            String visibility,
            Instant deadlineAt,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                requestNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                budgetAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockFilled,
                status,
                tradeStatus,
                visibility,
                deadlineAt,
                createdAt,
                updatedAt,
                null);
    }

    public RequestView(
            String id,
            String requestNo,
            String actorAccountId,
            String actorHandle,
            String title,
            String description,
            String deliveryStandard,
            BigDecimal budgetAmount,
            String currency,
            String paymentMethod,
            String paymentProfile,
            String paymentNetwork,
            String paymentAsset,
            String paymentRecipient,
            String inventoryPolicy,
            Integer stockTotal,
            int stockFilled,
            String status,
            String tradeStatus,
            String visibility,
            Instant deadlineAt,
            Instant createdAt,
            Instant updatedAt,
            PostItemSummaryView itemSummary) {
        this(
                id,
                requestNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                budgetAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockFilled,
                status,
                tradeStatus,
                visibility,
                deadlineAt,
                createdAt,
                updatedAt,
                itemSummary,
                null,
                List.of(),
                List.of());
    }

    public RequestView {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        blockedCapabilities = blockedCapabilities == null ? List.of() : List.copyOf(blockedCapabilities);
    }

    public RequestView withAgentState(
            String resourceKey,
            List<String> capabilities,
            List<Map<String, Object>> blockedCapabilities) {
        return new RequestView(
                id,
                requestNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                budgetAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockFilled,
                status,
                tradeStatus,
                visibility,
                deadlineAt,
                createdAt,
                updatedAt,
                itemSummary,
                resourceKey,
                capabilities,
                blockedCapabilities);
    }
}
