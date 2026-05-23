package com.monopolyfun.modules.post.service.mapper;

import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.modules.post.service.view.PostItemSummaryView;
import com.monopolyfun.modules.post.service.view.RequestView;

import java.util.Map;

public final class PostViewMapper {
    private PostViewMapper() {
    }

    public static OfferView offer(OfferEntity offer) {
        if (offer == null) return null;
        return new OfferView(
                offer.id(),
                offer.offerNo(),
                offer.actorAccountId(),
                null,
                offer.title(),
                offer.description(),
                offer.deliveryStandard(),
                offer.priceAmount(),
                offer.currency(),
                offer.paymentMethod(),
                offer.paymentProfile(),
                offer.paymentNetwork(),
                offer.paymentAsset(),
                offer.paymentRecipient(),
                offer.inventoryPolicy().name().toLowerCase(),
                offer.stockTotal(),
                offer.stockSold(),
                offer.status().name().toLowerCase(),
                metadataText(offer.metadata(), "tradeStatus", "open"),
                metadataText(offer.metadata(), "visibility", "market_public"),
                offer.createdAt(),
                offer.updatedAt());
    }

    public static OfferView publicOffer(OfferEntity offer, String actorHandle) {
        return publicOffer(offer, actorHandle, null);
    }

    public static OfferView publicOffer(OfferEntity offer, String actorHandle, PostItemSummaryView itemSummary) {
        if (offer == null) return null;
        // 中文注释：公开 Offer 只暴露 handle，内部账号主键保留在私有读面和命令校验链路。
        return new OfferView(
                null,
                offer.offerNo(),
                null,
                actorHandle,
                offer.title(),
                offer.description(),
                offer.deliveryStandard(),
                offer.priceAmount(),
                offer.currency(),
                offer.paymentMethod(),
                offer.paymentProfile(),
                offer.paymentNetwork(),
                offer.paymentAsset(),
                offer.paymentRecipient(),
                offer.inventoryPolicy().name().toLowerCase(),
                offer.stockTotal(),
                offer.stockSold(),
                offer.status().name().toLowerCase(),
                metadataText(offer.metadata(), "tradeStatus", "open"),
                metadataText(offer.metadata(), "visibility", "market_public"),
                offer.createdAt(),
                offer.updatedAt(),
                itemSummary);
    }

    public static RequestView request(RequestEntity request) {
        if (request == null) return null;
        return new RequestView(
                request.id(),
                request.requestNo(),
                request.actorAccountId(),
                null,
                request.title(),
                request.description(),
                request.deliveryStandard(),
                request.budgetAmount(),
                request.currency(),
                request.paymentMethod(),
                request.paymentProfile(),
                request.paymentNetwork(),
                request.paymentAsset(),
                request.paymentRecipient(),
                request.inventoryPolicy().name().toLowerCase(),
                request.stockTotal(),
                request.stockFilled(),
                request.status().name().toLowerCase(),
                metadataText(request.metadata(), "tradeStatus", "open"),
                metadataText(request.metadata(), "visibility", "market_public"),
                request.deadlineAt(),
                request.createdAt(),
                request.updatedAt());
    }

    public static RequestView publicRequest(RequestEntity request, String actorHandle) {
        return publicRequest(request, actorHandle, null);
    }

    public static RequestView publicRequest(RequestEntity request, String actorHandle, PostItemSummaryView itemSummary) {
        if (request == null) return null;
        // 中文注释：公开 Request 只暴露 handle，避免前端把公开身份误当内部账号主键。
        return new RequestView(
                null,
                request.requestNo(),
                null,
                actorHandle,
                request.title(),
                request.description(),
                request.deliveryStandard(),
                request.budgetAmount(),
                request.currency(),
                request.paymentMethod(),
                request.paymentProfile(),
                request.paymentNetwork(),
                request.paymentAsset(),
                request.paymentRecipient(),
                request.inventoryPolicy().name().toLowerCase(),
                request.stockTotal(),
                request.stockFilled(),
                request.status().name().toLowerCase(),
                metadataText(request.metadata(), "tradeStatus", "open"),
                metadataText(request.metadata(), "visibility", "market_public"),
                request.deadlineAt(),
                request.createdAt(),
                request.updatedAt(),
                itemSummary);
    }

    private static String metadataText(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) return fallback;
        Object value = metadata.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
