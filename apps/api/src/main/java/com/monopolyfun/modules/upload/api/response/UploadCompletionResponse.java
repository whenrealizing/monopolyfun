package com.monopolyfun.modules.upload.api.response;

import com.monopolyfun.modules.upload.domain.ProofAssetStatus;

import java.time.Instant;

public record UploadCompletionResponse(
        String assetId,
        String artifactRef,
        String objectKey,
        ProofAssetStatus status,
        Instant updatedAt
) {
}
