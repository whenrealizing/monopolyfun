package com.monopolyfun.modules.upload.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CompleteUploadRequest(
        @NotBlank String contentType,
        @Positive long contentLengthBytes,
        @NotBlank String checksumSha256
) {
}
