package com.monopolyfun.modules.upload.service.provider;

import com.monopolyfun.config.R2Config;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class S3CompatibleUploadProvider implements UploadProvider, AutoCloseable {
    private static final String CHECKSUM_METADATA_HEADER = "x-amz-meta-checksum-sha256";

    private final R2Config config;
    private final S3Presigner presigner;
    private final S3Client s3Client;

    public S3CompatibleUploadProvider(R2Config config) {
        this.config = config;
        String endpoint = required(config.getEndpoint(), "monopolyfun.uploads.endpoint");
        String accessKeyId = required(config.getAccessKeyId(), "monopolyfun.uploads.access-key-id");
        String secretAccessKey = required(config.getSecretAccessKey(), "monopolyfun.uploads.secret-access-key");
        String region = config.getRegion() == null || config.getRegion().isBlank() ? "auto" : config.getRegion();
        var credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        var serviceConfiguration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        // S3/R2 兼容签名交给 AWS SDK 维护，路径风格保持现有 bucket/objectKey URL 形态。
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public PresignedUpload presign(String bucket, String objectKey, String contentType, String checksumSha256, Duration ttl) {
        String normalizedChecksum = required(checksumSha256, "checksumSha256").toLowerCase();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(required(bucket, "bucket"))
                .key(required(objectKey, "objectKey"))
                .contentType(required(contentType, "contentType"))
                .metadata(Map.of("checksum-sha256", normalizedChecksum))
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(request)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        Map<String, String> headers = signedHeaders(presigned.signedHeaders(), contentType, normalizedChecksum);
        return new PresignedUpload(
                "s3-compatible",
                presigned.httpRequest().method().name(),
                presigned.url().toString(),
                Map.copyOf(headers));
    }

    @Override
    public PresignedDownload presignDownload(String bucket, String objectKey, String filename, Duration ttl) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(required(bucket, "bucket"))
                .key(required(objectKey, "objectKey"))
                .responseContentDisposition("attachment; filename=\"" + safeDownloadFilename(filename) + "\"")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return new PresignedDownload(
                "s3-compatible",
                presigned.httpRequest().method().name(),
                presigned.url().toString(),
                Map.of());
    }

    @Override
    public void close() {
        presigner.close();
        s3Client.close();
    }

    @Override
    public UploadObjectVerification verifyObject(
            String bucket,
            String objectKey,
            String expectedContentType,
            long expectedContentLengthBytes,
            String expectedChecksumSha256) {
        try {
            // 中文注释：完成上传前读取远端 HEAD 元数据，登记值和真实对象一致后才进入证明链。
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(required(bucket, "bucket"))
                    .key(required(objectKey, "objectKey"))
                    .build());
            Map<String, String> metadata = response.metadata() == null ? Map.of() : response.metadata();
            Map<String, Object> metadataPayload = new LinkedHashMap<>();
            metadataPayload.putAll(metadata);
            return new UploadObjectVerification(
                    true,
                    response.contentLength(),
                    response.contentType(),
                    metadata.get("checksum-sha256"),
                    response.eTag(),
                    Map.copyOf(metadataPayload));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return new UploadObjectVerification(false, null, null, null, null, Map.of());
            }
            throw exception;
        }
    }

    private Map<String, String> signedHeaders(Map<String, List<String>> signedHeaders, String contentType, String checksum) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name)) {
                headers.put(name, String.join(",", values));
            }
        });
        // 中文注释：R2/S3 签名 header 按大小写不敏感匹配，避免 Content-Type 重复导致 PUT 签名失败。
        putHeaderIfAbsentIgnoreCase(headers, "Content-Type", contentType);
        putHeaderIfAbsentIgnoreCase(headers, CHECKSUM_METADATA_HEADER, checksum);
        return headers;
    }

    private void putHeaderIfAbsentIgnoreCase(Map<String, String> headers, String name, String value) {
        boolean exists = headers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
        if (!exists) {
            headers.put(name, value);
        }
    }

    private String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        return value;
    }

    private String safeDownloadFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "proof-asset" : filename;
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
