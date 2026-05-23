package com.monopolyfun.modules.upload.service.view;

import com.monopolyfun.modules.upload.domain.ProofAssetStatus;

import java.time.Instant;
import java.util.Map;

public record ProofAssetView(
        String id,
        String orderId,
        String artifactRef,
        String objectKey,
        String filename,
        String contentType,
        long contentLengthBytes,
        String checksumSha256,
        String storageProvider,
        String bucket,
        ProofAssetStatus status,
        String uploadedByAccountId,
        String purpose,
        String visibility,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
