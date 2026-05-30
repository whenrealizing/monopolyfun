package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.modules.share.infra.SharesLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectLifecycleService {
    public static final Duration OWNER_INACTIVE_TIMEOUT = Duration.ofDays(3);
    private static final Duration HOLDER_ACTIVE_WINDOW = Duration.ofDays(7);
    private static final String OWNER_LAST_ACTION_AT = "ownerLastActionAt";
    private static final String OWNER_LAST_ACTION_REASON = "ownerLastActionReason";
    private static final String OWNER_HANDOFF_LAST = "ownerHandoffLast";
    private static final String OWNER_CLAIM_LAST = "ownerClaimLast";
    private static final Logger log = LoggerFactory.getLogger(ProjectLifecycleService.class);

    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final SharesLedgerRepository sharesLedgerRepository;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final ProjectRoleRepository projectRoleRepository;

    public ProjectLifecycleService(
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            SharesLedgerRepository sharesLedgerRepository,
            AccountRepository accountRepository,
            OrderRepository orderRepository,
            ProjectRoleRepository projectRoleRepository) {
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.sharesLedgerRepository = sharesLedgerRepository;
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.projectRoleRepository = projectRoleRepository;
    }

    public Map<String, Object> withInitialOwnerAction(Map<String, Object> metadata, Instant now, String reason) {
        LinkedHashMap<String, Object> next = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        next.put(OWNER_LAST_ACTION_AT, now.toString());
        next.put(OWNER_LAST_ACTION_REASON, reason);
        return next;
    }

    public void touchOwnerAction(String projectId, String actorAccountId, String reason) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !project.ownerAccountId().equals(actorAccountId) || project.status() == ProjectStatus.ARCHIVED) {
            return;
        }
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(project.mutableMetadata());
        metadata.put(OWNER_LAST_ACTION_AT, now.toString());
        metadata.put(OWNER_LAST_ACTION_REASON, reason);
        projectRepository.save(project.withStatus(ProjectStatus.ACTIVE, metadata));
    }

    @Scheduled(fixedDelay = 300_000L)
    public void runScheduledOwnerHandoff() {
        handoffInactiveOwners(Instant.now());
    }

    public List<ProjectEntity> handoffInactiveOwners(Instant now) {
        Instant inactiveBefore = now.minus(OWNER_INACTIVE_TIMEOUT);
        // 中文注释：owner 接力是后台批处理入口，候选集由仓储层分页/索引语义收口，服务层保留最终校验。
        return projectRepository.findOwnerHandoffCandidates(inactiveBefore).stream()
                .map(project -> handoffIfInactive(project, now))
                .flatMap(Optional::stream)
                .toList();
    }

    public ProjectEntity claimOwner(String projectId, String actorAccountId, String reason, String plan) {
        AccountEntity claimant = accountRepository.findById(actorAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (project.status() != ProjectStatus.CLAIMABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project owner is not claimable");
        }
        if (project.projectLevel() != ProjectLevel.CHILD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only child project owner can be claimed");
        }
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(project.mutableMetadata());
        metadata.put(OWNER_CLAIM_LAST, Map.of(
                "claimId", "owner-claim-" + UUID.randomUUID(),
                "claimantAccountId", claimant.id(),
                "reason", blankToEmpty(reason),
                "plan", blankToEmpty(plan),
                "claimedAt", now.toString()));
        return transferOwner(project, claimant.id(), "owner_claimed", now, metadata);
    }

    private Optional<ProjectEntity> handoffIfInactive(ProjectEntity project, Instant now) {
        Instant ownerLastActionAt = ownerLastActionAt(project).orElse(project.updatedAt() == null ? project.createdAt() : project.updatedAt());
        if (ownerLastActionAt != null && ownerLastActionAt.plus(OWNER_INACTIVE_TIMEOUT).isAfter(now)) {
            return Optional.empty();
        }
        Optional<String> nextOwner = nextActiveShareHolder(project, now);
        if (nextOwner.isPresent()) {
            return Optional.of(transferOwner(project, nextOwner.get(), "owner_auto_handoff", now, project.mutableMetadata()));
        }
        if (project.status() == ProjectStatus.CLAIMABLE) {
            return Optional.empty();
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(project.mutableMetadata());
        metadata.put(OWNER_HANDOFF_LAST, Map.of(
                "reason", "owner_inactive",
                "previousOwnerAccountId", project.ownerAccountId(),
                "status", "claimable",
                "occurredAt", now.toString()));
        ProjectEntity claimable = project.withStatus(ProjectStatus.CLAIMABLE, metadata);
        projectRepository.save(claimable);
        log.info("Project {} became owner-claimable after inactivity", project.id());
        return Optional.of(claimable);
    }

    private Optional<String> nextActiveShareHolder(ProjectEntity project, Instant now) {
        String marketId = PostItemSupport.marketIdForPost(PostKind.PROJECT, project.id());
        Map<String, HolderStats> holders = new LinkedHashMap<>();
        // shares_ledger 是 owner 接力的主来源，按实际释放 shares 聚合活跃贡献者。
        sharesLedgerRepository.findByMarketId(marketId).stream()
                .filter(entry -> !project.ownerAccountId().equals(entry.accountId()))
                .collect(Collectors.toMap(
                        SharesLedgerEntryEntity::accountId,
                        HolderStats::new,
                        HolderStats::merge,
                        LinkedHashMap::new))
                .forEach((accountId, stats) -> holders.merge(accountId, stats, HolderStats::merge));
        // 历史数据或失败重试可能缺少 ledger 行，已验收的 shares order 可以作为接力候选的兜底证据。
        orderRepository.findByMarketId(marketId).stream()
                .filter(order -> order.postKind() == PostKind.PROJECT)
                .filter(order -> project.id().equals(order.postId()))
                .filter(order -> order.status() == OrderStatus.FINAL_ACCEPTED)
                .filter(order -> order.settlementType() == SettlementType.SHARES)
                .map(HolderStats::fromOrder)
                .flatMap(Optional::stream)
                .filter(stats -> !project.ownerAccountId().equals(stats.accountId()))
                .forEach(stats -> holders.merge(stats.accountId(), stats, HolderStats::merge));
        Instant activeAfter = now.minus(HOLDER_ACTIVE_WINDOW);
        return holders.values().stream()
                // 中文注释：owner 接力只认项目内真实贡献时间，避免账号资料更新把非贡献活跃误判为项目活跃。
                .filter(stats -> accountRepository.findById(stats.accountId()).isPresent())
                .filter(stats -> isAfterOrEqual(stats.lastShareAt(), activeAfter))
                .sorted(Comparator.comparingInt(HolderStats::amount).reversed().thenComparing(HolderStats::lastShareAt, Comparator.reverseOrder()))
                .map(HolderStats::accountId)
                .findFirst();
    }

    private boolean isAfterOrEqual(Instant value, Instant floor) {
        return value != null && !value.isBefore(floor);
    }

    private ProjectEntity transferOwner(
            ProjectEntity project,
            String nextOwnerAccountId,
            String reason,
            Instant now,
            Map<String, Object> baseMetadata) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(baseMetadata == null ? Map.of() : baseMetadata);
        metadata.put(OWNER_LAST_ACTION_AT, now.toString());
        metadata.put(OWNER_LAST_ACTION_REASON, reason);
        metadata.put(OWNER_HANDOFF_LAST, Map.of(
                "reason", reason,
                "previousOwnerAccountId", project.ownerAccountId(),
                "nextOwnerAccountId", nextOwnerAccountId,
                "occurredAt", now.toString()));
        ProjectEntity nextProject = project.withOwner(nextOwnerAccountId, ProjectStatus.ACTIVE, metadata);
        projectRepository.save(nextProject);
        if (rootProject(project)) {
            // 中文注释：Root Project owner 接力同步改写协议维护席位，保持公开 owner 与维护席位一致。
            projectRoleRepository.assignRole(project.id(), ProjectRoleCode.SYSTEM_CEO, nextOwnerAccountId, project.ownerAccountId());
        }
        marketRepository.findById(PostItemSupport.marketIdForPost(PostKind.PROJECT, project.id()))
                .map(market -> market.withLeadAccountId(nextOwnerAccountId, now))
                .ifPresent(marketRepository::save);
        log.info("Project {} owner moved from {} to {}", project.id(), project.ownerAccountId(), nextOwnerAccountId);
        return nextProject;
    }

    private boolean rootProject(ProjectEntity project) {
        return RootProjectService.ROOT_PROJECT_ID.equals(project.id())
                || RootProjectService.ROOT_PROJECT_NO.equals(project.projectNo());
    }

    private Optional<Instant> ownerLastActionAt(ProjectEntity project) {
        Object value = project.metadata() == null ? null : project.metadata().get(OWNER_LAST_ACTION_AT);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Instant.parse(String.valueOf(value)));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record HolderStats(String accountId, int amount, Instant lastShareAt) {
        HolderStats(SharesLedgerEntryEntity entry) {
            this(entry.accountId(), entry.amount(), entry.createdAt());
        }

        static Optional<HolderStats> fromOrder(OrderEntity order) {
            String accountId = order.fulfillerAccountId();
            if (accountId == null) {
                return Optional.empty();
            }
            int amount = order.settlementAmount() == null ? 0 : order.settlementAmount().intValueExact();
            Instant occurredAt = order.updatedAt() == null ? order.createdAt() : order.updatedAt();
            return Optional.of(new HolderStats(accountId, amount, occurredAt));
        }

        HolderStats merge(HolderStats other) {
            Instant latest = lastShareAt == null || (other.lastShareAt != null && other.lastShareAt.isAfter(lastShareAt))
                    ? other.lastShareAt
                    : lastShareAt;
            return new HolderStats(accountId, amount + other.amount, latest);
        }
    }
}
