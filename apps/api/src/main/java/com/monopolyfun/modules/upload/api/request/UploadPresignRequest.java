package com.monopolyfun.modules.upload.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadPresignRequest(
        @NotBlank String orderId,
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long contentLengthBytes,
        @NotBlank String checksumSha256,
        @Size(max = 40) String purpose,
        @Size(max = 40) String visibility
) {
}
