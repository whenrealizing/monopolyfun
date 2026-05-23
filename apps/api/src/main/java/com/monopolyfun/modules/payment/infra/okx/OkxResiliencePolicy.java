package com.monopolyfun.modules.payment.infra.okx;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OkxResiliencePolicy {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BACKOFF = Duration.ofMillis(300);

    public int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    public Duration backoff() {
        return BACKOFF;
    }

    public boolean retryableStatusCode(int statusCode) {
        // 中文注释：为后续接入 resilience4j/Spring Retry 固化 OKX 网关的重试边界，避免在支付业务里散落重试判断。
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
