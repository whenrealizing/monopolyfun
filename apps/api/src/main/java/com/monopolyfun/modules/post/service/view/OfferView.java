package com.monopolyfun.modules.post.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OfferView(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String id,
        String offerNo,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String actorAccountId,
        String actorHandle,
        String title,
        String description,
        String deliveryStandard,
        BigDecimal priceAmount,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient,
        String inventoryPolicy,
        Integer stockTotal,
        int stockSold,
        String status,
        String tradeStatus,
        String visibility,
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
    public OfferView(
            String id,
            String offerNo,
            String actorAccountId,
            String actorHandle,
            String title,
            String description,
            String deliveryStandard,
            BigDecimal priceAmount,
            String currency,
            String paymentMethod,
            String paymentProfile,
            String paymentNetwork,
            String paymentAsset,
            String paymentRecipient,
            String inventoryPolicy,
            Integer stockTotal,
            int stockSold,
            String status,
            String tradeStatus,
            String visibility,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                offerNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                priceAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockSold,
                status,
                tradeStatus,
                visibility,
                createdAt,
                updatedAt,
                null);
    }

    public OfferView(
            String id,
            String offerNo,
            String actorAccountId,
            String actorHandle,
            String title,
            String description,
            String deliveryStandard,
            BigDecimal priceAmount,
            String currency,
            String paymentMethod,
            String paymentProfile,
            String paymentNetwork,
            String paymentAsset,
            String paymentRecipient,
            String inventoryPolicy,
            Integer stockTotal,
            int stockSold,
            String status,
            String tradeStatus,
            String visibility,
            Instant createdAt,
            Instant updatedAt,
            PostItemSummaryView itemSummary) {
        this(
                id,
                offerNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                priceAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockSold,
                status,
                tradeStatus,
                visibility,
                createdAt,
                updatedAt,
                itemSummary,
                null,
                List.of(),
                List.of());
    }

    public OfferView {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        blockedCapabilities = blockedCapabilities == null ? List.of() : List.copyOf(blockedCapabilities);
    }

    public OfferView withAgentState(
            String resourceKey,
            List<String> capabilities,
            List<Map<String, Object>> blockedCapabilities) {
        return new OfferView(
                id,
                offerNo,
                actorAccountId,
                actorHandle,
                title,
                description,
                deliveryStandard,
                priceAmount,
                currency,
                paymentMethod,
                paymentProfile,
                paymentNetwork,
                paymentAsset,
                paymentRecipient,
                inventoryPolicy,
                stockTotal,
                stockSold,
                status,
                tradeStatus,
                visibility,
                createdAt,
                updatedAt,
                itemSummary,
                resourceKey,
                capabilities,
                blockedCapabilities);
    }
}
