package com.monopolyfun.modules.post.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublishRequestRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String description,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient,
        String deadlineAt,
        @NotEmpty @Size(max = 20) List<@Valid PublishPostItemRequest> items
) {
}
