package com.monopolyfun.modules.post.service.command;

import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.initiative.service.InitiativeService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.api.request.ClosePostRequest;
import com.monopolyfun.modules.post.api.request.PublishOfferRequest;
import com.monopolyfun.modules.post.api.request.PublishPostItemRequest;
import com.monopolyfun.modules.post.api.request.PublishRequestRequest;
import com.monopolyfun.modules.post.api.request.UpdateOfferPostRequest;
import com.monopolyfun.modules.post.api.request.UpdateRequestPostRequest;
import com.monopolyfun.modules.post.api.response.OfferCreateResponse;
import com.monopolyfun.modules.post.api.response.RequestCreateResponse;
import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.OfferStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.domain.RequestStatus;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.modules.post.service.view.RequestView;
import com.monopolyfun.modules.project.api.request.PublishProjectItemRequest;
import com.monopolyfun.modules.project.api.request.PublishProjectRequest;
import com.monopolyfun.modules.project.api.request.UpdateProjectPostRequest;
import com.monopolyfun.modules.project.api.response.ProjectCreateResponse;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.ProjectLifecycleService;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.modules.project.service.view.ProjectView;
import com.monopolyfun.modules.repo.service.RepoProvisionService;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.risk.service.RiskAction;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.modules.workthread.service.RevenueAutomationService;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.platform.command.CommandContext;
import com.monopolyfun.platform.command.CommandKernel;
import com.monopolyfun.platform.command.CommandMetadata;
import com.monopolyfun.platform.command.CommandResult;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.platform.lifecycle.LifecycleTransition;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.id.BusinessIdService;
import com.monopolyfun.shared.id.BusinessIdType;
import com.monopolyfun.shared.id.BusinessIds;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional
public class PostCommandService {
    private static final int MAX_PUBLISH_ITEMS = 20;
    private static final int MAX_PROJECT_REFERENCE_LINKS = 5;
    private static final String PROJECT_MAINTENANCE_MODE_REPO_FIRST = "repo_first";
    private static final List<String> PROJECT_REPO_MAINTENANCE_COMMANDS = List.of(
            "gh repo view",
            "gh issue list",
            "gh pr list",
            "gh workflow list");
    private static final Map<String, Object> PROJECT_REPO_MAINTENANCE_PLAYBOOK = Map.of(
            "taskTypes", List.of("backlog_triage", "pr_review", "workflow_health", "release_sync"),
            "evidenceTypes", List.of("issue_url", "pr_url", "commit_url", "workflow_run_url", "release_url"));
    private static final String PAYMENT_METHOD_SHARES = "shares";
    private static final String PAYMENT_METHOD_OKX_DIRECT_PAY = "okx_direct_pay";
    private static final String OKX_DIRECT_PAY_NETWORK = "eip155:196";
    private static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final AccountRepository accountRepository;
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final ListingRepository listingRepository;
    private final PostItemCommandService postItemCommandService;
    private final CurrentAccountAccess currentAccountAccess;
    private final CommandKernel commandKernel;
    private final BusinessIdService businessIdService;
    private final ProjectLifecycleService projectLifecycleService;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final RootProjectService rootProjectService;
    private final AccountRiskGuard accountRiskGuard;
    private final ProjectSharePoolService projectSharePoolService;
    private final RepoProvisionService repoProvisionService;
    private final WorkRepository workRepository;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;
    private final InitiativeService initiativeService;
    private final RevenueAutomationService revenueAutomationService;

    public PostCommandService(
            AccountRepository accountRepository,
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            ListingRepository listingRepository,
            PostItemCommandService postItemCommandService,
            CurrentAccountAccess currentAccountAccess,
            CommandKernel commandKernel,
            BusinessIdService businessIdService,
            ProjectLifecycleService projectLifecycleService,
            OrganizationAuthorityService organizationAuthorityService,
            RootProjectService rootProjectService,
            AccountRiskGuard accountRiskGuard,
            ProjectSharePoolService projectSharePoolService,
            RepoProvisionService repoProvisionService,
            WorkRepository workRepository,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory,
            InitiativeService initiativeService,
            RevenueAutomationService revenueAutomationService) {
        this.accountRepository = accountRepository;
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.listingRepository = listingRepository;
        this.postItemCommandService = postItemCommandService;
        this.currentAccountAccess = currentAccountAccess;
        this.commandKernel = commandKernel;
        this.businessIdService = businessIdService;
        this.projectLifecycleService = projectLifecycleService;
        this.organizationAuthorityService = organizationAuthorityService;
        this.rootProjectService = rootProjectService;
        this.accountRiskGuard = accountRiskGuard;
        this.projectSharePoolService = projectSharePoolService;
        this.repoProvisionService = repoProvisionService;
        this.workRepository = workRepository;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
        this.initiativeService = initiativeService;
        this.revenueAutomationService = revenueAutomationService;
    }

    public OfferCreateResponse createOffer(PublishOfferRequest request) {
        return createOffer(request, false);
    }

