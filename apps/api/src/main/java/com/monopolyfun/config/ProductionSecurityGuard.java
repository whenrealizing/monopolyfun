package com.monopolyfun.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityGuard {
    private static final int MIN_SECRET_LENGTH = 24;
    private static final String DEV_PAYMENT_CALLBACK_SECRET = "monopolyfun-dev-secret";
    private static final String DEV_DIGITAL_INVENTORY_SECRET = "monopolyfun-digital-inventory-dev-secret";

    private final SecurityCookieProperties security;
    private final PaymentConfig paymentConfig;
    private final R2Config r2Config;
    private final DigitalInventoryConfig digitalInventoryConfig;
    private final org.springframework.core.env.Environment environment;

    public ProductionSecurityGuard(
            SecurityCookieProperties security,
            PaymentConfig paymentConfig,
            R2Config r2Config,
            DigitalInventoryConfig digitalInventoryConfig,
            org.springframework.core.env.Environment environment) {
        this.security = security;
        this.paymentConfig = paymentConfig;
        this.r2Config = r2Config;
        this.digitalInventoryConfig = digitalInventoryConfig;
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionDefaults() {
        if (paymentConfig.isFakeCallbackEnabled()) {
            // 中文注释：fake 回调即使在本地也需要显式密钥，避免调试入口使用空签名边界。
            requireUsableSecret(paymentConfig.getCallbackSecret(), "PAYMENT_CALLBACK_SECRET");
        }
        if (!security.isProduction()) {
            return;
        }
        require(security.isCookieSecure(), "monopolyfun.security.cookie-secure must be true in production");
        require(!"fake".equalsIgnoreCase(paymentConfig.getProvider()), "PAYMENT_PROVIDER must use a production provider");
        require(!paymentConfig.isFakeCallbackEnabled(), "PAYMENT_FAKE_CALLBACK_ENABLED must be false in production");
        require(!"fake".equalsIgnoreCase(r2Config.getProvider()), "UPLOAD_PROVIDER must use a production provider");
        requireRotatedSecret(paymentConfig.getCallbackSecret(), DEV_PAYMENT_CALLBACK_SECRET, "PAYMENT_CALLBACK_SECRET");
        requireRotatedSecret(digitalInventoryConfig.getEncryptionSecret(), DEV_DIGITAL_INVENTORY_SECRET, "DIGITAL_INVENTORY_ENCRYPTION_SECRET");
        require(!"postgres".equals(environment.getProperty("spring.datasource.password")), "DATABASE_PASSWORD must be rotated");
        require(!isLocalDatabaseUrl(environment.getProperty("spring.datasource.url")), "DATABASE_URL must point to an external PostgreSQL service");
        require(hasText(r2Config.getBucket()), "UPLOAD_BUCKET is required");
    }

    private void requireRotatedSecret(String value, String forbidden, String name) {
        requireUsableSecret(value, name);
        require(!forbidden.equals(value), name + " must be rotated");
    }

    private void requireUsableSecret(String value, String name) {
        require(hasText(value), name + " is required");
        require(value.trim().length() >= MIN_SECRET_LENGTH, name + " must be at least " + MIN_SECRET_LENGTH + " characters");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            // 中文注释：生产启动阶段 fail-fast，避免使用本地默认密钥或 fake provider 对外服务。
            throw new IllegalStateException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isLocalDatabaseUrl(String value) {
        return value != null && (value.contains("localhost") || value.contains("127.0.0.1"));
    }

    private boolean isLocalUrl(String value) {
        return value != null && (value.contains("localhost") || value.contains("127.0.0.1"));
    }
}
