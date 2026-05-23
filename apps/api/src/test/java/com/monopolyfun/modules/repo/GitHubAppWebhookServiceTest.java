package com.monopolyfun.modules.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.GitHubAppConfig;
import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;
import com.monopolyfun.modules.repo.infra.GitHubWebhookDeliveryRepository;
import com.monopolyfun.modules.repo.infra.RepoDeliverySessionRepository;
import com.monopolyfun.modules.repo.service.GitHubAppWebhookService;
import com.monopolyfun.modules.work.infra.WorkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubAppWebhookServiceTest {
    private static final String SECRET = "webhook-secret";

    private static String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return "sha256=" + hex;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void pullRequestWebhookRenewsClaimLeaseAndRecordsProgress() {
        RepoDeliverySessionRepository sessions = Mockito.mock(RepoDeliverySessionRepository.class);
        WorkRepository workRepository = Mockito.mock(WorkRepository.class);
        GitHubWebhookDeliveryRepository deliveryRepository = Mockito.mock(GitHubWebhookDeliveryRepository.class);
        GitHubAppWebhookService service = service(sessions, deliveryRepository, workRepository);
        RepoDeliverySessionEntity session = session();
        String payload = """
                {
                  "repository": {"html_url": "https://github.com/acme/widgets"},
                  "pull_request": {
                    "html_url": "https://github.com/acme/widgets/pull/7",
                    "head": {"ref": "mf/order-1-acct-worker", "sha": "abc123"},
                    "base": {"repo": {"html_url": "https://github.com/acme/widgets"}}
                  }
                }
                """;
        when(sessions.findActiveByRepoUrlAndHeadBranch("https://github.com/acme/widgets", "mf/order-1-acct-worker"))
                .thenReturn(Optional.of(session));
        when(workRepository.renewClaimLease(
                eq("acct-worker"),
                eq("wb-delivery-result-ORDER-1"),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(1);
        when(deliveryRepository.recordOnce(
                eq("delivery-1"),
                eq("pull_request"),
                eq("https://github.com/acme/widgets"),
                eq("mf/order-1-acct-worker"),
                eq("session-1"),
                anyMap(),
                any(Instant.class)))
                .thenReturn(true);

        var response = service.handle("pull_request", "delivery-1", signature(payload), payload);

        assertThat(response.status()).isEqualTo("observed");
        assertThat(response.renewedClaims()).isEqualTo(1);
        verify(workRepository).renewClaimLease(
                eq("acct-worker"),
                eq("wb-delivery-result-ORDER-1"),
                any(Instant.class),
                any(Instant.class));
        ArgumentCaptor<RepoDeliverySessionEntity> saved = ArgumentCaptor.forClass(RepoDeliverySessionEntity.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo("progress_observed");
        assertThat(saved.getValue().prUrl()).isEqualTo("https://github.com/acme/widgets/pull/7");
        assertThat(saved.getValue().headCommit()).isEqualTo("abc123");
        assertThat(saved.getValue().metadata()).containsKey("lastGitHubWebhook");
    }

    @Test
    void duplicateDeliveryDoesNotRenewClaimLease() {
        RepoDeliverySessionRepository sessions = Mockito.mock(RepoDeliverySessionRepository.class);
        WorkRepository workRepository = Mockito.mock(WorkRepository.class);
        GitHubWebhookDeliveryRepository deliveryRepository = Mockito.mock(GitHubWebhookDeliveryRepository.class);
        GitHubAppWebhookService service = service(sessions, deliveryRepository, workRepository);
        String payload = """
                {
                  "repository": {"html_url": "https://github.com/acme/widgets"},
                  "ref": "refs/heads/mf/order-1-acct-worker",
                  "after": "abc123"
                }
                """;
        when(sessions.findActiveByRepoUrlAndHeadBranch("https://github.com/acme/widgets", "mf/order-1-acct-worker"))
                .thenReturn(Optional.of(session()));
        when(deliveryRepository.recordOnce(
                eq("delivery-1"),
                eq("push"),
                eq("https://github.com/acme/widgets"),
                eq("mf/order-1-acct-worker"),
                eq("session-1"),
                anyMap(),
                any(Instant.class)))
                .thenReturn(false);

        var response = service.handle("push", "delivery-1", signature(payload), payload);

        assertThat(response.status()).isEqualTo("duplicate");
        assertThat(response.renewedClaims()).isZero();
        verify(workRepository, never()).renewClaimLease(any(), any(), any(), any());
    }

    @Test
    void invalidSignatureIsRejected() {
        GitHubAppWebhookService service = service(
                Mockito.mock(RepoDeliverySessionRepository.class),
                Mockito.mock(GitHubWebhookDeliveryRepository.class),
                Mockito.mock(WorkRepository.class));

        assertThatThrownBy(() -> service.handle("push", "delivery-1", "sha256=bad", "{}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    private GitHubAppWebhookService service(
            RepoDeliverySessionRepository sessions,
            GitHubWebhookDeliveryRepository deliveryRepository,
            WorkRepository workRepository) {
        GitHubAppConfig config = new GitHubAppConfig();
        config.setWebhookSecret(SECRET);
        return new GitHubAppWebhookService(config, new ObjectMapper(), sessions, deliveryRepository, workRepository);
    }

    private RepoDeliverySessionEntity session() {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new RepoDeliverySessionEntity(
                "session-1",
                "PRJ-1",
                "ORDER-1",
                "github",
                "https://github.com/acme/widgets",
                "https://token@github.com/acme/widgets.git",
                "main",
                "mf/order-1-acct-worker",
                null,
                null,
                null,
                "issued",
                "openclaw",
                "acct-worker",
                "secret://repo-delivery-sessions/ORDER-1/github-token",
                now.plusSeconds(3600),
                Map.of("repoOwner", "acme", "repoName", "widgets"),
                now,
                now);
    }
}
