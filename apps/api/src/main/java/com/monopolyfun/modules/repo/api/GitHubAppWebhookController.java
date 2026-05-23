package com.monopolyfun.modules.repo.api;

import com.monopolyfun.modules.repo.api.response.GitHubAppWebhookResponse;
import com.monopolyfun.modules.repo.service.GitHubAppWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/github/app/webhook")
public class GitHubAppWebhookController {
    private static final String EVENT_HEADER = "X-GitHub-Event";
    private static final String DELIVERY_HEADER = "X-GitHub-Delivery";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final GitHubAppWebhookService webhookService;

    public GitHubAppWebhookController(GitHubAppWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @Hidden
    public GitHubAppWebhookResponse handle(
            @RequestHeader HttpHeaders headers,
            @RequestBody String payload) {
        // 中文注释：GitHub App webhook 是公开入口，安全边界由 HMAC 签名和 session 匹配共同完成。
        return webhookService.handle(
                headers.getFirst(EVENT_HEADER),
                headers.getFirst(DELIVERY_HEADER),
                headers.getFirst(SIGNATURE_HEADER),
                payload);
    }
}
