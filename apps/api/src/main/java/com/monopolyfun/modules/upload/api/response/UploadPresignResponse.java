package com.monopolyfun.modules.upload.api.response;

import java.time.Instant;
import java.util.Map;

public record UploadPresignResponse(
        String assetId,
        String artifactRef,
        String objectKey,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> uploadHeaders,
        Instant expiresAt
) {
}
