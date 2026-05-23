package com.monopolyfun;

import com.monopolyfun.config.R2Config;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.upload.api.request.CompleteUploadRequest;
import com.monopolyfun.modules.upload.api.request.UploadPresignRequest;
import com.monopolyfun.modules.upload.api.response.UploadCompletionResponse;
import com.monopolyfun.modules.upload.api.response.UploadPresignResponse;
import com.monopolyfun.modules.upload.domain.ProofAssetEntity;
import com.monopolyfun.modules.upload.domain.ProofAssetStatus;
import com.monopolyfun.modules.upload.infra.ProofAssetRepository;
import com.monopolyfun.modules.upload.service.UploadService;
import com.monopolyfun.modules.upload.service.provider.FakeUploadProvider;
import com.monopolyfun.modules.upload.service.provider.UploadProvider;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadServiceTest {
    @Test
    void presignCreatesPendingAssetAndCompleteMarksVerified() {
        R2Config config = new R2Config();
        config.setBucket("proof-bucket");
        config.setProvider("fake");
        config.setUploadBaseUrl("https://uploads.example/proof-bucket");
        config.setPublicBaseUrl("https://public.example/proof-bucket");
        config.setPresignTtl(Duration.ofMinutes(15));
        config.setMaxUploadBytes(5_000_000);
        config.setAllowedContentTypes(List.of("image/png"));

        InMemoryProofAssetRepository proofAssetRepository = new InMemoryProofAssetRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        UploadService service = new UploadService(
                config,
                new StubOrderRepository(),
                proofAssetRepository,
                new FakeUploadProvider(config),
                new CurrentAccountAccess(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(AccountRiskGuard.class));

        UploadPresignResponse presigned = service.presign(new UploadPresignRequest(
                "MF260505ORD000001X",
                "proof.png",
                "image/png",
                1024,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                null));

        assertNotNull(presigned.assetId());
        assertEquals("asset://" + presigned.assetId(), presigned.artifactRef());
        assertEquals("PUT", presigned.uploadMethod());
        assertEquals("image/png", presigned.uploadHeaders().get("Content-Type"));
        assertTrue(proofAssetRepository.findById(presigned.assetId()).isPresent());
        assertEquals(ProofAssetStatus.PENDING, proofAssetRepository.findById(presigned.assetId()).orElseThrow().status());

        UploadCompletionResponse completed = service.complete(
                presigned.assetId(),
                new CompleteUploadRequest(
                        "image/png",
                        1024,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));

        assertEquals(ProofAssetStatus.VERIFIED, completed.status());
        assertEquals(ProofAssetStatus.VERIFIED, proofAssetRepository.findById(presigned.assetId()).orElseThrow().status());
    }

    @Test
    void completeQuarantinesWhenFilenameExtensionMismatchesContentType() {
        R2Config config = new R2Config();
        config.setBucket("proof-bucket");
        config.setProvider("fake");
        config.setUploadBaseUrl("https://uploads.example/proof-bucket");
        config.setPublicBaseUrl("https://public.example/proof-bucket");
        config.setPresignTtl(Duration.ofMinutes(15));
        config.setMaxUploadBytes(5_000_000);
        config.setAllowedContentTypes(List.of("image/png"));

        InMemoryProofAssetRepository proofAssetRepository = new InMemoryProofAssetRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        UploadService service = new UploadService(
                config,
                new StubOrderRepository(),
                proofAssetRepository,
                new FakeUploadProvider(config),
                new CurrentAccountAccess(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(AccountRiskGuard.class));

        UploadPresignResponse presigned = service.presign(new UploadPresignRequest(
                "MF260505ORD000001X",
                "proof.pdf",
                "image/png",
                1024,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                null));

        UploadCompletionResponse completed = service.complete(
                presigned.assetId(),
                new CompleteUploadRequest(
                        "image/png",
                        1024,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));

        assertEquals(ProofAssetStatus.QUARANTINED, completed.status());
    }

    @Test
    void completeRejectsWhenRemoteObjectMetadataDoesNotMatchRegistration() {
        R2Config config = new R2Config();
        config.setBucket("proof-bucket");
        config.setProvider("fake");
        config.setUploadBaseUrl("https://uploads.example/proof-bucket");
        config.setPublicBaseUrl("https://public.example/proof-bucket");
        config.setPresignTtl(Duration.ofMinutes(15));
        config.setMaxUploadBytes(5_000_000);
        config.setAllowedContentTypes(List.of("image/png"));

        InMemoryProofAssetRepository proofAssetRepository = new InMemoryProofAssetRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        UploadProvider mismatchedProvider = new FakeUploadProvider(config) {
            @Override
            public UploadObjectVerification verifyObject(
                    String bucket,
                    String objectKey,
                    String expectedContentType,
                    long expectedContentLengthBytes,
                    String expectedChecksumSha256) {
                return new UploadObjectVerification(true, expectedContentLengthBytes + 1, expectedContentType, expectedChecksumSha256, "etag", Map.of());
            }
        };
        UploadService service = new UploadService(
                config,
                new StubOrderRepository(),
                proofAssetRepository,
                mismatchedProvider,
                new CurrentAccountAccess(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(AccountRiskGuard.class));

        UploadPresignResponse presigned = service.presign(new UploadPresignRequest(
                "MF260505ORD000001X",
                "proof.png",
                "image/png",
                1024,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                null));

        assertThrows(ResponseStatusException.class, () -> service.complete(
                presigned.assetId(),
                new CompleteUploadRequest(
                        "image/png",
                        1024,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
        assertEquals(ProofAssetStatus.PENDING, proofAssetRepository.findById(presigned.assetId()).orElseThrow().status());
    }

    @Test
    void completeRejectsAccountThatDidNotCreateUploadTicket() {
        R2Config config = new R2Config();
        config.setBucket("proof-bucket");
        config.setProvider("fake");
        config.setUploadBaseUrl("https://uploads.example/proof-bucket");
        config.setPublicBaseUrl("https://public.example/proof-bucket");
        config.setPresignTtl(Duration.ofMinutes(15));
        config.setMaxUploadBytes(5_000_000);
        config.setAllowedContentTypes(List.of("image/png"));

        InMemoryProofAssetRepository proofAssetRepository = new InMemoryProofAssetRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        UploadService service = new UploadService(
                config,
                new StubOrderRepository(),
                proofAssetRepository,
                new FakeUploadProvider(config),
                new CurrentAccountAccess(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(AccountRiskGuard.class));

        UploadPresignResponse presigned = service.presign(new UploadPresignRequest(
                "MF260505ORD000001X",
                "proof.png",
                "image/png",
                1024,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                null));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-other", "@other", "Other"),
                null,
                List.of()));
        assertThrows(ResponseStatusException.class, () -> service.complete(
                presigned.assetId(),
                new CompleteUploadRequest(
                        "image/png",
                        1024,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
    }

    @Test
    void downloadRequiresOrderParticipantOrReviewAuthority() {
        R2Config config = new R2Config();
        config.setBucket("proof-bucket");
        config.setProvider("fake");
        config.setUploadBaseUrl("https://uploads.example/proof-bucket");
        config.setPublicBaseUrl("https://public.example/proof-bucket");
        config.setPresignTtl(Duration.ofMinutes(15));
        config.setMaxUploadBytes(5_000_000);
        config.setAllowedContentTypes(List.of("image/png"));

        InMemoryProofAssetRepository proofAssetRepository = new InMemoryProofAssetRepository();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        UploadService service = new UploadService(
                config,
                new StubOrderRepository(),
                proofAssetRepository,
                new FakeUploadProvider(config),
                new CurrentAccountAccess(),
                new NoopAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                new RateLimitService(),
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(AccountRiskGuard.class));

        UploadPresignResponse presigned = service.presign(new UploadPresignRequest(
                "MF260505ORD000001X",
                "proof.png",
                "image/png",
                1024,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                null));
        service.complete(
                presigned.assetId(),
                new CompleteUploadRequest(
                        "image/png",
                        1024,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));

        assertEquals("GET", service.download(presigned.assetId()).downloadMethod());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-other", "@other", "Other"),
                null,
                List.of()));
        assertThrows(ResponseStatusException.class, () -> service.download(presigned.assetId()));
    }

    private static final class StubOrderRepository implements OrderRepository {
        @Override
        public List<OrderEntity> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public List<OrderEntity> findByParticipantAccountId(String accountId, int limit) {
            return List.of();
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<OrderEntity> findByParticipantAccountId(
                String accountId,
                com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return new com.monopolyfun.shared.pagination.PageResult<>(
                    List.of(),
                    new com.monopolyfun.shared.pagination.PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public List<OrderEntity> findWorkbenchCandidates(String accountId, int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findDisputed(int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findExpiredPaymentLocks(Instant dueAt, int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findExpiredDisputeWindows(Instant dueAt, int limit) {
            return List.of();
        }

        @Override
        public List<SettlementAnomaly> findSettlementAnomalies(int limit) {
            return List.of();
        }

        @Override
        public List<OrderEntity> findByMarketId(String marketId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderEntity> findById(String id) {
            return Optional.of(new OrderEntity(
                    id,
                    "MF260505ORD000001X",
                    "mkt-1",
                    "listing-1",
                    ListingKind.WORK,
                    null,
                    null,
                    null,
                    OrderStatus.CLAIMED,
                    "delivery_result_due",
                    "acct-worker",
                    null,
                    null,
                    null,
                    null,
                    null,
                    SettlementType.SHARES,
                    BigDecimal.valueOf(100),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "nonce-test",
                    false,
                    List.of("artifact verified"),
                    "asset proof",
                    "shares settlement",
                    "none",
                    "none",
                    null,
                    null,
                    "normal",
                    false,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Instant.now(),
                    Instant.now()));
        }

        @Override
        public Optional<OrderEntity> findByOrderNo(String orderNo) {
            return findById("order-1").filter(order -> order.orderNo().equals(orderNo));
        }

        @Override
        public Optional<OrderEntity> findFirstByListingId(String listingId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderEntity> findFirstByParentOrderId(String parentOrderId) {
            return Optional.empty();
        }

        @Override
        public OrderEntity save(OrderEntity order) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubMarketRepository implements MarketRepository {
        @Override
        public List<MarketEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public Optional<MarketEntity> findById(String id) {
            return Optional.of(new MarketEntity(
                    id,
                    "Market",
                    "Summary",
                    "Goal",
                    "acct-lead",
                    "source",
                    "https://example.com",
                    SettlementType.SHARES,
                    1,
                    MarketStatus.ACTIVE,
                    Instant.now(),
                    "occupied",
                    Map.of(),
                    Instant.now(),
                    Instant.now()));
        }

        @Override
        public MarketEntity save(MarketEntity market) {
            return market;
        }
    }

    private static final class InMemoryProofAssetRepository implements ProofAssetRepository {
        private final Map<String, ProofAssetEntity> byId = new HashMap<>();

        @Override
        public Optional<ProofAssetEntity> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ProofAssetEntity> findByOrderIdAndArtifactRef(String orderId, String artifactRef) {
            return byId.values().stream()
                    .filter(asset -> asset.orderId().equals(orderId) && asset.artifactRef().equals(artifactRef))
                    .findFirst();
        }

        @Override
        public List<ProofAssetEntity> findRecent(int limit) {
            return byId.values().stream().limit(limit).toList();
        }

        @Override
        public Map<String, Long> countByStatus() {
            return Map.of();
        }

        @Override
        public List<ProofAssetEntity> findExceptions(int limit) {
            return List.of();
        }

        @Override
        public ProofAssetEntity save(ProofAssetEntity asset) {
            byId.put(asset.id(), asset);
            return asset;
        }
    }

    private static final class NoopAuditEventRecorder implements AuditEventRecorder {
        @Override
        public void record(AuditEvent event) {
        }
    }

    private static final class NoopRiskEventRepository implements RiskEventRepository {
        @Override
        public RiskEventEntity save(RiskEventEntity event) {
            return event;
        }

        @Override
        public List<RiskEventEntity> findRecent(int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findRecentByAccount(String accountId, int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countBySeverity() {
            return Map.of();
        }
    }
}
