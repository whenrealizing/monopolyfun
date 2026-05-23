package com.monopolyfun.modules.upload.service.provider;

import com.monopolyfun.config.R2Config;

import java.time.Duration;
import java.util.Map;

public class FakeUploadProvider implements UploadProvider, AutoCloseable {
    private final R2Config config;

    public FakeUploadProvider(R2Config config) {
        this.config = config;
    }

    @Override
    public PresignedUpload presign(String bucket, String objectKey, String contentType, String checksumSha256, Duration ttl) {
        String publicBase = required(config.getPublicBaseUrl(), "monopolyfun.uploads.public-base-url");
        String uploadBase = config.getUploadBaseUrl() == null || config.getUploadBaseUrl().isBlank()
                ? publicBase
                : config.getUploadBaseUrl();
        return new PresignedUpload(
                "fake",
                "PUT",
                buildUrl(uploadBase, objectKey),
                Map.of(
                        "Content-Type", contentType,
                        "x-upload-checksum-sha256", checksumSha256));
    }

    @Override
    public PresignedDownload presignDownload(String bucket, String objectKey, String filename, Duration ttl) {
        String publicBase = required(config.getPublicBaseUrl(), "monopolyfun.uploads.public-base-url");
        return new PresignedDownload(
                "fake",
                "GET",
                buildUrl(publicBase, objectKey),
                Map.of());
    }

    @Override
    public UploadObjectVerification verifyObject(
            String bucket,
            String objectKey,
            String expectedContentType,
            long expectedContentLengthBytes,
            String expectedChecksumSha256) {
        // 中文注释：fake provider 只服务本地测试，按登记值回放远端对象元数据以覆盖完整完成链路。
        return new UploadObjectVerification(
                true,
                expectedContentLengthBytes,
                expectedContentType,
                expectedChecksumSha256 == null ? null : expectedChecksumSha256.toLowerCase(),
                "fake-etag-" + objectKey,
                Map.of("provider", "fake", "bucket", bucket, "objectKey", objectKey));
    }

    private String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        return value;
    }

    private String buildUrl(String baseUrl, String objectKey) {
        return baseUrl.replaceAll("/+$", "") + "/" + objectKey;
    }

    @Override
    public void close() {
    }
}
