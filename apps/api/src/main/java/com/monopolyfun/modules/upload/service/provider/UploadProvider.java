package com.monopolyfun.modules.upload.service.provider;

import java.time.Duration;
import java.util.Map;

public interface UploadProvider {
    PresignedUpload presign(String bucket, String objectKey, String contentType, String checksumSha256, Duration ttl);

    PresignedDownload presignDownload(String bucket, String objectKey, String filename, Duration ttl);

    UploadObjectVerification verifyObject(
            String bucket,
            String objectKey,
            String expectedContentType,
            long expectedContentLengthBytes,
            String expectedChecksumSha256);

    record PresignedUpload(
            String providerName,
            String uploadMethod,
            String uploadUrl,
            Map<String, String> uploadHeaders
    ) {
    }

    record PresignedDownload(
            String providerName,
            String downloadMethod,
            String downloadUrl,
            Map<String, String> downloadHeaders
    ) {
    }

    record UploadObjectVerification(
            boolean exists,
            Long contentLengthBytes,
            String contentType,
            String checksumSha256,
            String eTag,
            Map<String, Object> metadata
    ) {
    }
}