    public OfferCreateResponse createOffer(PublishOfferRequest request, boolean includeAgent) {
        String actorAccountId = requireAccount();
        accountRiskGuard.requireAllowed(actorAccountId, RiskAction.PUBLISH_POST);
        validatePostText(request.title(), request.description(), null, "offer");
        List<PublishPostItemRequest> items = normalizePublishItems(request.items());
        String deliveryStandard = firstItemDeliveryStandard(items, "offer.initialTask.deliveryStandard");
        String paymentMethod = normalizePaymentMethod(request.paymentMethod());
        String paymentNetwork = normalizePaymentNetwork(paymentMethod, request.paymentNetwork());
        String paymentRecipient = normalizeOfferPaymentRecipient(paymentMethod, request.paymentRecipient());
        Instant now = Instant.now();
        // 中文注释：Offer 使用统一业务编号，公开 URL 与客服检索读取 offerNo。
        BusinessIds offerIds = businessIdService.next(BusinessIdType.OFFER);
        OfferEntity offer = new OfferEntity(
                offerIds.id(),
                offerIds.displayNo(),
                actorAccountId,
                normalizeTitle(request.title()),
                normalizeLongText(request.description(), "description"),
                deliveryStandard,
                null,
                normalizeCurrency(request.currency(), paymentMethod),
                paymentMethod,
                blankToNull(request.paymentProfile()),
                paymentNetwork,
                blankToNull(request.paymentAsset()),
                paymentRecipient,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                OfferStatus.OPEN,
                paymentMetadata(paymentNetwork, paymentRecipient),
                now,
                now);
        CommandReceipt receipt = commandKernel.execute(new CommandMetadata("publish_offer", "offer", offer.offerNo()), context -> {
            offerRepository.save(offer);
            // 中文注释：发布入口只创建用户明确填写的 item，价格和数量都由 item 自己承担，避免 post 顶层与 item 重复定价。
            MarketEntity market = new MarketEntity(
                    PostItemSupport.marketIdForPost(PostKind.OFFER, offer.id()),
                    offer.title(),
                    offer.description(),
                    deliveryStandard,
                    actorAccountId,
                    "offer://" + offer.id(),
                    "http://localhost:3000/market/offers/" + offer.offerNo(),
                    SettlementType.MONEY,
                    0,
                    MarketStatus.ACTIVE,
                    context.startedAt(),
                    "occupied",
                    PostItemSupport.defaultMarketMetadata(PostKind.OFFER),
                    context.startedAt(),
                    context.startedAt());
            marketRepository.save(market);
            savePublishedItems(PostKind.OFFER, offer.id(), market, actorAccountId, items, context.startedAt());
            return new CommandResult(offer.offerNo(), "offer_created", Map.of("offerNo", offer.offerNo()), List.of());
        });
        OfferView view = com.monopolyfun.modules.post.service.mapper.PostViewMapper.offer(offer);
        if (includeAgent) {
            // 中文注释：发布后的 agent 状态用于自动化 readback，普通发布响应保持业务字段为主。
            view = view.withAgentState(
                    agentResourceKeyFactory.offer(offer.offerNo()),
                    agentCapabilityResolver.offerCapabilities(offer, actorAccountId),
                    agentCapabilityResolver.offerBlockedCapabilities(offer, actorAccountId));
        }
        return new OfferCreateResponse(view, receipt);
    }

    public RequestCreateResponse createRequest(PublishRequestRequest request) {
        return createRequest(request, false);
    }

    public RequestCreateResponse createRequest(PublishRequestRequest request, boolean includeAgent) {
        String actorAccountId = requireAccount();
        accountRiskGuard.requireAllowed(actorAccountId, RiskAction.PUBLISH_POST);
        validatePostText(request.title(), request.description(), null, "request");
        List<PublishPostItemRequest> items = normalizePublishItems(request.items());
        String deliveryStandard = firstItemDeliveryStandard(items, "request.initialTask.deliveryStandard");
        String paymentMethod = normalizePaymentMethod(request.paymentMethod());
        String paymentNetwork = normalizePaymentNetwork(paymentMethod, request.paymentNetwork());
        String paymentRecipient = normalizeRequestPaymentRecipient(paymentMethod, request.paymentRecipient());
        Instant deadlineAt = parseDeadline(request.deadlineAt());
        Instant now = Instant.now();
        // 中文注释：Request 使用统一业务编号，公开 URL 与客服检索读取 requestNo。
        BusinessIds requestIds = businessIdService.next(BusinessIdType.REQUEST);
        RequestEntity demand = new RequestEntity(
                requestIds.id(),
                requestIds.displayNo(),
                actorAccountId,
                normalizeTitle(request.title()),
                normalizeLongText(request.description(), "description"),
                deliveryStandard,
                null,
                normalizeCurrency(request.currency(), paymentMethod),
                paymentMethod,
                blankToNull(request.paymentProfile()),
                paymentNetwork,
                blankToNull(request.paymentAsset()),
                paymentRecipient,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                RequestStatus.OPEN,
                deadlineAt,
                paymentMetadata(paymentNetwork, paymentRecipient),
                now,
                now);
        commandKernel.execute(new CommandMetadata("publish_request", "request", demand.requestNo()), context -> {
            requestPostRepository.save(demand);
            // 中文注释：request 也升级成 market 容器，后续需求条目直接挂在 request market 下。
            MarketEntity market = new MarketEntity(
                    PostItemSupport.marketIdForPost(PostKind.REQUEST, demand.id()),
                    demand.title(),
                    demand.description(),
                    deliveryStandard,
                    actorAccountId,
                    "request://" + demand.id(),
                    "http://localhost:3000/market/requests/" + demand.requestNo(),
                    SettlementType.MONEY,
                    0,
                    MarketStatus.ACTIVE,
                    context.startedAt(),
                    "occupied",
                    PostItemSupport.defaultMarketMetadata(PostKind.REQUEST),
                    context.startedAt(),
                    context.startedAt());
            marketRepository.save(market);
            savePublishedItems(PostKind.REQUEST, demand.id(), market, actorAccountId, items, context.startedAt());
            return new CommandResult(demand.requestNo(), "request_created", Map.of("requestNo", demand.requestNo()), List.of());
        });
        RequestView view = com.monopolyfun.modules.post.service.mapper.PostViewMapper.request(demand);
        if (includeAgent) {
            // 中文注释：Request 发布后的 agent 状态用于继续追加需求 item 或关闭需求。
            view = view.withAgentState(
                    agentResourceKeyFactory.request(demand.requestNo()),
                    agentCapabilityResolver.requestCapabilities(demand, actorAccountId),
                    agentCapabilityResolver.requestBlockedCapabilities(demand, actorAccountId));
        }
        return new RequestCreateResponse(view);
    }

