package com.monopolyfun.config;

import com.monopolyfun.modules.upload.service.provider.FakeUploadProvider;
import com.monopolyfun.modules.upload.service.provider.S3CompatibleUploadProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadProviderConfigTest {
    private final UploadProviderConfig uploadProviderConfig = new UploadProviderConfig();

    @Test
    void unknownProviderFailsClosed() {
        R2Config config = new R2Config();
        config.setProvider("typo");

        assertThrows(IllegalStateException.class, () -> uploadProviderConfig.uploadProvider(config));
    }

    @Test
    void blankProviderFailsClosed() {
        R2Config config = new R2Config();
        config.setProvider(" ");

        assertThrows(IllegalStateException.class, () -> uploadProviderConfig.uploadProvider(config));
    }

    @Test
    void supportedProvidersResolveToConcreteProvider() {
        R2Config fake = new R2Config();
        fake.setProvider("fake");
        assertInstanceOf(FakeUploadProvider.class, uploadProviderConfig.uploadProvider(fake));

        R2Config r2 = new R2Config();
        r2.setProvider("r2");
        r2.setEndpoint("https://r2.example.com");
        r2.setAccessKeyId("access-key");
        r2.setSecretAccessKey("secret-key");
        assertInstanceOf(S3CompatibleUploadProvider.class, uploadProviderConfig.uploadProvider(r2));
    }
}
