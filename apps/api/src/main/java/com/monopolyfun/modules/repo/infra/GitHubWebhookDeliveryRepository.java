package com.monopolyfun.modules.repo.infra;

import java.time.Instant;
import java.util.Map;

public interface GitHubWebhookDeliveryRepository {
    boolean recordOnce(
            String deliveryId,
            String event,
            String repoUrl,
            String headBranch,
            String sessionId,
            Map<String, Object> metadata,
            Instant now);
}
