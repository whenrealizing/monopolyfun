package com.monopolyfun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "monopolyfun.uploads")
public class R2Config {
    private String provider = "fake";
    private String bucket;
    private String endpoint;
    private String region = "auto";
    private String accessKeyId;
    private String secretAccessKey;
    private String publicBaseUrl;
    private String uploadBaseUrl;
    private Duration presignTtl = Duration.ofMinutes(15);
    private Duration downloadPresignTtl = Duration.ofMinutes(5);
    private long maxUploadBytes = 10 * 1024 * 1024;
    private List<String> allowedContentTypes = List.of("image/png", "image/jpeg", "image/webp", "application/pdf");

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getUploadBaseUrl() {
        return uploadBaseUrl;
    }

    public void setUploadBaseUrl(String uploadBaseUrl) {
        this.uploadBaseUrl = uploadBaseUrl;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public Duration getDownloadPresignTtl() {
        return downloadPresignTtl;
    }

    public void setDownloadPresignTtl(Duration downloadPresignTtl) {
        this.downloadPresignTtl = downloadPresignTtl;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
