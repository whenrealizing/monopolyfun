package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.workthread.api.request.ClaimDistributionRequest;
import com.monopolyfun.modules.workthread.api.request.CreateDistributionBatchRequest;
import com.monopolyfun.modules.workthread.api.request.UpsertProjectRevenueAddressRequest;
import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.DistributionEntitlementEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import com.monopolyfun.modules.workthread.service.view.ContributionRewardView;
import com.monopolyfun.modules.workthread.service.view.DistributionBatchView;
import com.monopolyfun.modules.workthread.service.view.DistributionClaimView;
import com.monopolyfun.modules.workthread.service.view.ProjectRevenueAddressView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RevenueDistributionService {
    private static final String CLAIM_TOKEN = "BNB";

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

    public DistributionBatchView createBatch(ProjectEntity project, CreateDistributionBatchRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        requireProjectOwner(project, request.actorAccountId());
        requirePeriod(request.period());
        if (repository.findDistributionBatch(project.id(), request.period().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution period already exists");
        }
        List<AccountShareSnapshot> snapshots = accountShareSnapshots(project.id());
        int totalShares = snapshots.stream().mapToInt(AccountShareSnapshot::shares).sum();
        if (totalShares <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settled shares required before distribution");
        }
        Instant now = repository.now();
        String batchId = "db-" + UUID.randomUUID();
        // 中文注释：批次创建时冻结每个账号的 shares 和金额，后续新增贡献只能参与之后周期。
        List<DistributionEntitlementEntity> entitlements = snapshots.stream()
                .map(snapshot -> new DistributionEntitlementEntity(
                        "de-" + UUID.randomUUID(),
                        batchId,
                        snapshot.accountId(),
                        snapshot.shares(),
                        claimable(request.totalRevenueMinor(), snapshot.shares(), totalShares),
                        "claimable",
                        now))
                .toList();
        DistributionBatchEntity batch = repository.saveDistributionBatch(new DistributionBatchEntity(
                batchId,
                project.id(),
                request.period().trim(),
                request.totalRevenueMinor(),
                totalShares,
                root(project.id(), request.period(), request.totalRevenueMinor(), entitlements),
                "published",
                now,
                now));
        repository.saveDistributionEntitlements(entitlements);
        return new DistributionBatchView(
                batch.id(),
                batch.projectId(),
                batch.period(),
                batch.totalRevenueMinor(),
                batch.totalSnapshotShares(),
                batch.merkleRoot(),
                0,
                CLAIM_TOKEN,
                batch.status(),
                batch.createdAt().toString(),
                batch.updatedAt().toString());
    }

    public ContributionRewardView rewards(ProjectEntity project, String accountId) {
        String currentPeriod = YearMonth.now(ZoneOffset.UTC).toString();
        int shares = repository.sumSharesByProjectAndAccount(project.id(), accountId);
        int bounty = repository.sumBountyByProjectAndAccount(project.id(), accountId);
        int claimable = repository.findDistributionBatch(project.id(), currentPeriod)
                .flatMap(batch -> repository.findDistributionEntitlement(batch.id(), accountId))
                .map(entitlement -> "claimable".equals(entitlement.status()) && !repository.hasDistributionClaim(entitlement.batchId(), accountId) ? entitlement.amountMinor() : 0)
                .orElse(0);
        return new ContributionRewardView(shares, bounty, CLAIM_TOKEN, claimable, CLAIM_TOKEN);
    }

    public DistributionClaimView claim(ProjectEntity project, String period, ClaimDistributionRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        requirePeriod(period);
        DistributionBatchEntity batch = repository.findDistributionBatch(project.id(), period)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution batch not found"));
        DistributionEntitlementEntity entitlement = repository.findDistributionEntitlement(batch.id(), request.actorAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Distribution entitlement not found"));
        if (!"claimable".equals(entitlement.status()) || entitlement.amountMinor() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution entitlement is not claimable");
        }
        int amount = entitlement.amountMinor();
        Instant now = repository.now();
        DistributionClaimEntity existing = repository.findDistributionClaim(batch.id(), request.actorAccountId()).orElse(null);
        if (existing != null) {
            String requestedWallet = request.walletAddress() == null ? "" : request.walletAddress().trim();
            // 中文注释：用户回填 txHash 时可省略钱包地址，系统沿用首次 claim 固定的钱包，降低领取摩擦。
            if (!requestedWallet.isBlank() && !existing.walletAddress().equals(requestedWallet)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution claim wallet already exists");
            }
            DistributionClaimEntity updated = submitClaimTxIfPresent(existing, request.txHash(), request.txConfirmed(), now);
            return new DistributionClaimView(batch.id(), batch.projectId(), batch.period(), updated.accountId(), updated.walletAddress(), updated.amountMinor(), CLAIM_TOKEN, updated.proof(), updated.txHash(), updated.status());
        }
        requireWalletAddress(request.walletAddress(), "walletAddress");
        List<String> proof = proof(batch, request.actorAccountId(), request.walletAddress(), amount);
        String normalizedTxHash = request.txHash() == null || request.txHash().isBlank() ? null : request.txHash().trim();
        String status = normalizedTxHash == null ? "claimable" : Boolean.TRUE.equals(request.txConfirmed()) ? "claimed" : "submitted";
        // 中文注释：首次 claim 冻结钱包、金额和 proof，后续请求只能补充同一 claim 的链上 txHash。
        DistributionClaimEntity saved = repository.saveDistributionClaim(new DistributionClaimEntity(
                "dc-" + UUID.randomUUID(),
                batch.id(),
                request.actorAccountId(),
                request.walletAddress().trim(),
                amount,
                proof,
                normalizedTxHash,
                status,
                now,
                now));
        return new DistributionClaimView(batch.id(), batch.projectId(), batch.period(), saved.accountId(), saved.walletAddress(), saved.amountMinor(), CLAIM_TOKEN, saved.proof(), saved.txHash(), saved.status());
    }

    private DistributionClaimEntity submitClaimTxIfPresent(DistributionClaimEntity existing, String txHash, Boolean txConfirmed, Instant now) {
        boolean confirmed = Boolean.TRUE.equals(txConfirmed);
        String normalizedTxHash = txHash == null || txHash.isBlank() ? existing.txHash() : txHash.trim();
        if ((normalizedTxHash == null || normalizedTxHash.isBlank()) && !confirmed) {
            return existing;
        }
        if (normalizedTxHash == null || normalizedTxHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Distribution claim txHash is required before confirmation");
        }
        if (existing.txHash() != null && !existing.txHash().isBlank()) {
            if (existing.txHash().equals(normalizedTxHash) && !confirmed) {
                return existing;
            }
            if (existing.txHash().equals(normalizedTxHash) && "claimed".equals(existing.status())) {
                return existing;
            }
            if (existing.txHash().equals(normalizedTxHash)) {
                // 中文注释：链上 receipt 和事件校验通过后，系统把已提交 txHash 推进为已领取状态。
                return repository.confirmDistributionClaimTx(existing.batchId(), existing.accountId(), normalizedTxHash, now);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution claim txHash already exists");
        }
        if (confirmed) {
            // 中文注释：允许系统在同一次回填中提交 txHash 并写入链上已确认状态。
            return repository.confirmDistributionClaimTx(existing.batchId(), existing.accountId(), normalizedTxHash, now);
        }
        // 中文注释：已创建的 claim 固定收款地址、金额和 proof，txHash 只推进链上提交状态。
        return repository.submitDistributionClaimTx(existing.batchId(), existing.accountId(), normalizedTxHash, now);
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

    private int claimable(int totalRevenueMinor, int memberShares, int totalShares) {
        if (totalRevenueMinor <= 0 || totalShares <= 0 || memberShares <= 0) {
            return 0;
        }
        return (int) Math.floor((double) totalRevenueMinor * memberShares / totalShares);
    }

    private List<AccountShareSnapshot> accountShareSnapshots(String projectId) {
        Map<String, Integer> sharesByAccount = repository.listContributionsByProject(projectId).stream()
                .filter(contribution -> "settled".equals(contribution.status()))
                .collect(Collectors.toMap(
                        ContributionEntryEntity::accountId,
                        ContributionEntryEntity::shares,
                        Integer::sum,
                        LinkedHashMap::new));
        return sharesByAccount.entrySet().stream()
                .map(entry -> new AccountShareSnapshot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AccountShareSnapshot::accountId))
                .toList();
    }

    private static String root(String projectId, String period, int revenue, List<DistributionEntitlementEntity> entitlements) {
        String entitlementPayload = entitlements.stream()
                .sorted(Comparator.comparing(DistributionEntitlementEntity::accountId))
                .map(entitlement -> entitlement.accountId() + ":" + entitlement.snapshotShares() + ":" + entitlement.amountMinor())
                .collect(Collectors.joining(","));
        return "sha256:" + sha256(projectId + "|" + period + "|" + revenue + "|" + entitlementPayload);
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

    private record AccountShareSnapshot(String accountId, int shares) {
    }
}
