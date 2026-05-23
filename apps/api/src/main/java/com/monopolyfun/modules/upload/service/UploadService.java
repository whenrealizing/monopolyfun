package com.monopolyfun.modules.upload.service;

import com.monopolyfun.config.R2Config;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.api.request.PaymentActionRequest;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.risk.service.RiskAction;
import com.monopolyfun.modules.upload.api.request.CompleteUploadRequest;
import com.monopolyfun.modules.upload.api.request.UploadPresignRequest;
import com.monopolyfun.modules.upload.api.response.UploadCompletionResponse;
import com.monopolyfun.modules.upload.api.response.UploadDownloadResponse;
import com.monopolyfun.modules.upload.api.response.UploadPresignResponse;
import com.monopolyfun.modules.upload.domain.ProofAssetEntity;
import com.monopolyfun.modules.upload.domain.ProofAssetStatus;
import com.monopolyfun.modules.upload.infra.ProofAssetRepository;
import com.monopolyfun.modules.upload.service.provider.UploadProvider;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class UploadService {
    private static final String VISIBILITY_PARTICIPANTS = "participants";
    private static final String VISIBILITY_REVIEWER_ONLY = "reviewer_only";
    private static final String VISIBILITY_PRIVATE = "private";
    private static final java.util.Set<String> ALLOWED_PURPOSES = java.util.Set.of("proof", "delivery", "dispute", "review", "progress");
    private static final java.util.Set<String> ALLOWED_VISIBILITIES = java.util.Set.of(VISIBILITY_PARTICIPANTS, VISIBILITY_REVIEWER_ONLY, VISIBILITY_PRIVATE);

    private final R2Config r2Config;
    private final OrderRepository orderRepository;
    private final ProofAssetRepository proofAssetRepository;
    private final UploadProvider uploadProvider;
    private final CurrentAccountAccess currentAccountAccess;
    private final AuditEventRecorder auditEventRecorder;
    private final TraceContextHolder traceContextHolder;
    private final RiskEventService riskEventService;
    private final RateLimitService rateLimitService;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final AccountRiskGuard accountRiskGuard;

    public UploadService(
            R2Config r2Config,
            OrderRepository orderRepository,
            ProofAssetRepository proofAssetRepository,
            UploadProvider uploadProvider,
            CurrentAccountAccess currentAccountAccess,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder,
            RiskEventService riskEventService,
            RateLimitService rateLimitService,
            OrganizationAuthorityService organizationAuthorityService,
            AccountRiskGuard accountRiskGuard) {
        this.r2Config = r2Config;
        this.orderRepository = orderRepository;
        this.proofAssetRepository = proofAssetRepository;
        this.uploadProvider = uploadProvider;
        this.currentAccountAccess = currentAccountAccess;
        this.auditEventRecorder = auditEventRecorder;
        this.traceContextHolder = traceContextHolder;
        this.riskEventService = riskEventService;
        this.rateLimitService = rateLimitService;
        this.organizationAuthorityService = organizationAuthorityService;
        this.accountRiskGuard = accountRiskGuard;
    }

    public UploadPresignResponse presign(UploadPresignRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        accountRiskGuard.requireAllowed(actorAccountId, RiskAction.UPLOAD_PRESIGN);
        enforcePresignRateLimit(actorAccountId);
        var order = orderRepository.findByOrderNo(request.orderId())
                .or(() -> orderRepository.findById(request.orderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        requireUploadParticipant(order);
        validatePresignRequest(request.contentType(), request.contentLengthBytes(), request.checksumSha256());
        String purpose = normalizePurpose(request.purpose());
        String visibility = normalizeVisibility(request.visibility());
        Instant now = Instant.now();
        String monthPath = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC).format(now);
        String safeFilename = request.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = "proofs/%s/%s/%s-%s".formatted(order.orderNo(), monthPath, UUID.randomUUID(), safeFilename);
        String assetId = "asset-" + UUID.randomUUID();
        String artifactRef = "asset://" + assetId;
        UploadProvider.PresignedUpload presignedUpload = uploadProvider.presign(
                requireConfiguredValue(r2Config.getBucket(), "monopolyfun.uploads.bucket"),
                objectKey,
                request.contentType(),
                request.checksumSha256(),
                r2Config.getPresignTtl());
        proofAssetRepository.save(new ProofAssetEntity(
                assetId,
                order.id(),
                artifactRef,
                objectKey,
                safeFilename,
                request.contentType(),
                request.contentLengthBytes(),
                request.checksumSha256().toLowerCase(),
                presignedUpload.providerName(),
                requireConfiguredValue(r2Config.getBucket(), "monopolyfun.uploads.bucket"),
                ProofAssetStatus.PENDING,
                actorAccountId,
                purpose,
                visibility,
                Map.of("roleAtUpload", order.roleFor(actorAccountId) == null ? "authority" : order.roleFor(actorAccountId)),
                now,
                now));
        recordAudit("upload_presign", order.id(), assetId, Map.of("artifactRef", artifactRef, "contentType", request.contentType(), "purpose", purpose, "visibility", visibility));
        return new UploadPresignResponse(
                assetId,
                artifactRef,
                objectKey,
                presignedUpload.uploadMethod(),
                presignedUpload.uploadUrl(),
                presignedUpload.uploadHeaders(),
                now.plus(r2Config.getPresignTtl()));
    }

    public UploadCompletionResponse complete(String assetId, CompleteUploadRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        ProofAssetEntity asset = proofAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload asset not found"));
        requireCompleteActor(asset, actorAccountId);
        validateCompletionRequest(asset, request);
        UploadProvider.UploadObjectVerification verification = uploadProvider.verifyObject(
                asset.bucket(),
                asset.objectKey(),
                asset.contentType(),
                asset.contentLengthBytes(),
                asset.checksumSha256());
        validateRemoteObject(asset, verification);
        ProofAssetEntity uploaded = asset.markUploaded(Instant.now());
        ProofAssetEntity inspected = inspectUploadedAsset(uploaded);
        proofAssetRepository.save(inspected);
        recordAudit("upload_complete", inspected.orderId(), inspected.id(), Map.of("artifactRef", inspected.artifactRef(), "status", inspected.status()));
        return new UploadCompletionResponse(
                inspected.id(),
                inspected.artifactRef(),
                inspected.objectKey(),
                inspected.status(),
                inspected.updatedAt());
    }

    public UploadDownloadResponse download(String assetId) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        ProofAssetEntity asset = proofAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload asset not found"));
        var order = orderRepository.findByOrderNo(asset.orderId())
                .or(() -> orderRepository.findById(asset.orderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        requireDownloadActor(asset, order, actorAccountId);
        Instant expiresAt = Instant.now().plus(r2Config.getDownloadPresignTtl());
        UploadProvider.PresignedDownload signed = uploadProvider.presignDownload(
                asset.bucket(),
                asset.objectKey(),
                asset.filename(),
                r2Config.getDownloadPresignTtl());
        recordAudit("upload_download_presigned", asset.orderId(), asset.id(), Map.of("artifactRef", asset.artifactRef(), "visibility", asset.visibility()));
        return new UploadDownloadResponse(
                asset.id(),
                asset.artifactRef(),
                asset.filename(),
                asset.contentType(),
                asset.contentLengthBytes(),
                asset.checksumSha256(),
                asset.status(),
                signed.downloadMethod(),
                signed.downloadUrl(),
                signed.downloadHeaders(),
                expiresAt);
    }

    public ProofAssetView verify(String assetId, PaymentActionRequest request) {
        return updateAssetStatus(assetId, request, ProofAssetStatus.VERIFIED, "upload_verified");
    }

    public ProofAssetView quarantine(String assetId, PaymentActionRequest request) {
        return updateAssetStatus(assetId, request, ProofAssetStatus.QUARANTINED, "upload_quarantined");
    }

    public ProofAssetView cancel(String assetId, PaymentActionRequest request) {
        return updateAssetStatus(assetId, request, ProofAssetStatus.CANCELLED, "upload_cancelled");
    }

    private void validatePresignRequest(String contentType, long contentLengthBytes, String checksumSha256) {
        if (!r2Config.getAllowedContentTypes().contains(contentType)) {
            riskEventService.record("upload_content_type_rejected", "upload", contentType, currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse("anonymous"), "medium", "Unsupported upload content type", Map.of("contentType", contentType));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported upload content type");
        }
        if (contentLengthBytes > r2Config.getMaxUploadBytes()) {
            riskEventService.record("upload_size_rejected", "upload", null, currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse("anonymous"), "medium", "Upload exceeds max size", Map.of("contentLengthBytes", contentLengthBytes));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload exceeds max size");
        }
        if (!checksumSha256.matches("^[a-fA-F0-9]{64}$")) {
            riskEventService.record("upload_checksum_rejected", "upload", null, currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse("anonymous"), "medium", "Upload checksum invalid", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checksumSha256 must be 64 hex chars");
        }
    }

    private void validateCompletionRequest(ProofAssetEntity asset, CompleteUploadRequest request) {
        validatePresignRequest(request.contentType(), request.contentLengthBytes(), request.checksumSha256());
        if (asset.status() != ProofAssetStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload asset is not pending");
        }
        if (!asset.contentType().equals(request.contentType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload content type mismatch");
        }
        if (asset.contentLengthBytes() != request.contentLengthBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload content length mismatch");
        }
        if (!asset.checksumSha256().equalsIgnoreCase(request.checksumSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload checksum mismatch");
        }
    }

    private void validateRemoteObject(ProofAssetEntity asset, UploadProvider.UploadObjectVerification verification) {
        // 中文注释：客户端 complete 请求只作为意图，远端对象 HEAD 元数据才是进入订单证明链的事实来源。
        if (verification == null || !verification.exists()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Uploaded object does not exist");
        }
        if (verification.contentLengthBytes() == null || verification.contentLengthBytes() != asset.contentLengthBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Uploaded object content length mismatch");
        }
        if (verification.contentType() != null && !verification.contentType().equals(asset.contentType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Uploaded object content type mismatch");
        }
        if (verification.checksumSha256() != null && !verification.checksumSha256().equalsIgnoreCase(asset.checksumSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Uploaded object checksum mismatch");
        }
    }

    private String requireConfiguredValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, propertyName + " is not configured");
        }
        return value;
    }

    private void recordAudit(String type, String orderId, String assetId, Map<String, Object> payload) {
        auditEventRecorder.record(new AuditEvent(
                "audit-" + UUID.randomUUID(),
                type,
                "upload_asset",
                assetId,
                currentAccountAccess.requireAccountId(),
                traceContextHolder.currentTraceId().orElse("trace-upload"),
                "success",
                Map.copyOf(payload),
                Instant.now()));
    }

    private ProofAssetView updateAssetStatus(String assetId, PaymentActionRequest request, ProofAssetStatus nextStatus, String auditType) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        ProofAssetEntity asset = proofAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload asset not found"));
        var order = orderRepository.findByOrderNo(asset.orderId())
                .or(() -> orderRepository.findById(asset.orderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        // 中文注释：人工改写资产状态只接受统一权限服务的上传验收能力，权限事实来自项目职位。
        if (!organizationAuthorityService.canReviewUpload(request.actorAccountId(), order)) {
            riskEventService.record("upload_status_transition_forbidden", "upload_asset", asset.id(), request.actorAccountId(), "high", "Upload review capability required", Map.of("nextStatus", nextStatus.name()));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upload review capability required");
        }
        ProofAssetEntity updated = new ProofAssetEntity(
                asset.id(),
                asset.orderId(),
                asset.artifactRef(),
                asset.objectKey(),
                asset.filename(),
                asset.contentType(),
                asset.contentLengthBytes(),
                asset.checksumSha256(),
                asset.storageProvider(),
                asset.bucket(),
                nextStatus,
                asset.uploadedByAccountId(),
                asset.purpose(),
                asset.visibility(),
                mergeMetadata(asset.metadata(), Map.of("reason", request.reason() == null ? "" : request.reason())),
                asset.createdAt(),
                Instant.now());
        proofAssetRepository.save(updated);
        recordAudit(auditType, updated.orderId(), updated.id(), Map.of("status", nextStatus.name(), "reason", request.reason() == null ? "" : request.reason()));
        return com.monopolyfun.modules.upload.service.mapper.UploadViewMapper.proofAsset(updated);
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> existing, Map<String, Object> extra) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.putAll(existing);
        merged.putAll(extra);
        return Map.copyOf(merged);
    }

    private ProofAssetEntity inspectUploadedAsset(ProofAssetEntity uploaded) {
        String reason = inspectionFailureReason(uploaded);
        Instant now = Instant.now();
        if (reason == null) {
            ProofAssetEntity verified = uploaded.withStatus(
                    ProofAssetStatus.VERIFIED,
                    mergeMetadata(uploaded.metadata(), Map.of("inspectionMode", "auto", "inspectionResult", "verified")),
                    now);
            recordAudit("upload_verified_auto", verified.orderId(), verified.id(), Map.of("artifactRef", verified.artifactRef(), "status", verified.status()));
            return verified;
        }
        ProofAssetEntity quarantined = uploaded.withStatus(
                ProofAssetStatus.QUARANTINED,
                mergeMetadata(uploaded.metadata(), Map.of("inspectionMode", "auto", "inspectionResult", "quarantined", "inspectionReason", reason)),
                now);
        recordAudit("upload_quarantined_auto", quarantined.orderId(), quarantined.id(), Map.of("artifactRef", quarantined.artifactRef(), "status", quarantined.status(), "reason", reason));
        riskEventService.record(
                "upload_auto_quarantine",
                "upload_asset",
                quarantined.id(),
                currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse("anonymous"),
                "high",
                reason,
                Map.of("contentType", quarantined.contentType(), "filename", quarantined.filename()));
        return quarantined;
    }

    private void requireUploadParticipant(com.monopolyfun.modules.order.domain.OrderEntity order) {
        String accountId = currentAccountAccess.requireAccountId();
        boolean orderParticipant = order.hasParticipant(accountId);
        boolean uploadReviewer = organizationAuthorityService.canReviewUpload(accountId, order);
        if (!orderParticipant && !uploadReviewer) {
            riskEventService.record("upload_order_access_denied", "order", order.id(), accountId, "high", "Upload requires order participant or upload review capability", Map.of("marketId", order.marketId()));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upload requires order participant or upload review capability");
        }
    }

    private void requireCompleteActor(ProofAssetEntity asset, String actorAccountId) {
        var order = orderRepository.findByOrderNo(asset.orderId())
                .or(() -> orderRepository.findById(asset.orderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        boolean uploader = actorAccountId.equals(asset.uploadedByAccountId());
        boolean uploadReviewer = organizationAuthorityService.canReviewUpload(actorAccountId, order);
        // 中文注释：complete 是把对象纳入订单证据链的落点，只接受原上传者或上传审核者收口。
        if (!uploader && !uploadReviewer) {
            riskEventService.record("upload_complete_forbidden", "upload_asset", asset.id(), actorAccountId, "high", "Upload completion requires original uploader or upload review capability", Map.of("orderId", asset.orderId()));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upload completion requires original uploader or upload review capability");
        }
    }

    private void requireDownloadActor(ProofAssetEntity asset, com.monopolyfun.modules.order.domain.OrderEntity order, String actorAccountId) {
        if (asset.status() == ProofAssetStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload asset is not available for download");
        }
        boolean uploader = actorAccountId.equals(asset.uploadedByAccountId());
        boolean participant = order.hasParticipant(actorAccountId);
        boolean reviewer = organizationAuthorityService.canReviewOrder(actorAccountId, order);
        boolean disputeResolver = organizationAuthorityService.canResolveOrderDispute(actorAccountId, order);
        boolean uploadReviewer = organizationAuthorityService.canReviewUpload(actorAccountId, order);
        boolean allowed = switch (asset.visibility()) {
            case VISIBILITY_PRIVATE -> uploader || uploadReviewer;
            case VISIBILITY_REVIEWER_ONLY ->
                    uploader || reviewer || disputeResolver || uploadReviewer || actorAccountId.equals(order.reviewerAccountId());
            default -> uploader || participant || reviewer || disputeResolver || uploadReviewer;
        };
        if (!allowed) {
            riskEventService.record("upload_download_forbidden", "upload_asset", asset.id(), actorAccountId, "high", "Upload download capability required", Map.of("visibility", asset.visibility()));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upload download capability required");
        }
        if ((asset.status() == ProofAssetStatus.QUARANTINED || asset.status() == ProofAssetStatus.CANCELLED || asset.status() == ProofAssetStatus.FAILED)
                && !uploadReviewer && !disputeResolver) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload asset is not available for participant download");
        }
    }

    private String normalizePurpose(String value) {
        if (value == null || value.isBlank()) {
            return "proof";
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_PURPOSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported upload purpose");
        }
        return normalized;
    }

    private String normalizeVisibility(String value) {
        if (value == null || value.isBlank()) {
            return VISIBILITY_PARTICIPANTS;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_VISIBILITIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported upload visibility");
        }
        return normalized;
    }

    private String inspectionFailureReason(ProofAssetEntity asset) {
        String filename = asset.filename().toLowerCase(java.util.Locale.ROOT);
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (filename.endsWith(".exe") || filename.endsWith(".js") || filename.endsWith(".sh") || filename.endsWith(".html")) {
            return "Executable or script-like extension is not allowed";
        }
        return switch (asset.contentType()) {
            case "image/png" -> extension.equals("png") ? null : "PNG upload extension mismatch";
            case "image/jpeg" ->
                    (extension.equals("jpg") || extension.equals("jpeg")) ? null : "JPEG upload extension mismatch";
            case "image/webp" -> extension.equals("webp") ? null : "WEBP upload extension mismatch";
            case "application/pdf" -> extension.equals("pdf") ? null : "PDF upload extension mismatch";
            default -> "Unsupported upload content type";
        };
    }

    private void enforcePresignRateLimit(String actorAccountId) {
        if (rateLimitService.isAllowed("upload_presign", actorAccountId, 20, java.time.Duration.ofMinutes(10))) {
            return;
        }
        riskEventService.record("upload_presign_rate_limited", "account", actorAccountId, actorAccountId, "high", "Too many upload presign requests", Map.of("limit", 20, "windowSeconds", 600));
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many upload presign requests");
    }
}
