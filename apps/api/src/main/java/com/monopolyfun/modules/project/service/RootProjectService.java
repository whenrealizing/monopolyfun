package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class RootProjectService {
    public static final String ROOT_PROJECT_ID = "project-root";
    public static final String ROOT_PROJECT_NO = "monopolyfun";
    public static final String ROOT_FALLBACK_ACCOUNT_ID = "acct-root-system";
    private static final String ROOT_PROJECT_TITLE = "MonopolyFun";
    private static final String ROOT_PROJECT_SUMMARY = "在 MonopolyFun，可以自由发布项目、自由交易任务并获取股票。";
    private static final String ROOT_PROJECT_GOAL = "让项目从发布、协作、交付到结算都在同一个市场里完成。";

    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final ProjectSharePoolService projectSharePoolService;

    public RootProjectService(
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            ProjectRoleRepository projectRoleRepository,
            ProjectSharePoolService projectSharePoolService) {
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.projectRoleRepository = projectRoleRepository;
        this.projectSharePoolService = projectSharePoolService;
    }

    public ProjectEntity ensureRootProject(String bootstrapAccountId) {
        String ownerAccountId = resolveBootstrapAccount(bootstrapAccountId);
        Instant now = Instant.now();
        ProjectEntity root = normalizeRootProjectNo(projectRepository.findRootProject().orElseGet(() -> createRootProject(ownerAccountId, now)), now);
        MarketEntity market = marketRepository.findById(PostItemSupport.marketIdForPost(PostKind.PROJECT, root.id()))
                .orElseGet(() -> marketRepository.save(rootMarket(root, now)));
        // 中文注释：Root Project 也必须初始化独立 shares pool，公开路由 monopolyfun 与普通项目共享发放规则。
        projectSharePoolService.initialize(root.id(), market.id(), now);
        // 中文注释：Root Project 复用 project_roles，系统级治理与 child 项目治理保持同一套审批口径。
        projectRoleRepository.initializeProjectRoles(root.id(), root.ownerAccountId(), now);
        return root;
    }

    private ProjectEntity normalizeRootProjectNo(ProjectEntity root, Instant now) {
        if (ROOT_PROJECT_NO.equals(root.projectNo())) {
            return root;
        }
        // 中文注释：Root Project 的公开 URL 固定为 monopolyfun，物理 id 继续承接系统事实。
        return projectRepository.save(new ProjectEntity(
                root.id(),
                ROOT_PROJECT_NO,
                root.ownerAccountId(),
                root.projectLevel(),
                root.parentProjectId(),
                root.title(),
                root.summary(),
                root.oneSentence(),
                root.inventoryPolicy(),
                root.stockTotal(),
                root.stockSold(),
                root.status(),
                root.metadata(),
                root.createdAt(),
                now));
    }

    private ProjectEntity createRootProject(String ownerAccountId, Instant now) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("description", ROOT_PROJECT_SUMMARY);
        metadata.put("goal", ROOT_PROJECT_GOAL);
        ProjectEntity root = new ProjectEntity(
                ROOT_PROJECT_ID,
                ROOT_PROJECT_NO,
                ownerAccountId,
                ProjectLevel.ROOT,
                null,
                ROOT_PROJECT_TITLE,
                ROOT_PROJECT_SUMMARY,
                ROOT_PROJECT_SUMMARY,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                ProjectStatus.ACTIVE,
                metadata,
                now,
                now);
        return projectRepository.save(root);
    }

    private MarketEntity rootMarket(ProjectEntity root, Instant now) {
        return new MarketEntity(
                PostItemSupport.marketIdForPost(PostKind.PROJECT, root.id()),
                ROOT_PROJECT_TITLE,
                ROOT_PROJECT_SUMMARY,
                ROOT_PROJECT_GOAL,
                root.ownerAccountId(),
                "project://" + root.id(),
                "http://localhost:3000/market/projects/" + root.projectNo(),
                SettlementType.SHARES,
                0,
                MarketStatus.ACTIVE,
                now,
                "occupied",
                PostItemSupport.defaultMarketMetadata(PostKind.PROJECT),
                now,
                now);
    }

    private String resolveBootstrapAccount(String bootstrapAccountId) {
        if (bootstrapAccountId != null && !bootstrapAccountId.isBlank() && accountRepository.findById(bootstrapAccountId).isPresent()) {
            return bootstrapAccountId;
        }
        return accountRepository.findById(ROOT_FALLBACK_ACCOUNT_ID)
                .map(AccountEntity::id)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    accountRepository.save(new AccountEntity(
                            ROOT_FALLBACK_ACCOUNT_ID,
                            "@root-system",
                            "Root System",
                            null,
                            com.monopolyfun.modules.risk.domain.RiskAccountStatus.ACTIVE,
                            com.monopolyfun.modules.risk.domain.RiskLevel.NORMAL,
                            null,
                            null,
                            null,
                            Map.of(),
                            now,
                            now));
                    return ROOT_FALLBACK_ACCOUNT_ID;
                });
    }
}
