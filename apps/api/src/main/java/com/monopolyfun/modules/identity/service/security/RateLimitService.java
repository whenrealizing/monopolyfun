package com.monopolyfun.modules.identity.service.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    public boolean isAllowed(String scope, String key, int limit, Duration window) {
        String bucketKey = scope + "::" + key;
        Bucket bucket = buckets.get(bucketKey, ignored -> newBucket(limit, window));
        return bucket.tryConsume(1);
    }

    public void clear() {
        // 中文注释：测试和运维重置可清空内存限流桶，避免长生命周期进程保留已过期业务上下文。
        buckets.invalidateAll();
    }

    private Bucket newBucket(int limit, Duration window) {
        // 中文注释：Bucket4j 负责令牌桶语义，Caffeine 负责回收低频 key，避免内存桶无限增长。
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillIntervally(limit, window)
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
