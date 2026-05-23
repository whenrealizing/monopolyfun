package com.monopolyfun.modules.upload.domain;

import java.time.Instant;
import java.util.Map;

public record ProofAssetEntity(
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
    public ProofAssetEntity markUploaded(Instant uploadedAt) {
        return new ProofAssetEntity(
                id,
                orderId,
                artifactRef,
                objectKey,
                filename,
                contentType,
                contentLengthBytes,
                checksumSha256,
                storageProvider,
                bucket,
                ProofAssetStatus.UPLOADED,
                uploadedByAccountId,
                purpose,
                visibility,
                metadata,
                createdAt,
                uploadedAt);
    }

    public ProofAssetEntity withStatus(ProofAssetStatus nextStatus, Map<String, Object> nextMetadata, Instant at) {
        return new ProofAssetEntity(
                id,
                orderId,
                artifactRef,
                objectKey,
                filename,
                contentType,
                contentLengthBytes,
                checksumSha256,
                storageProvider,
                bucket,
                nextStatus,
                uploadedByAccountId,
                purpose,
                visibility,
                nextMetadata == null ? metadata : nextMetadata,
                createdAt,
                at);
    }
}