    public ProjectCreateResponse createProject(PublishProjectRequest request) {
        return createProject(request, false);
    }

    public ProjectCreateResponse createProject(PublishProjectRequest request, boolean includeAgent) {
        String ownerAccountId = requireAccount();
        accountRiskGuard.requireAllowed(ownerAccountId, RiskAction.PUBLISH_POST);
        validateProjectPostText(request);
        List<PublishPostItemRequest> items = normalizeProjectPublishItems(request.items());
        validateProjectSystemPricingRequest(request, items);
        ProjectEntity rootProject = rootProjectService.ensureRootProject(ownerAccountId);
        String parentProjectId = normalizeParentProjectId(request.parentProjectId(), rootProject.id());
        String title = normalizeTitle(request.title());
        String description = normalizeLongText(request.description(), "description");
        Instant now = Instant.now();
        // 中文注释：项目编号从统一入口生成，页面展示与后续对账共享同一套规则。
        BusinessIds projectIds = businessIdService.next(BusinessIdType.PROJECT);
        RepoProvisionService.ResolvedProjectRepo resolvedRepo = repoProvisionService.resolveProjectRepo(
                ownerAccountId,
                projectIds.displayNo(),
                title,
                firstNonBlank(request.goal(), request.description(), request.title()),
                request.provisionSessionId());
        Map<String, Object> metadata = projectLifecycleService.withInitialOwnerAction(buildProjectMetadata(request, resolvedRepo), now, "publish_project");
        ProjectEntity project = new ProjectEntity(
                projectIds.id(),
                projectIds.displayNo(),
                ownerAccountId,
                ProjectLevel.CHILD,
                parentProjectId,
                title,
                description,
                description,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                ProjectStatus.ACTIVE,
                metadata,
                now,
                now);
        commandKernel.execute(new CommandMetadata("publish_project", "project", project.projectNo()), context -> {
            projectRepository.save(project);
            // 中文注释：项目创建时绑定系统默认收益轨道，真实用户无需理解链、合约和 token 字段。
            revenueAutomationService.ensureSystemRevenueTrack(project, context.startedAt());
            String marketGoal = metadata.get("goal") == null ? project.summary() : String.valueOf(metadata.get("goal")).trim();
            // 中文注释：project 创建后立刻生成 1:1 market，并初始化 1000 万总份额与 curve 参数，后续 item 直接挂到这个市场里。
            MarketEntity market = new MarketEntity(
                    PostItemSupport.marketIdForPost(PostKind.PROJECT, project.id()),
                    project.title(),
                    project.summary(),
                    marketGoal,
                    ownerAccountId,
                    "project://" + project.id(),
                    "http://localhost:3000/market/projects/" + project.projectNo(),
                    SettlementType.SHARES,
                    0,
                    MarketStatus.ACTIVE,
                    context.startedAt(),
                    "occupied",
                    PostItemSupport.defaultMarketMetadata(PostKind.PROJECT),
                    context.startedAt(),
                    context.startedAt());
            marketRepository.save(market);
            // 中文注释：Project shares 的预算和 curve 状态独立落到 share pool，market 只保留公开展示职责。
            projectSharePoolService.initialize(project.id(), market.id(), context.startedAt());
            // 中文注释：初始化需求只保留为公开 item，Launch Card 由参与者在验证协议里继续迭代。
            savePublishedItems(PostKind.PROJECT, project.id(), market, ownerAccountId, items, context.startedAt());
            // 中文注释：Project 发布完成后立即生成项目推荐，让角色补位和维护任务进入同一个 workbench 推进面。
            initiativeService.generateProjectRecommendations(project.projectNo());
            return new CommandResult(project.projectNo(), "project_created", Map.of("projectNo", project.projectNo()), List.of());
        });
        // 中文注释：Project 创建完成后立即生成 owner 下一步推荐，保证 agent 与 workbench 都有可执行入口。
        initiativeService.generateProjectRecommendations(project.projectNo());
        ProjectView view = com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.project(project, List.of());
        if (includeAgent) {
            // 中文注释：普通 Project 创建后开放市场任务能力，角色任命集中到 Root Project。
            view = view.withAgentState(
                    agentResourceKeyFactory.project(project.projectNo()),
                    agentCapabilityResolver.projectCapabilities(project, true, false, ownerAccountId),
                    agentCapabilityResolver.projectBlockedCapabilities(project, true, false, ownerAccountId));
        }
        return new ProjectCreateResponse(view);
    }

