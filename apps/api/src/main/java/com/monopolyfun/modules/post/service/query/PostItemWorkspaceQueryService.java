package com.monopolyfun.modules.post.service.query;

import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.post.service.view.PostWorkspaceView;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.modules.share.service.view.ProjectSharesView;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PostItemWorkspaceQueryService {
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ProjectSharePoolService projectSharePoolService;
    private final CurrentAccountAccess currentAccountAccess;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;

    public PostItemWorkspaceQueryService(
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            ListingRepository listingRepository,
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            OrganizationAuthorityService organizationAuthorityService,
            ProjectSharePoolService projectSharePoolService,
            CurrentAccountAccess currentAccountAccess,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory) {
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.projectSharePoolService = projectSharePoolService;
        this.currentAccountAccess = currentAccountAccess;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
    }

    public PostWorkspaceView getWorkspace(String postNo) {
        return getWorkspace(postNo, false);
    }

    public PostWorkspaceView getWorkspace(String postNo, boolean includeAgent) {
        PostRef postRef = requirePostRef(postNo);
        Object post = requirePostView(postRef.postKind(), postRef.postId());
        MarketEntity market = requirePostMarket(postRef.postKind(), postRef.postId());
        List<PostItemView> items = listItemsForRef(postRef, includeAgent);
        Map<String, Long> itemCounts = items.stream()
                .collect(Collectors.groupingBy(PostItemView::status, LinkedHashMap::new, Collectors.counting()));
        return new PostWorkspaceView(postRef.postKind(), post, com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.market(market), buildSharesView(postRef.postKind(), market), items, itemCounts);
    }

    public List<PostItemView> listItems(String postNo) {
        return listItems(postNo, false);
    }

    public List<PostItemView> listItems(String postNo, boolean includeAgent) {
        PostRef postRef = requirePostRef(postNo);
        return listItemsForRef(postRef, includeAgent);
    }

    private List<PostItemView> listItemsForRef(PostRef postRef, boolean includeAgent) {
        PostKind postKind = postRef.postKind();
        String internalPostId = postRef.postId();
        MarketEntity market = requirePostMarket(postKind, internalPostId);
        List<ListingRepository.PostItemListing> postItems = listingRepository.findPostItems(postKind, internalPostId);
        Map<String, OrderEntity> latestOrders = orderRepository.findByIds(postItems.stream()
                        .map(ListingRepository.PostItemListing::latestOrderId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList())
                .stream()
                .collect(Collectors.toMap(OrderEntity::id, order -> order));
        return postItems.stream()
                .map(item -> toItemView(postKind, internalPostId, market, item.listing(), latestOrders.get(item.latestOrderId()), item.latestPaymentStatus(), includeAgent))
                .sorted(Comparator.comparing(PostItemView::status).thenComparing(PostItemView::updatedAt).reversed())
                .toList();
    }

    public PostItemView getItem(String itemId) {
        return getItem(itemId, false);
    }

    public PostItemView getItem(String itemId, boolean includeAgent) {
        ListingRepository.PostItemListing postItem = requirePostItemReadModel(itemId);
        ListingEntity listing = postItem.listing();
        PostKind postKind = PostItemSupport.postKind(listing.metadata());
        String postId = PostItemSupport.postId(listing.metadata());
        MarketEntity market = requirePostMarket(postKind, postId);
        OrderEntity latestOrder = postItem.latestOrderId() == null
                ? null
                : orderRepository.findById(postItem.latestOrderId()).orElse(null);
        return toItemView(postKind, postId, market, listing, latestOrder, postItem.latestPaymentStatus(), includeAgent);
    }

    private ListingRepository.PostItemListing requirePostItemReadModel(String itemId) {
        return listingRepository.findPostItemById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post item not found"));
    }

    public ListingEntity requirePostItem(String itemId) {
        ListingEntity listing = listingRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post item not found"));
        if (!PostItemSupport.SUBJECT_TYPE.equalsIgnoreCase(listing.subjectType()) || !PostItemSupport.isPostItem(listing.metadata())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post item not found");
        }
        return listing;
    }

    private PostItemView toItemView(PostKind postKind, String postId, MarketEntity market, ListingEntity listing, OrderEntity latestOrder, String latestPaymentStatus, boolean includeAgent) {
        OrderEntity activeOrder = activeOrder(latestOrder);
        Integer reservedShares = latestOrder == null ? null : PostItemSupport.metadataInt(latestOrder.metadata(), "reservedShares");
        boolean activeOrderPaymentRequired = activeOrder != null && activeOrder.settlementType() == SettlementType.MONEY;
        Integer rewardPreview = postKind == PostKind.PROJECT
                ? (reservedShares != null && reservedShares > 0
                ? reservedShares
                : projectRewardPreview(postId, listing))
                : null;
        PostItemView view = new PostItemView(
                listing.id(),
                postKind.name().toLowerCase(Locale.ROOT),
                postId,
                listing.marketId(),
                listing.title(),
                PostItemSupport.summary(listing.metadata()),
                listing.deliverableSpec(),
                listing.proofSpec(),
                PostItemSupport.acceptanceCriteria(listing.metadata()),
                PostItemSupport.itemKind(listing.metadata()),
                PostItemSupport.fulfillmentMode(listing.metadata()),
                PostItemSupport.deliveryMode(listing.metadata()),
                PostItemSupport.deliverySource(listing.metadata()),
                value(listing.metadata(), "deliveryProvider", null),
                value(listing.metadata(), "deliverySlaLabel", null),
                value(listing.metadata(), "deliveryFailurePolicy", null),
                PostItemSupport.buyerNotePlaceholder(listing.metadata()),
                PostItemSupport.agentInstruction(listing.metadata()),
                PostItemSupport.priority(listing.metadata()),
                listing.settlementType().name().toLowerCase(Locale.ROOT),
                PostItemSupport.metadataAmount(listing.metadata(), "priceAmount"),
                PostItemSupport.metadataAmount(listing.metadata(), "budgetAmount"),
                value(listing.metadata(), "currency", "SHARES"),
                value(listing.metadata(), "paymentMethod", "shares"),
                value(listing.metadata(), "paymentNetwork", null),
                value(listing.metadata(), "paymentRecipient", null),
                PostItemSupport.difficultyScore(listing.metadata()),
                rewardPreview,
                reservedShares,
                listing.inventoryLimit(),
                listing.activeOrdersCount(),
                deriveStatus(listing, latestOrder, activeOrder),
                activeOrder == null ? null : activeOrder.claimedByAccountId(),
                activeOrder == null ? null : activeOrder.orderNo(),
                activeOrder == null ? null : activeOrder.displayPhase(),
                activeOrderPaymentRequired,
                activeOrderPaymentStatus(activeOrderPaymentRequired, latestPaymentStatus),
                activeOrder == null ? null : PostItemSupport.metadataInstant(activeOrder.metadata(), "lockExpiresAt"),
                activeOrder == null ? null : PostItemSupport.metadataInstant(activeOrder.metadata(), "nextProgressDueAt"),
                latestOrder == null ? listing.updatedAt() : latestOrder.updatedAt());
        if (!includeAgent) {
            return view;
        }
        // 中文注释：agent 能力需要结合当前账号和 post owner，显式请求时才补齐这组派生状态。
        String accountId = currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse(null);
        String ownerAccountId = ownerAccountId(postKind, postId);
        return view.withAgentState(
                agentResourceKeyFactory.postItem(listing.id()),
                agentCapabilityResolver.postItemCapabilities(postKind, listing, ownerAccountId, accountId),
                agentCapabilityResolver.postItemBlockedCapabilities(postKind, listing, ownerAccountId, accountId));
    }

    private String ownerAccountId(PostKind postKind, String postId) {
        return switch (postKind) {
            case OFFER ->
                    offerRepository.findById(postId).map(com.monopolyfun.modules.post.domain.OfferEntity::actorAccountId).orElse(null);
            case REQUEST ->
                    requestPostRepository.findById(postId).map(com.monopolyfun.modules.post.domain.RequestEntity::actorAccountId).orElse(null);
            case PROJECT -> projectRepository.findById(postId).map(ProjectEntity::ownerAccountId).orElse(null);
            case REVIEW -> null;
        };
    }

    private String activeOrderPaymentStatus(boolean paymentRequired, String paymentStatus) {
        if (!paymentRequired) {
            return null;
        }
        // 中文注释：请求详情页依赖这个快照区分已接单待付款和已付款待交付，避免把锁单直接展示成验收阶段。
        return paymentStatus == null
                ? "missing_payment_intent"
                : paymentStatus.toLowerCase(Locale.ROOT);
    }

    private OrderEntity activeOrder(OrderEntity latestOrder) {
        if (latestOrder == null) {
            return null;
        }
        return switch (latestOrder.status()) {
            case CLAIMED, DELIVERED, ACCEPTED_OPEN, DISPUTED -> latestOrder;
            case FINAL_ACCEPTED, FINAL_CLOSED -> null;
        };
    }

    private int projectRewardPreview(String projectId, ListingEntity listing) {
        var pool = projectSharePoolService.requireByProjectId(projectId);
        return Math.min(pool.rewardPreview(PostItemSupport.difficultyScore(listing.metadata())), pool.taskRemaining());
    }

    private String deriveStatus(ListingEntity listing, OrderEntity latestOrder, OrderEntity activeOrder) {
        if (listing.status() == ListingStatus.ARCHIVED) return "archived";
        if (listing.status() == ListingStatus.OPEN && listing.hasAvailableCapacity()) return "open";
        if (activeOrder == null) {
            if (latestOrder != null && latestOrder.status() == OrderStatus.FINAL_ACCEPTED) {
                return "completed";
            }
            if (latestOrder != null && latestOrder.status() == OrderStatus.FINAL_CLOSED && "timeout_release".equalsIgnoreCase(latestOrder.closedReason())) {
                return listing.status() == ListingStatus.OPEN ? "released" : "closed";
            }
            return listing.status() == ListingStatus.PAUSED ? "locked" : "open";
        }
        if (activeOrder.status() == OrderStatus.CLAIMED) {
            return "locked_waiting_progress".equals(activeOrder.displayPhase()) ? "locked" : "in_progress";
        }
        if (activeOrder.status() == OrderStatus.DELIVERED || activeOrder.status() == OrderStatus.DISPUTED) {
            return "in_review";
        }
        if (activeOrder.status() == OrderStatus.ACCEPTED_OPEN) {
            return "accepted_window_open";
        }
        return listing.status().name().toLowerCase(Locale.ROOT);
    }

    private ProjectSharesView buildSharesView(PostKind postKind, MarketEntity market) {
        if (postKind != PostKind.PROJECT) return null;
        // 中文注释：Project workspace 的 shares 展示从独立 pool 读取，避免 market 展示表继续承载发放预算。
        return projectSharePoolService.viewByMarketId(market.id());
    }

    private Object requirePostView(PostKind postKind, String postId) {
        return switch (postKind) {
            case OFFER -> offerRepository.findById(postId)
                    .map(offer -> com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicOffer(
                            offer,
                            accountRepository.findById(offer.actorAccountId()).map(account -> account.handle()).orElse(null)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
            case REQUEST -> requestPostRepository.findById(postId)
                    .map(request -> com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicRequest(
                            request,
                            accountRepository.findById(request.actorAccountId()).map(account -> account.handle()).orElse(null)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
            case PROJECT -> projectRepository.findById(postId)
                    .map(project -> {
                        // 中文注释：公开 workspace 是纯读取路径，使用批量读取接口避免缺失角色时触发初始化写入。
                        List<com.monopolyfun.modules.project.domain.ProjectRoleEntity> roles = organizationAuthorityService
                                .listProjectRolesByProjectIds(List.of(project.id()))
                                .getOrDefault(project.id(), List.of());
                        Map<String, String> roleHandles = accountRepository.findByIds(roles.stream()
                                        .map(role -> role.accountId())
                                        .filter(accountId -> accountId != null && !accountId.isBlank())
                                        .distinct()
                                        .toList()).stream()
                                .collect(Collectors.toMap(account -> account.id(), account -> account.handle()));
                        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProject(
                                project,
                                roles,
                                accountRepository.findById(project.ownerAccountId()).map(account -> account.handle()).orElse(null),
                                roleHandles);
                    })
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
            case REVIEW ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review post has no item workspace");
        };
    }

    private MarketEntity requirePostMarket(PostKind postKind, String postId) {
        return marketRepository.findById(PostItemSupport.marketIdForPost(postKind, postId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post market not found"));
    }

    private PostRef requirePostRef(String postNo) {
        if (postNo == null || postNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "post business number is required");
        }
        String normalized = postNo.trim();
        // 中文注释：公开 workspace 只接收业务编号，内部 id 在这里被收敛成领域实体主键。
        return offerRepository.findByOfferNo(normalized)
                .map(offer -> new PostRef(PostKind.OFFER, offer.id()))
                .or(() -> requestPostRepository.findByRequestNo(normalized).map(request -> new PostRef(PostKind.REQUEST, request.id())))
                .or(() -> findProjectByPublicNo(normalized).map(project -> new PostRef(PostKind.PROJECT, project.id())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private Optional<ProjectEntity> findProjectByPublicNo(String projectNo) {
        if (RootProjectService.ROOT_PROJECT_NO.equalsIgnoreCase(projectNo)) {
            // 中文注释：Root Project 的公开 workspace 入口使用 monopolyfun，内部继续读取 root 项目主键。
            return projectRepository.findRootProject();
        }
        return projectRepository.findByProjectNo(projectNo);
    }

    private String value(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private record PostRef(PostKind postKind, String postId) {
    }
}
