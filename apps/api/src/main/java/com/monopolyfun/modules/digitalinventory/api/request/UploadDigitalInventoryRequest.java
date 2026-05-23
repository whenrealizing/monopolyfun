package com.monopolyfun.modules.digitalinventory.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UploadDigitalInventoryRequest(
        @NotBlank String actorAccountId,
        @NotEmpty @Size(max = 500) List<@Valid UploadItem> items
) {
    public record UploadItem(
            @NotBlank @Size(max = 5000) String payload
    ) {
    }
}
