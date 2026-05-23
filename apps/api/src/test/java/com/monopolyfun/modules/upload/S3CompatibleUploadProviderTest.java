package com.monopolyfun;

import com.monopolyfun.config.R2Config;
import com.monopolyfun.modules.upload.service.provider.S3CompatibleUploadProvider;
import com.monopolyfun.modules.upload.service.provider.UploadProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3CompatibleUploadProviderTest {
    @Test
    void presignsR2CompatiblePutObjectWithRequiredHeaders() {
        R2Config config = new R2Config();
        config.setEndpoint("https://r2.example.test");
        config.setRegion("auto");
        config.setAccessKeyId("test-access-key");
        config.setSecretAccessKey("test-secret-key");
        config.setPublicBaseUrl("https://cdn.example.test/assets");

        try (S3CompatibleUploadProvider provider = new S3CompatibleUploadProvider(config)) {
            UploadProvider.PresignedUpload upload = provider.presign(
                    "proof-assets",
                    "orders/order-1/proof.png",
                    "image/png",
                    "ABCDEF123456",
                    Duration.ofMinutes(10));

            URI uri = URI.create(upload.uploadUrl());
            assertThat(upload.providerName()).isEqualTo("s3-compatible");
            assertThat(upload.uploadMethod()).isEqualTo("PUT");
            assertThat(uri.getHost()).isEqualTo("r2.example.test");
            assertThat(uri.getPath()).isEqualTo("/proof-assets/orders/order-1/proof.png");
            assertThat(uri.getQuery()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
            assertThat(uri.getQuery()).contains("X-Amz-Expires=600");
            assertThat(uri.getQuery()).contains("X-Amz-Signature=");
            assertThat(upload.uploadHeaders().entrySet()).anySatisfy(entry -> {
                assertThat(entry.getKey()).isEqualToIgnoringCase("Content-Type");
                assertThat(entry.getValue()).isEqualTo("image/png");
            });
            assertThat(upload.uploadHeaders().keySet().stream().filter(key -> key.equalsIgnoreCase("Content-Type"))).hasSize(1);
            assertThat(upload.uploadHeaders()).containsEntry("x-amz-meta-checksum-sha256", "abcdef123456");
            UploadProvider.PresignedDownload download = provider.presignDownload(
                    "proof-assets",
                    "orders/order-1/proof.png",
                    "proof.png",
                    Duration.ofMinutes(5));
            URI downloadUri = URI.create(download.downloadUrl());
            assertThat(download.downloadMethod()).isEqualTo("GET");
            assertThat(downloadUri.getHost()).isEqualTo("r2.example.test");
            assertThat(downloadUri.getQuery()).contains("X-Amz-Expires=300");
        }
    }
}
