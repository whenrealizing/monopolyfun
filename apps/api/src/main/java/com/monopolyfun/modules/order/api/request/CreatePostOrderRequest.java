package com.monopolyfun.modules.order.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreatePostOrderRequest(
        @NotBlank String actorAccountId,
        String buyerNote,
        String paymentRecipient,
        Map<String, Object> deliveryInput
) {
}
