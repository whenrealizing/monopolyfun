package com.monopolyfun.modules.upload.api.response;

import com.monopolyfun.modules.upload.domain.ProofAssetStatus;

import java.time.Instant;
import java.util.Map;

public record UploadDownloadResponse(
        String assetId,
        String artifactRef,
        String filename,
        String contentType,
        long contentLengthBytes,
        String checksumSha256,
        ProofAssetStatus status,
        String downloadMethod,
        String downloadUrl,
        Map<String, String> downloadHeaders,
        Instant expiresAt
) {
}
