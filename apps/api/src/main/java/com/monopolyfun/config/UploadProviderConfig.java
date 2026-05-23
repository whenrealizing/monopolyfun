package com.monopolyfun.config;

import com.monopolyfun.modules.upload.service.provider.FakeUploadProvider;
import com.monopolyfun.modules.upload.service.provider.S3CompatibleUploadProvider;
import com.monopolyfun.modules.upload.service.provider.UploadProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UploadProviderConfig {
    @Bean(destroyMethod = "close")
    UploadProvider uploadProvider(R2Config config) {
        if (config.getProvider() == null || config.getProvider().isBlank()) {
            // 中文注释：显式空值代表配置错误，启动阶段直接失败，避免部署环境把空字符串当成本地 fake。
            throw new IllegalStateException("Unsupported upload provider: " + config.getProvider());
        }
        String provider = config.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "fake" -> new FakeUploadProvider(config);
            case "s3", "s3-compatible", "r2" -> new S3CompatibleUploadProvider(config);
            default -> throw new IllegalStateException("Unsupported upload provider: " + config.getProvider());
        };
    }
}
