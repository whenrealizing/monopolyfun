package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.workthread.api.request.ClaimDistributionRequest;
import com.monopolyfun.modules.workthread.api.request.CreateDistributionBatchRequest;
import com.monopolyfun.modules.workthread.api.request.UpsertProjectRevenueAddressRequest;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import com.monopolyfun.modules.workthread.service.view.ContributionRewardView;
import com.monopolyfun.modules.workthread.service.view.DistributionClaimView;
import com.monopolyfun.modules.workthread.service.view.ProjectRevenueAddressView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RevenueDistributionService {
    private static final String CLAIM_TOKEN = "USDC";

    private final WorkThreadRepository repository;
    private final CurrentAccountAccess currentAccountAccess;

    public RevenueDistributionService(WorkThreadRepository repository, CurrentAccountAccess currentAccountAccess) {
        this.repository = repository;
        this.currentAccountAccess = currentAccountAccess;
    }

    public ProjectRevenueAddressView upsertAddress(ProjectEntity project, UpsertProjectRevenueAddressRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        requireProjectOwner(project, request.actorAccountId());
        requireWalletAddress(request.contractAddress(), "contractAddress");
        requireWalletAddress(request.tokenAddress(), "tokenAddress");
        Instant now = repository.now();
        ProjectRevenueAddressEntity saved = repository.saveRevenueAddress(new ProjectRevenueAddressEntity(
                "pra-" + UUID.randomUUID(),
                project.id(),
                request.chainId().trim(),
                request.contractAddress().trim(),
                request.tokenAddress().trim(),
                "active",
                now,
                now));
        return new ProjectRevenueAddressView(saved.id(), saved.projectId(), saved.chainId(), saved.contractAddress(), saved.tokenAddress(), saved.status());
    }

    public DistributionBatchEntity createBatch(ProjectEntity project, CreateDistributionBatchRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        requireProjectOwner(project, request.actorAccountId());
        requirePeriod(request.period());
        if (repository.findDistributionBatch(project.id(), request.period().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution period already exists");
        }
        int totalShares = repository.sumSharesByProject(project.id());
        if (totalShares <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settled shares required before distribution");
        }
        Instant now = repository.now();
        // 中文注释：distribution batch 冻结本周期收入和总 shares，claim 只按该快照计算，避免事后贡献追溯瓜分历史收入。
        return repository.saveDistributionBatch(new DistributionBatchEntity(
                "db-" + UUID.randomUUID(),
                project.id(),
                request.period().trim(),
                request.totalRevenueMinor(),
                totalShares,
                root(project.id(), request.period(), request.totalRevenueMinor(), totalShares),
                "published",
                now,
                now));
    }

    public ContributionRewardView rewards(ProjectEntity project, String accountId) {
        String currentPeriod = YearMonth.now(ZoneOffset.UTC).toString();
        int shares = repository.sumSharesByProjectAndAccount(project.id(), accountId);
        int bounty = repository.sumBountyByProjectAndAccount(project.id(), accountId);
        int claimable = repository.findDistributionBatch(project.id(), currentPeriod)
                .map(batch -> claimable(batch, shares))
                .orElse(0);
        return new ContributionRewardView(shares, bounty, CLAIM_TOKEN, claimable, CLAIM_TOKEN);
    }

    public DistributionClaimView claim(ProjectEntity project, String period, ClaimDistributionRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        requirePeriod(period);
        requireWalletAddress(request.walletAddress(), "walletAddress");
        DistributionBatchEntity batch = repository.findDistributionBatch(project.id(), period)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution batch not found"));
        int memberShares = repository.sumSharesByProjectAndAccount(project.id(), request.actorAccountId());
        int amount = claimable(batch, memberShares);
        List<String> proof = proof(batch, request.actorAccountId(), request.walletAddress(), amount);
        Instant now = repository.now();
        DistributionClaimEntity existing = repository.findDistributionClaim(batch.id(), request.actorAccountId()).orElse(null);
        DistributionClaimEntity saved = repository.saveDistributionClaim(new DistributionClaimEntity(
                existing == null ? "dc-" + UUID.randomUUID() : existing.id(),
                batch.id(),
                request.actorAccountId(),
                request.walletAddress().trim(),
                amount,
                proof,
                request.txHash(),
                request.txHash() == null || request.txHash().isBlank() ? "claimable" : "submitted",
                existing == null ? now : existing.createdAt(),
                now));
        return new DistributionClaimView(batch.id(), batch.projectId(), batch.period(), saved.accountId(), saved.walletAddress(), saved.amountMinor(), CLAIM_TOKEN, saved.proof(), saved.txHash(), saved.status());
    }

    private void requireProjectOwner(ProjectEntity project, String actorAccountId) {
        if (!project.ownerAccountId().equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner required");
        }
    }

    private void requirePeriod(String period) {
        if (period == null || !period.trim().matches("\\d{4}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must use YYYY-MM");
        }
    }

    private void requireWalletAddress(String walletAddress, String field) {
        if (walletAddress == null || !walletAddress.trim().matches("0x[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be an EVM address");
        }
    }

    private int claimable(DistributionBatchEntity batch, int memberShares) {
        if (batch.totalRevenueMinor() <= 0 || batch.totalSnapshotShares() <= 0 || memberShares <= 0) {
            return 0;
        }
        return (int) Math.floor((double) batch.totalRevenueMinor() * memberShares / batch.totalSnapshotShares());
    }

    private static String root(String projectId, String period, int revenue, int shares) {
        return "sha256:" + sha256(projectId + "|" + period + "|" + revenue + "|" + shares);
    }

    private static List<String> proof(DistributionBatchEntity batch, String accountId, String wallet, int amount) {
        String leaf = sha256(batch.projectId() + "|" + batch.period() + "|" + accountId + "|" + wallet + "|" + amount + "|" + batch.merkleRoot());
        return List.of("leaf:" + leaf, "root:" + batch.merkleRoot());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate claim proof", exception);
        }
    }
}