    public OfferView updateOffer(String offerNo, UpdateOfferPostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validatePostText(request.title(), request.description(), request.deliveryStandard(), "offer");
        OfferEntity current = offerRepository.findByOfferNo(offerNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        String offerId = current.id();
        requireOfferOwner(current, request.actorAccountId());
        requireOpenOffer(current);
        String paymentMethod = normalizeUpdatedPaymentMethod(current.paymentMethod(), request.paymentMethod());
        String paymentNetwork = normalizePaymentNetwork(paymentMethod, firstPresent(request.paymentNetwork(), current.paymentNetwork()));
        String paymentRecipient = normalizeOfferPaymentRecipient(paymentMethod, firstPresent(request.paymentRecipient(), current.paymentRecipient()));
        OfferEntity updated = new OfferEntity(
                current.id(),
                current.offerNo(),
                current.actorAccountId(),
                normalizeTitle(request.title()),
                normalizeLongText(request.description(), "description"),
                normalizeOptionalLongText(request.deliveryStandard()) == null ? current.deliveryStandard() : normalizeOptionalLongText(request.deliveryStandard()),
                current.priceAmount(),
                normalizeCurrency(firstPresent(request.currency(), current.currency()), paymentMethod),
                paymentMethod,
                firstPresent(request.paymentProfile(), current.paymentProfile()),
                paymentNetwork,
                firstPresent(request.paymentAsset(), current.paymentAsset()),
                paymentRecipient,
                current.inventoryPolicy(),
                current.stockTotal(),
                current.stockSold(),
                current.status(),
                paymentMetadata(paymentNetwork, paymentRecipient),
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("update_offer", "offer", current.offerNo()), context -> {
            offerRepository.save(updated);
            updatePostMarket(PostKind.OFFER, offerId, updated.title(), updated.description(), updated.deliveryStandard(), context);
            return new CommandResult(current.offerNo(), "offer_updated", Map.of("offerNo", current.offerNo()), List.of());
        });
        return com.monopolyfun.modules.post.service.mapper.PostViewMapper.offer(updated);
    }

    public OfferView closeOffer(String offerNo, ClosePostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        OfferEntity current = offerRepository.findByOfferNo(offerNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        String offerId = current.id();
        requireOfferOwner(current, request.actorAccountId());
        requireOpenOffer(current);
        OfferEntity closed = new OfferEntity(
                current.id(),
                current.offerNo(),
                current.actorAccountId(),
                current.title(),
                current.description(),
                current.deliveryStandard(),
                current.priceAmount(),
                current.currency(),
                current.paymentMethod(),
                current.paymentProfile(),
                current.paymentNetwork(),
                current.paymentAsset(),
                current.paymentRecipient(),
                current.inventoryPolicy(),
                current.stockTotal(),
                current.stockSold(),
                OfferStatus.CLOSED,
                current.metadata(),
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("close_offer", "offer", current.offerNo()), context -> {
            offerRepository.save(closed);
            // 中文注释：关闭 post 时同步关闭 market 和公开 item，页面与下单校验都会立即停止新交易。
            List<LifecycleTransition<?, ?>> transitions = closePostMarketAndItems(PostKind.OFFER, offerId, request.reason(), context);
            workRepository.closeOpenItemsBySource("offer", current.offerNo(), "post_closed");
            return new CommandResult(current.offerNo(), "offer_closed", Map.of("offerNo", current.offerNo()), transitions);
        });
        return com.monopolyfun.modules.post.service.mapper.PostViewMapper.offer(closed);
    }

    public RequestView updateRequest(String requestNo, UpdateRequestPostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validatePostText(request.title(), request.description(), request.deliveryStandard(), "request");
        RequestEntity current = requestPostRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        String requestId = current.id();
        requireRequestOwner(current, request.actorAccountId());
        requireOpenRequest(current);
        String paymentMethod = normalizeUpdatedPaymentMethod(current.paymentMethod(), request.paymentMethod());
        String paymentNetwork = normalizePaymentNetwork(paymentMethod, firstPresent(request.paymentNetwork(), current.paymentNetwork()));
        String paymentRecipient = normalizeRequestPaymentRecipient(paymentMethod, firstPresent(request.paymentRecipient(), current.paymentRecipient()));
        RequestEntity updated = new RequestEntity(
                current.id(),
                current.requestNo(),
                current.actorAccountId(),
                normalizeTitle(request.title()),
                normalizeLongText(request.description(), "description"),
                normalizeOptionalLongText(request.deliveryStandard()) == null ? current.deliveryStandard() : normalizeOptionalLongText(request.deliveryStandard()),
                current.budgetAmount(),
                normalizeCurrency(firstPresent(request.currency(), current.currency()), paymentMethod),
                paymentMethod,
                firstPresent(request.paymentProfile(), current.paymentProfile()),
                paymentNetwork,
                firstPresent(request.paymentAsset(), current.paymentAsset()),
                paymentRecipient,
                current.inventoryPolicy(),
                current.stockTotal(),
                current.stockFilled(),
                current.status(),
                request.deadlineAt() == null ? current.deadlineAt() : parseDeadline(request.deadlineAt()),
                paymentMetadata(paymentNetwork, paymentRecipient),
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("update_request", "request", current.requestNo()), context -> {
            requestPostRepository.save(updated);
            updatePostMarket(PostKind.REQUEST, requestId, updated.title(), updated.description(), updated.deliveryStandard(), context);
            return new CommandResult(current.requestNo(), "request_updated", Map.of("requestNo", current.requestNo()), List.of());
        });
        return com.monopolyfun.modules.post.service.mapper.PostViewMapper.request(updated);
    }

    public RequestView closeRequest(String requestNo, ClosePostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        RequestEntity current = requestPostRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        String requestId = current.id();
        requireRequestOwner(current, request.actorAccountId());
        requireOpenRequest(current);
        RequestEntity closed = new RequestEntity(
                current.id(),
                current.requestNo(),
                current.actorAccountId(),
                current.title(),
                current.description(),
                current.deliveryStandard(),
                current.budgetAmount(),
                current.currency(),
                current.paymentMethod(),
                current.paymentProfile(),
                current.paymentNetwork(),
                current.paymentAsset(),
                current.paymentRecipient(),
                current.inventoryPolicy(),
                current.stockTotal(),
                current.stockFilled(),
                RequestStatus.CLOSED,
                current.deadlineAt(),
                current.metadata(),
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("close_request", "request", current.requestNo()), context -> {
            requestPostRepository.save(closed);
            // 中文注释：需求关闭会停止继续接单，已有 order 保持独立生命周期继续履约或结算。
            List<LifecycleTransition<?, ?>> transitions = closePostMarketAndItems(PostKind.REQUEST, requestId, request.reason(), context);
            workRepository.closeOpenItemsBySource("request", current.requestNo(), "post_closed");
            return new CommandResult(current.requestNo(), "request_closed", Map.of("requestNo", current.requestNo()), transitions);
        });
        return com.monopolyfun.modules.post.service.mapper.PostViewMapper.request(closed);
    }

    public ProjectView updateProject(String projectNo, UpdateProjectPostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        validateProjectUpdateText(request);
        ProjectEntity current = projectRepository.findByProjectNo(projectNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String projectId = current.id();
        requireProjectOwner(current, request.actorAccountId());
        if (current.status() != ProjectStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not active");
        }
        String title = normalizeTitle(request.title());
        String description = normalizeLongText(request.description(), "description");
        Map<String, Object> metadata = updatedProjectMetadata(current, description, request);
        ProjectEntity updated = new ProjectEntity(
                current.id(),
                current.projectNo(),
                current.ownerAccountId(),
                current.projectLevel(),
                current.parentProjectId(),
                title,
                description,
                description,
                current.inventoryPolicy(),
                current.stockTotal(),
                current.stockSold(),
                current.status(),
                metadata,
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("update_project", "project", current.projectNo()), context -> {
            projectRepository.save(updated);
            updatePostMarket(PostKind.PROJECT, projectId, updated.title(), updated.summary(), stringValue(metadata.get("goal")), context);
            projectLifecycleService.touchOwnerAction(projectId, request.actorAccountId(), "update_project");
            return new CommandResult(current.projectNo(), "project_updated", Map.of("projectNo", current.projectNo()), List.of());
        });
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.project(updated, organizationAuthorityService.listProjectRoles(updated.id()).stream()
                .map(com.monopolyfun.modules.project.service.mapper.ProjectViewMapper::projectRole)
                .toList());
    }

    public ProjectView closeProject(String projectNo, ClosePostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        ProjectEntity current = projectRepository.findByProjectNo(projectNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        requireProjectOwner(current, request.actorAccountId());
        if (current.status() != ProjectStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not active");
        }
        Map<String, Object> metadata = current.mutableMetadata();
        metadata.put("closedReason", blankToNull(request.reason()) == null ? "owner_closed" : blankToNull(request.reason()));
        ProjectEntity archived = current.withStatus(ProjectStatus.ARCHIVED, metadata);
        commandKernel.execute(new CommandMetadata("close_project", "project", current.projectNo()), context -> {
            projectRepository.save(archived);
            // 中文注释：项目关闭和 offer/request 一样同步关闭公开 market 与 item，避免 agent 继续领到过期任务。
            List<LifecycleTransition<?, ?>> transitions = closePostMarketAndItems(PostKind.PROJECT, current.id(), request.reason(), context);
            projectLifecycleService.touchOwnerAction(current.id(), request.actorAccountId(), "close_project");
            workRepository.closeOpenItemsBySource("project", current.projectNo(), "post_closed");
            return new CommandResult(current.projectNo(), "project_closed", Map.of("projectNo", current.projectNo()), transitions);
        });
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.project(archived, organizationAuthorityService.listProjectRoles(archived.id()).stream()
                .map(com.monopolyfun.modules.project.service.mapper.ProjectViewMapper::projectRole)
                .toList());
    }

    private void validatePostText(String title, String description, String deliveryStandard, String scope) {
        // 中文注释：发布和更新入口先执行统一长度边界，避免超长文本绕过 DTO 进入 post/market 双写。
        RequestPayloadLimits.requireTextLength(scope + ".title", title, 80);
        RequestPayloadLimits.requireTextLength(scope + ".description", description, 1000);
        RequestPayloadLimits.requireTextLength(scope + ".deliveryStandard", deliveryStandard, 1000);
    }

    private void validateProjectPostText(PublishProjectRequest request) {
        validatePostText(request.title(), request.description(), null, "project");
        RequestPayloadLimits.requireTextLength("project.goal", request.goal(), 2000);
        RequestPayloadLimits.requireTextLength("project.ownerIntro", request.ownerIntro(), 2000);
        RequestPayloadLimits.requireTextLength("project.provisionSessionId", request.provisionSessionId(), 120);
    }

    private void validateProjectUpdateText(UpdateProjectPostRequest request) {
        validatePostText(request.title(), request.description(), null, "project");
        RequestPayloadLimits.requireTextLength("project.goal", request.goal(), 2000);
        RequestPayloadLimits.requireTextLength("project.ownerIntro", request.ownerIntro(), 2000);
    }

    private String requireAccount() {
        String accountId = currentAccountAccess.requireAccountId();
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account not found"));
        return accountId;
    }

    private List<PublishPostItemRequest> normalizePublishItems(List<PublishPostItemRequest> items) {
        if (items == null || items.stream().filter(item -> item != null).findAny().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items is required");
        }
        List<PublishPostItemRequest> normalized = items.stream().filter(item -> item != null).toList();
        if (normalized.size() > MAX_PUBLISH_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items cannot exceed " + MAX_PUBLISH_ITEMS);
        }
        for (int index = 0; index < normalized.size(); index++) {
            validatePublishItem(index, normalized.get(index));
        }
        return normalized;
    }

    private List<PublishPostItemRequest> normalizeProjectPublishItems(List<PublishProjectItemRequest> items) {
        if (items == null || items.stream().filter(item -> item != null).findAny().isEmpty()) {
            // 中文注释：Project 的首个可执行对象是初始任务，空项目会让公司创建后失去下一步动作。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initial task is required");
        }
        List<PublishPostItemRequest> normalized = items.stream()
                .filter(item -> item != null)
                .map(this::toProjectPublishItem)
                .toList();
        if (normalized.size() > MAX_PUBLISH_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items cannot exceed " + MAX_PUBLISH_ITEMS);
        }
        for (int index = 0; index < normalized.size(); index++) {
            validatePublishItem(index, normalized.get(index));
        }
        return normalized;
    }

    private PublishPostItemRequest toProjectPublishItem(PublishProjectItemRequest item) {
        // 中文注释：Project 初始任务统一映射到内部任务项合同，系统补齐固定 quantity 与派生字段，避免两套落库分支继续漂移。
        return new PublishPostItemRequest(
                item.name(),
                item.description(),
                item.deliveryStandard(),
                item.acceptanceCriteria(),
                null,
                item.difficultyScore(),
                1,
                null,
                null);
    }

    private void validatePublishItem(int index, PublishPostItemRequest item) {
        RequestPayloadLimits.requireTextLength("items[" + index + "].name", item.name(), 120);
        RequestPayloadLimits.requireTextLength("items[" + index + "].description", item.description(), 1000);
        RequestPayloadLimits.requireTextLength("items[" + index + "].deliveryStandard", item.deliveryStandard(), 1000);
        RequestPayloadLimits.requireStringList("items[" + index + "].acceptanceCriteria", item.acceptanceCriteria(), 20, 500);
        RequestPayloadLimits.requireTextLength("items[" + index + "].agentInstruction", item.agentInstruction(), 2000);
    }

    private List<ListingEntity> savePublishedItems(
            PostKind postKind,
            String postId,
            MarketEntity market,
            String actorAccountId,
            List<PublishPostItemRequest> items,
            Instant now) {
        List<ListingEntity> savedItems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            PublishPostItemRequest item = items.get(index);
            ListingEntity listing = postItemCommandService.buildPostItemForPublish(postKind, postId, market, actorAccountId, item, now, index);
            listingRepository.save(listing);
            savedItems.add(listing);
        }
        return savedItems;
    }

    private void updatePostMarket(PostKind postKind, String postId, String title, String summary, String goal, CommandContext context) {
        MarketEntity market = requirePostMarket(postKind, postId);
        var updated = market.updateDetails(
                title,
                summary,
                goal,
                market.sourceRef(),
                market.surfaceUrl(),
                market.settlementType(),
                lifecycleContext(context, Map.of("postKind", postKind.name().toLowerCase(), "postId", postId)));
        marketRepository.save(updated.entity());
    }

    private List<LifecycleTransition<?, ?>> closePostMarketAndItems(PostKind postKind, String postId, String reason, CommandContext context) {
        MarketEntity market = requirePostMarket(postKind, postId);
        List<LifecycleTransition<?, ?>> transitions = new ArrayList<>();
        var stalled = market.stall(lifecycleContext(context, Map.of(
                "postKind", postKind.name().toLowerCase(),
                "postId", postId,
                "reason", blankToNull(reason) == null ? "owner_closed" : blankToNull(reason))));
        marketRepository.save(stalled.entity());
        transitions.add(stalled.transition());
        for (ListingEntity listing : listingRepository.findByMarketId(market.id())) {
            if (listing.status() == ListingStatus.CLOSED || listing.status() == ListingStatus.ARCHIVED) {
                continue;
            }
            var closed = listing.close(lifecycleContext(context, Map.of("postKind", postKind.name().toLowerCase(), "postId", postId, "itemId", listing.id())));
            listingRepository.save(closed.entity());
            transitions.add(closed.transition());
        }
        return transitions;
    }

    private MarketEntity requirePostMarket(PostKind postKind, String postId) {
        return marketRepository.findById(PostItemSupport.marketIdForPost(postKind, postId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post market not found"));
    }

    private void requireOfferOwner(OfferEntity offer, String actorAccountId) {
        if (!offer.actorAccountId().equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner can update offer");
        }
    }

    private void requireRequestOwner(RequestEntity request, String actorAccountId) {
        if (!request.actorAccountId().equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner can update request");
        }
    }

    private void requireProjectOwner(ProjectEntity project, String actorAccountId) {
        if (!project.ownerAccountId().equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only project owner can update project");
        }
    }

    private void requireOpenOffer(OfferEntity offer) {
        if (offer.status() != OfferStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer is already closed");
        }
    }

    private void requireOpenRequest(RequestEntity request) {
        if (request.status() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is already closed");
        }
    }

    private String normalizeUpdatedPaymentMethod(String currentPaymentMethod, String requestedPaymentMethod) {
        return blankToNull(requestedPaymentMethod) == null ? currentPaymentMethod : normalizePaymentMethod(requestedPaymentMethod);
    }

    private String firstPresent(String requestedValue, String currentValue) {
        String requested = blankToNull(requestedValue);
        return requested == null ? currentValue : requested;
    }

    private String firstItemDeliveryStandard(List<PublishPostItemRequest> items, String fieldName) {
        return normalizeLongText(items.get(0).deliveryStandard(), fieldName);
    }

    private Instant parseDeadline(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deadlineAt must be an ISO-8601 datetime");
        }
    }

    private String normalizeCurrency(String value, String paymentMethod) {
        if (PAYMENT_METHOD_SHARES.equalsIgnoreCase(paymentMethod)) {
            return "SHARES";
        }
        String currency = value == null || value.isBlank() ? "USD" : value.trim().toUpperCase();
        return currency.length() > 20 ? currency.substring(0, 20) : currency;
    }

    private String normalizePaymentMethod(String value) {
        // 中文注释：公开买卖发布统一进入 OKX 支付流，订单执行入口由付款状态控制。
        String paymentMethod = value == null || value.isBlank() ? PAYMENT_METHOD_OKX_DIRECT_PAY : value.trim();
        if (PAYMENT_METHOD_OKX_DIRECT_PAY.equals(paymentMethod)) {
            return paymentMethod;
        }
        if (PAYMENT_METHOD_SHARES.equals(paymentMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "买卖发布使用 OKX 支付，Shares 用于公司任务和评审订单");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method");
    }

    private String normalizeOfferPaymentRecipient(String paymentMethod, String value) {
        String recipient = blankToNull(value);
        if (!PAYMENT_METHOD_OKX_DIRECT_PAY.equals(paymentMethod)) {
            return null;
        }
        if (recipient == null || !EVM_ADDRESS_PATTERN.matcher(recipient).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX Direct Pay requires a seller EVM paymentRecipient");
        }
        return recipient;
    }

    private String normalizeRequestPaymentRecipient(String paymentMethod, String value) {
        if (!PAYMENT_METHOD_OKX_DIRECT_PAY.equals(paymentMethod)) {
            return null;
        }
        String recipient = blankToNull(value);
        if (recipient == null) {
            return null;
        }
        if (!EVM_ADDRESS_PATTERN.matcher(recipient).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX Direct Pay requires a valid EVM paymentRecipient");
        }
        return recipient;
    }

    private String normalizePaymentNetwork(String paymentMethod, String value) {
        if (!PAYMENT_METHOD_OKX_DIRECT_PAY.equals(paymentMethod)) {
            return null;
        }
        String requestedNetwork = blankToNull(value);
        if (requestedNetwork != null && !OKX_DIRECT_PAY_NETWORK.equals(requestedNetwork)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OKX Direct Pay only supports X Layer eip155:196");
        }
        // 中文注释：OKX 官方当前只开放 X Layer ChainIndex 196，发布链路固定写入 CAIP-2 网络标识。
        return OKX_DIRECT_PAY_NETWORK;
    }

    private Map<String, Object> paymentMetadata(String paymentNetwork, String paymentRecipient) {
        if (paymentNetwork == null && paymentRecipient == null) {
            return Map.of();
        }
        // 中文注释：收款地址只作为发布级支付配置进入 metadata，listing/order 快照再固化一份用于争议证据。
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "paymentNetwork", paymentNetwork);
        putIfPresent(metadata, "paymentRecipient", paymentRecipient);
        return metadata;
    }

    private String normalizeTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    private String normalizeLongText(String value, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private Map<String, Object> buildProjectMetadata(PublishProjectRequest request, RepoProvisionService.ResolvedProjectRepo resolvedRepo) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        List<String> links = normalizeRequiredReferenceLinks(resolvedRepo.referenceLinks());

        // 中文注释：项目详情页需要可持续复用的长文本区块，这里把可选字段集中写进 metadata，避免继续把一整页信息压扁成 oneSentence。
        putIfPresent(metadata, "description", normalizeOptionalLongText(request.description()));
        putIfPresent(metadata, "goal", normalizeOptionalLongText(request.goal()));
        putIfPresent(metadata, "ownerIntro", normalizeOptionalLongText(request.ownerIntro()));
        metadata.put("referenceLinks", links);
        putIfPresent(metadata, "repoManagementMode", resolvedRepo.repoManagementMode());
        putIfPresent(metadata, "repoProvisionSessionId", resolvedRepo.provisionSessionId());
        putRepoMaintenanceMetadata(metadata, links.get(0));
        return metadata;
    }

    private Map<String, Object> updatedProjectMetadata(ProjectEntity current, String description, UpdateProjectPostRequest request) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(current.metadata() == null ? Map.of() : current.metadata());
        // 中文注释：编辑项目时保留生命周期扩展字段，只覆盖详情页直接读取的可变发布信息。
        putOrRemove(metadata, "description", normalizeOptionalLongText(description));
        putOrRemove(metadata, "goal", normalizeOptionalLongText(request.goal()));
        putOrRemove(metadata, "ownerIntro", normalizeOptionalLongText(request.ownerIntro()));
        // 中文注释：平台仓库是项目维护事实源，编辑项目内容时延续创建阶段绑定的系统仓库。
        List<String> links = normalizeRequiredReferenceLinks(metadataReferenceLinks(current));
        metadata.put("referenceLinks", links);
        putRepoMaintenanceMetadata(metadata, links.get(0));
        return Map.copyOf(metadata);
    }

    private List<String> metadataReferenceLinks(ProjectEntity project) {
        Object value = project.metadata() == null ? null : project.metadata().get("referenceLinks");
        if (!(value instanceof List<?> rawLinks)) {
            return List.of();
        }
        return rawLinks.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private void validateProjectSystemPricingRequest(PublishProjectRequest request, List<PublishPostItemRequest> items) {
        // 中文注释：Project 发布只接收 item 难度分，实际 shares 数量在领取时按 curve 统一计算。
        for (int index = 0; index < items.size(); index++) {
            PublishPostItemRequest item = items.get(index);
            if (item.amount() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project items[" + index + "].amount is system-priced and must not be provided");
            }
            if (item.agentInstruction() != null && !item.agentInstruction().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project items[" + index + "].agentInstruction is derived from deliveryStandard and must not be provided");
            }
            if (item.quantity() != null && item.quantity() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project items[" + index + "].quantity must be 1");
            }
        }
    }

    private String normalizeParentProjectId(String requestedParentProjectId, String rootProjectId) {
        String parentProjectId = requestedParentProjectId == null || requestedParentProjectId.isBlank()
                ? rootProjectId
                : requestedParentProjectId.trim();
        if (!rootProjectId.equals(parentProjectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Child project parentProjectId must be the root project");
        }
        return parentProjectId;
    }

    private String normalizeOptionalLongText(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private void putOrRemove(Map<String, Object> target, String key, String value) {
        if (value == null || value.isBlank()) {
            target.remove(key);
            return;
        }
        target.put(key, value);
    }

    private void putListIfPresent(Map<String, Object> target, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }

    private List<String> normalizeReferenceLinks(List<String> values) {
        if (values == null) return List.of();
        List<String> normalized = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > MAX_PROJECT_REFERENCE_LINKS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceLinks cannot exceed " + MAX_PROJECT_REFERENCE_LINKS);
        }
        for (String link : normalized) {
            validateReferenceLink(link);
        }
        return normalized;
    }

    private List<String> normalizeRequiredReferenceLinks(List<String> values) {
        List<String> links = normalizeReferenceLinks(values);
        // 中文注释：公司项目以平台仓库为维护事实源，创建和更新都要保留系统生成的 repo URL。
        if (links.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceLinks is required");
        }
        return links;
    }

    private void putRepoMaintenanceMetadata(Map<String, Object> metadata, String primaryLink) {
        RepoReference repo = parseRepoReference(primaryLink);
        metadata.put("maintenanceMode", PROJECT_MAINTENANCE_MODE_REPO_FIRST);
        metadata.put("repoProvider", repo.provider());
        metadata.put("repoOwner", repo.owner());
        metadata.put("repoName", repo.name());
        metadata.put("defaultMaintenanceCommands", PROJECT_REPO_MAINTENANCE_COMMANDS);
        metadata.put("maintenancePlaybook", PROJECT_REPO_MAINTENANCE_PLAYBOOK);
    }

    private RepoReference parseRepoReference(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().replaceFirst("^/+", "").split("/");
            if (host.equals("github.com") && parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return new RepoReference("github", parts[0], parts[1].replaceFirst("\\.git$", ""));
            }
            return new RepoReference(host.isBlank() ? "external_git" : host, "", "");
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceLinks must be valid URLs");
        }
    }

    private void validateReferenceLink(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceLinks must be http or https URLs");
            }
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceLinks must be valid URLs");
        }
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private LifecycleContext lifecycleContext(CommandContext context, Map<String, Object> metadata) {
        return new LifecycleContext(context.actorAccountId(), context.traceId(), context.startedAt(), metadata);
    }

    private record RepoReference(String provider, String owner, String name) {
    }
}
