package com.monopolyfun.modules.post.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOfferPostRequest(
        @NotBlank String actorAccountId,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 1000) String deliveryStandard,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient
) {
}
