package com.monopolyfun.modules.post.service.command;

import com.monopolyfun.modules.digitalinventory.service.DigitalInventoryService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.command.PaymentService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.api.request.CreatePaymentIntentRequest;
import com.monopolyfun.modules.post.api.request.ClosePostRequest;
import com.monopolyfun.modules.post.api.request.CreatePostItemRequest;
import com.monopolyfun.modules.post.api.request.PublishPostItemRequest;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.OfferStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.domain.RequestStatus;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.PostItemInputDefaults;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.ProjectLifecycleService;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.risk.service.RiskAction;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.modules.work.service.OrderWorkItemPublisher;
import com.monopolyfun.platform.command.CommandContext;
import com.monopolyfun.platform.command.CommandKernel;
import com.monopolyfun.platform.command.CommandMetadata;
import com.monopolyfun.platform.command.CommandResult;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.error.ApiStatusException;
import com.monopolyfun.shared.id.BusinessIdService;
import com.monopolyfun.shared.id.BusinessIdType;
import com.monopolyfun.shared.id.BusinessIds;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class PostItemCommandService {
    private static final BigDecimal MIN_MONEY_AMOUNT = new BigDecimal("0.01");
    private static final double MIN_PROJECT_DIFFICULTY_SCORE = 0.5d;
    private static final double MAX_PROJECT_DIFFICULTY_SCORE = 8d;
    private static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final CommandKernel commandKernel;
    private final PostItemWorkspaceQueryService postItemWorkspaceQueryService;
    private final BusinessIdService businessIdService;
    private final ProjectLifecycleService projectLifecycleService;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final AccountRiskGuard accountRiskGuard;
    private final PaymentService paymentService;
    private final ProjectSharePoolService projectSharePoolService;
    private final DigitalInventoryService digitalInventoryService;
    private final OrderWorkItemPublisher orderWorkItemPublisher;

    public PostItemCommandService(
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            ListingRepository listingRepository,
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            CurrentAccountAccess currentAccountAccess,
            CommandKernel commandKernel,
            PostItemWorkspaceQueryService postItemWorkspaceQueryService,
            BusinessIdService businessIdService,
            ProjectLifecycleService projectLifecycleService,
            OrganizationAuthorityService organizationAuthorityService,
            AccountRiskGuard accountRiskGuard,
            PaymentService paymentService,
            ProjectSharePoolService projectSharePoolService,
            DigitalInventoryService digitalInventoryService,
            OrderWorkItemPublisher orderWorkItemPublisher) {
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.commandKernel = commandKernel;
        this.postItemWorkspaceQueryService = postItemWorkspaceQueryService;
        this.businessIdService = businessIdService;
        this.projectLifecycleService = projectLifecycleService;
        this.organizationAuthorityService = organizationAuthorityService;
        this.accountRiskGuard = accountRiskGuard;
        this.paymentService = paymentService;
        this.projectSharePoolService = projectSharePoolService;
        this.digitalInventoryService = digitalInventoryService;
        this.orderWorkItemPublisher = orderWorkItemPublisher;
    }

    public PostItemView createItem(String postNo, CreatePostItemRequest request) {
        return createItem(postNo, request, false);
    }

    public PostItemView createItem(String postNo, CreatePostItemRequest request, boolean includeAgent) {
        PostRef postRef = requirePostRef(postNo);
        PostKind postKind = postRef.postKind();
        String postId = postRef.postId();
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        accountRiskGuard.requireAllowed(request.actorAccountId(), RiskAction.CREATE_POST_ITEM);
        requirePostWritableByOwner(postKind, postId, request.actorAccountId());
        validateProjectItemInputs(postKind, request.amount(), request.agentInstruction(), request.difficultyScore(), request.quantity());
        MarketEntity market = requirePostMarket(postKind, postId);
        String itemKind = normalizeItemKind(postKind, request.itemType());
        String deliveryStandard = normalizeText(request.deliveryStandard(), 2_000, "deliveryStandard");
        List<String> acceptanceCriteria = normalizeAcceptanceCriteria(request.acceptanceCriteria(), deliveryStandard);
        ListingEntity listing = buildItemListing(
                postKind,
                postId,
                market,
                "listing-post-item-" + UUID.randomUUID(),
                normalizeText(request.name(), 120, "name"),
                normalizeOptionalItemDescription(request.description()),
                deliveryStandard,
                acceptanceSpec(acceptanceCriteria),
                acceptanceCriteria,
                itemKind,
                resolveFulfillmentMode(postKind, request.mode()),
                resolveDeliveryMode(postKind, request.mode()),
                resolveDeliverySource(postKind, request.mode()),
                defaultBuyerNotePlaceholder(itemKind),
                resolveAgentInstruction(postKind, request.agentInstruction()),
                "medium",
                resolveItemPriceAmount(postKind, request.amount()),
                resolveItemBudgetAmount(postKind, request.amount()),
                resolveCurrency(postKind, postId, null),
                resolvePaymentMethod(postKind, postId, null),
                resolvePaymentNetwork(postKind, postId, null),
                resolvePaymentRecipient(postKind, postId, null),
                resolveDifficultyScore(postKind, request.difficultyScore()),
                normalizeQuantity(postKind, request.quantity()),
                PostItemSupport.DEFAULT_LOCK_TIMEOUT_SECONDS,
                PostItemSupport.DEFAULT_PROGRESS_TIMEOUT_SECONDS,
                null,
                request.actorAccountId(),
                Instant.now());

        commandKernel.execute(new CommandMetadata("open_post_item", "listing", listing.id()), context -> {
            listingRepository.save(listing);
            if (postKind == PostKind.PROJECT) {
                projectLifecycleService.touchOwnerAction(postId, request.actorAccountId(), "open_project_item");
            }
            return new CommandResult(listing.id(), "post_item_opened", Map.of("itemId", listing.id(), "postId", postId), List.of());
        });
        return postItemWorkspaceQueryService.getItem(listing.id(), includeAgent);
    }

    public CommandReceipt claimItem(String itemId, String actorAccountId, String buyerNote, String paymentRecipient, Map<String, Object> deliveryInput) {
        return commandKernel.execute(new CommandMetadata("claim_post_item", "listing", itemId), context -> {
            currentAccountAccess.requireSameAccount(actorAccountId);
            accountRiskGuard.requireAllowed(actorAccountId, RiskAction.CLAIM_POST_ITEM);
            ListingEntity listing = postItemWorkspaceQueryService.requirePostItem(itemId);
            ListingEntity lockedListing = listingRepository.findByIdForUpdate(listing.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post item not found"));
            listing = lockedListing;
            PostKind postKind = PostItemSupport.postKind(listing.metadata());
            String postId = PostItemSupport.postId(listing.metadata());
            String ownerAccountId = requirePostClaimable(postKind, postId, listing, actorAccountId);
            // 中文注释：Project 支持 owner 自领任务推进，买卖型 offer/request 保持自交易拦截。
            if (isSelfClaimBlockedPostKind(postKind) && ownerAccountId.equals(actorAccountId)) {
                throw new ApiStatusException(
                        HttpStatus.FORBIDDEN,
                        "listing.claim.self_forbidden",
                        "Post owner cannot claim own item",
                        Map.of("itemId", listing.id(), "postId", postId));
            }
            if (postKind == PostKind.REVIEW) {
                throw new ApiStatusException(
                        HttpStatus.BAD_REQUEST,
                        "listing.claim.review_unsupported",
                        "Review task must be claimed through review task API",
                        Map.of("itemId", listing.id()));
            }
            requireNoActivePostItemOrder(postKind, listing.id(), actorAccountId);
            MarketEntity market = requirePostMarket(postKind, postId);
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions = new ArrayList<>();
            ListingEntity reservedListing = reserveListingCapacity(listing, context, transitions);
            String claimPaymentRecipient = normalizeClaimPaymentRecipient(postKind, reservedListing, paymentRecipient);

            Integer reservedShares = resolveReservedShares(postKind, postId, listing);
            if (postKind == PostKind.PROJECT && (reservedShares == null || reservedShares <= 0)) {
                throw new ApiStatusException(
                        HttpStatus.CONFLICT,
                        "listing.inventory.sold_out",
                        "Project share pool is exhausted",
                        Map.of("itemId", listing.id(), "postId", postId));
            }
            Integer reservedCurveSlot = postKind == PostKind.PROJECT ? projectSharePoolService.requireByProjectId(postId).nextCurveSlot() : null;

            Instant now = context.startedAt();
            String fulfillmentMode = PostItemSupport.fulfillmentMode(listing.metadata());
            boolean reviewedFulfillment = PostItemSupport.isReviewedFulfillment(listing.metadata());
            boolean moneySettlement = reservedListing.settlementType() == SettlementType.MONEY;
            Instant lockExpiresAt = reviewedFulfillment || moneySettlement
                    ? now.plusSeconds(PostItemSupport.lockTimeoutSeconds(listing.metadata()))
                    : null;
            Instant nextProgressDueAt = reviewedFulfillment
                    ? now.plusSeconds(PostItemSupport.progressTimeoutSeconds(listing.metadata()))
                    : null;
            Map<String, Object> orderMetadata = PostItemSupport.withOrderParticipants(PostItemSupport.createOrderMetadata(
                            postKind,
                            postId,
                            reservedListing.id(),
                            fulfillmentMode,
                            PostItemSupport.deliveryMode(listing.metadata()),
                            PostItemSupport.deliverySource(listing.metadata()),
                            normalizeOptionalText(buyerNote, 2_000),
                            reservedShares,
                            reservedCurveSlot,
                            postKind == PostKind.PROJECT ? PostItemSupport.difficultyScore(listing.metadata()) : null,
                            lockExpiresAt,
                            nextProgressDueAt),
                    postKind,
                    ownerAccountId,
                    actorAccountId);
            if (moneySettlement && postKind != PostKind.REQUEST && lockExpiresAt != null) {
                orderMetadata = new LinkedHashMap<>(orderMetadata);
                orderMetadata.put("paymentDueAt", lockExpiresAt.toString());
            }
            orderMetadata = withInstantFulfillmentMetadata(reservedListing, orderMetadata, deliveryInput);

            // 中文注释：订单内部主键和展示编号一起取号，交易链路只依赖这一处编号规则。
            BusinessIds orderIds = businessIdService.next(BusinessIdType.ORDER);
            String parentOrderId = null;
            var claimed = OrderEntity.claim(
                    orderIds.id(),
                    orderIds.displayNo(),
                    market.id(),
                    reservedListing.id(),
                    reservedListing.kind(),
                    postKind,
                    postId,
                    parentOrderId,
                    actorAccountId,
                    reservedListing.settlementType(),
                    orderSettlementAmount(postKind, reservedListing, reservedShares),
                    "nonce-" + UUID.randomUUID(),
                    PostItemSupport.acceptanceCriteria(reservedListing.metadata()),
                    reservedListing.proofSpec(),
                    reservedListing.settlementSpec(),
                    buildDeliverySnapshot(postKind, postId, reservedListing),
                    buildSettlementSnapshot(reservedListing, reservedShares, claimPaymentRecipient),
                    orderMetadata,
                    lifecycleContext(context, Map.of("postKind", postKind.name().toLowerCase(Locale.ROOT), "postId", postId, "itemId", reservedListing.id())));

            if (postKind == PostKind.PROJECT) {
                // 中文注释：任务领取只占用 project_share_pools.task_reserved，工资池和公开 market 保持独立。
                projectSharePoolService.reserveTask(postId, reservedShares);
            }
            OrderEntity nextOrder = claimed.entity();
            orderRepository.save(nextOrder);
            orderWorkItemPublisher.publishClaimedOrder(nextOrder, context.startedAt());
            if (PostItemSupport.isStockFulfillment(reservedListing.metadata())) {
                // 中文注释：库存发货在下单阶段锁定具体库存，付款成功后直接读取同一条库存完成交付。
                digitalInventoryService.reserveForOrder(reservedListing.id(), nextOrder.id(), actorAccountId, context.startedAt());
            }
            updatePostTradeAfterClaim(postKind, postId);
            transitions.add(claimed.transition());
            saveEvent(nextOrder.id(), "post_item_claimed", actorAccountId, Map.of(
                    "postKind", postKind.name().toLowerCase(Locale.ROOT),
                    "postId", postId,
                    "itemId", reservedListing.id(),
                    "label", "Item 已锁定并生成订单"));
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", nextOrder.orderNo());
            payload.put("postId", postId);
            payload.put("itemId", reservedListing.id());
            payload.put("paymentRequired", nextOrder.settlementType() == SettlementType.MONEY && postKind != PostKind.REQUEST);
            payload.put("paymentActorAccountId", nextOrder.buyerAccountId());
            if (claimPaymentRecipient != null) {
                payload.put("paymentRecipient", claimPaymentRecipient);
            }
            if (nextOrder.settlementType() == SettlementType.MONEY && actorAccountId.equals(nextOrder.buyerAccountId())) {
                // 中文注释：offer 购买人就是付款人，claim 成功后立刻生成支付会话，订单从第一步就进入付款优先状态。
                var paymentIntent = paymentService.createIntent(nextOrder.orderNo(), new CreatePaymentIntentRequest(actorAccountId, null, Map.of(), false, Map.of()));
                payload.put("paymentIntentId", paymentIntent.paymentIntent().id());
                payload.put("paymentIntentStatus", paymentIntent.paymentIntent().status());
                if (paymentIntent.checkoutUrl() != null) {
                    payload.put("checkoutUrl", paymentIntent.checkoutUrl());
                }
            }
            return new CommandResult(
                    nextOrder.orderNo(),
                    nextOrder.status().name(),
                    Map.copyOf(payload),
                    transitions);
        });
    }

    public PostItemView updateItem(String itemId, CreatePostItemRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        ListingEntity current = postItemWorkspaceQueryService.requirePostItem(itemId);
        if (current.status() == ListingStatus.CLOSED || current.status() == ListingStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post item is closed");
        }
        PostKind postKind = PostItemSupport.postKind(current.metadata());
        String postId = PostItemSupport.postId(current.metadata());
        requirePostWritableByOwner(postKind, postId, request.actorAccountId());
        validateProjectItemInputs(postKind, request.amount(), request.agentInstruction(), request.difficultyScore(), request.quantity());
        MarketEntity market = requirePostMarket(postKind, postId);
        String itemKind = normalizeItemKind(postKind, request.itemType());
        String deliveryStandard = normalizeText(request.deliveryStandard(), 2_000, "deliveryStandard");
        List<String> acceptanceCriteria = normalizeAcceptanceCriteria(request.acceptanceCriteria(), deliveryStandard);
        ListingEntity rebuilt = buildItemListing(
                postKind,
                postId,
                market,
                current.id(),
                normalizeText(request.name(), 120, "name"),
                normalizeOptionalItemDescription(request.description()),
                deliveryStandard,
                acceptanceSpec(acceptanceCriteria),
                acceptanceCriteria,
                itemKind,
                resolveFulfillmentMode(postKind, request.mode()),
                resolveDeliveryMode(postKind, request.mode()),
                resolveDeliverySource(postKind, request.mode()),
                defaultBuyerNotePlaceholder(itemKind),
                resolveAgentInstruction(postKind, request.agentInstruction()),
                "medium",
                resolveItemPriceAmount(postKind, request.amount()),
                resolveItemBudgetAmount(postKind, request.amount()),
                resolveCurrency(postKind, postId, null),
                resolvePaymentMethod(postKind, postId, null),
                resolvePaymentNetwork(postKind, postId, null),
                resolvePaymentRecipient(postKind, postId, null),
                resolveDifficultyScore(postKind, request.difficultyScore()),
                Math.max(current.activeOrdersCount(), normalizeQuantity(postKind, request.quantity())),
                PostItemSupport.lockTimeoutSeconds(current.metadata()),
                PostItemSupport.progressTimeoutSeconds(current.metadata()),
                PostItemSupport.metadataInt(current.metadata(), "publishedItemPosition"),
                current.openedByAccountId(),
                current.createdAt());
        // 中文注释：更新 item 时保留当前容量和状态，避免编辑文案时意外释放或重开已有订单。
        ListingEntity persisted = new ListingEntity(
                rebuilt.id(),
                rebuilt.marketId(),
                rebuilt.kind(),
                rebuilt.parentOrderId(),
                rebuilt.title(),
                rebuilt.subjectType(),
                rebuilt.subjectRef(),
                rebuilt.deliverableSpec(),
                rebuilt.proofSpec(),
                rebuilt.settlementSpec(),
                rebuilt.inventoryLimit(),
                current.activeOrdersCount(),
                Math.max(rebuilt.stockTotal(), current.activeOrdersCount()),
                rebuilt.settlementType(),
                current.status(),
                rebuilt.openedByAccountId(),
                rebuilt.metadata(),
                current.createdAt(),
                Instant.now());
        commandKernel.execute(new CommandMetadata("update_post_item", "listing", itemId), context -> {
            listingRepository.save(persisted);
            if (postKind == PostKind.PROJECT) {
                projectLifecycleService.touchOwnerAction(postId, request.actorAccountId(), "update_project_item");
            }
            return new CommandResult(itemId, "post_item_updated", Map.of("itemId", itemId, "postId", postId), List.of());
        });
        return postItemWorkspaceQueryService.getItem(itemId);
    }

    public PostItemView closeItem(String itemId, ClosePostRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        commandKernel.execute(new CommandMetadata("close_post_item", "listing", itemId), context -> {
            ListingEntity listing = postItemWorkspaceQueryService.requirePostItem(itemId);
            PostKind postKind = PostItemSupport.postKind(listing.metadata());
            String postId = PostItemSupport.postId(listing.metadata());
            requirePostWritableByOwner(postKind, postId, request.actorAccountId());
            if (listing.status() == ListingStatus.CLOSED || listing.status() == ListingStatus.ARCHIVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Post item is closed");
            }
            String closeReason = normalizeOptionalText(request.reason(), 500) == null ? "owner_closed" : normalizeOptionalText(request.reason(), 500);
            // 中文注释：关闭 item 只停止新的领取动作，已有 order 继续沿订单生命周期履约、验收和结算。
            var closed = listing.close(lifecycleContext(context, Map.of(
                    "postKind", postKind.name().toLowerCase(Locale.ROOT),
                    "postId", postId,
                    "itemId", itemId,
                    "reason", closeReason)));
            listingRepository.save(closed.entity());
            return new CommandResult(itemId, "post_item_closed", Map.of("itemId", itemId, "postId", postId), List.of(closed.transition()));
        });
        return postItemWorkspaceQueryService.getItem(itemId);
    }

    public ListingEntity buildPostItemForPublish(
            PostKind postKind,
            String postId,
            MarketEntity market,
            String actorAccountId,
            PublishPostItemRequest request,
            Instant now,
            int publishedItemPosition) {
        // 中文注释：Project item 由 owner 定义任务和难度分，shares 价格由领取时的 curve 统一计算。
        validateProjectItemInputs(postKind, request.amount(), request.agentInstruction(), request.difficultyScore(), request.quantity());
        // 中文注释：发布入口直接写入履约模式，直接发货 item 从首屏创建开始具备完整 order 快照。
        String itemKind = normalizeItemKind(postKind, null);
        String mode = request.mode();
        String deliveryStandard = normalizeText(request.deliveryStandard(), 2_000, "deliveryStandard");
        List<String> acceptanceCriteria = normalizeAcceptanceCriteria(request.acceptanceCriteria(), deliveryStandard);
        return buildItemListing(
                postKind,
                postId,
                market,
                "listing-post-item-" + UUID.randomUUID(),
                normalizeText(request.name(), 120, "name"),
                normalizeOptionalItemDescription(request.description()),
                deliveryStandard,
                acceptanceSpec(acceptanceCriteria),
                acceptanceCriteria,
                itemKind,
                resolveFulfillmentMode(postKind, mode),
                resolveDeliveryMode(postKind, mode),
                resolveDeliverySource(postKind, mode),
                defaultBuyerNotePlaceholder(itemKind),
                resolveAgentInstruction(postKind, request.agentInstruction()),
                "medium",
                resolveItemPriceAmount(postKind, request.amount()),
                resolveItemBudgetAmount(postKind, request.amount()),
                resolveCurrency(postKind, postId, null),
                resolvePaymentMethod(postKind, postId, null),
                resolvePaymentNetwork(postKind, postId, null),
                resolvePaymentRecipient(postKind, postId, null),
                resolveDifficultyScore(postKind, request.difficultyScore()),
                normalizeQuantity(postKind, request.quantity()),
                PostItemSupport.DEFAULT_LOCK_TIMEOUT_SECONDS,
                PostItemSupport.DEFAULT_PROGRESS_TIMEOUT_SECONDS,
                publishedItemPosition,
                actorAccountId,
                now);
    }

    private ListingEntity buildItemListing(
            PostKind postKind,
            String postId,
            MarketEntity market,
            String listingId,
            String title,
            String summary,
            String deliverableSpec,
            String acceptanceSpec,
            List<String> acceptanceCriteria,
            String itemKind,
            String fulfillmentMode,
            String deliveryMode,
            String deliverySource,
            String buyerNotePlaceholder,
            String agentInstruction,
            String priority,
            BigDecimal priceAmount,
            BigDecimal budgetAmount,
            String currency,
            String paymentMethod,
            String paymentNetwork,
            String paymentRecipient,
            double difficultyScore,
            int seatCount,
            int lockTimeoutSeconds,
            int progressTimeoutSeconds,
            Integer publishedItemPosition,
            String actorAccountId,
            Instant now) {
        SettlementType settlementType = resolveSettlementType(postKind, priceAmount, budgetAmount, paymentMethod);
        Map<String, Object> metadata = PostItemSupport.createItemMetadata(
                postKind,
                postId,
                summary,
                itemKind,
                fulfillmentMode,
                deliveryMode,
                deliverySource,
                buyerNotePlaceholder == null || buyerNotePlaceholder.isBlank() ? defaultBuyerNotePlaceholder(itemKind) : buyerNotePlaceholder,
                agentInstruction,
                acceptanceCriteria,
                priority,
                priceAmount,
                budgetAmount,
                currency,
                paymentMethod,
                paymentNetwork,
                paymentRecipient,
                difficultyScore,
                lockTimeoutSeconds,
                progressTimeoutSeconds);
        applyInstantFulfillmentDefaults(metadata);
        if (publishedItemPosition != null) {
            // 中文注释：批量发布时保留用户填写顺序，workspace 在相同更新时间下用它稳定排序。
            metadata.put("publishedItemPosition", publishedItemPosition);
        }
        // 中文注释：PostItem 继续落在 listing 表，listing 只负责锁单和容量，用户侧稳定暴露 item 语义。
        return new ListingEntity(
                listingId,
                market.id(),
                ListingKind.WORK,
                null,
                title,
                PostItemSupport.SUBJECT_TYPE,
                listingId,
                deliverableSpec,
                acceptanceSpec,
                settlementLabel(postKind, settlementType, priceAmount, budgetAmount, currency, paymentMethod),
                seatCount,
                0,
                seatCount,
                settlementType,
                ListingStatus.OPEN,
                actorAccountId,
                metadata,
                now,
                now);
    }

    private ListingEntity reserveListingCapacity(
            ListingEntity listing,
            CommandContext context,
            List<com.monopolyfun.platform.lifecycle.LifecycleTransition<?, ?>> transitions) {
        if (listing.status() == ListingStatus.CLOSED || listing.status() == ListingStatus.ARCHIVED) {
            throw new ApiStatusException(
                    HttpStatus.CONFLICT,
                    "listing.already_closed",
                    "Post item is closed",
                    Map.of("itemId", listing.id()));
        }
        if (!listing.hasAvailableCapacity()) {
            throw new ApiStatusException(
                    HttpStatus.CONFLICT,
                    "listing.inventory.sold_out",
                    "Post item capacity is full",
                    Map.of("itemId", listing.id(), "inventoryLimit", listing.inventoryLimit(), "activeOrdersCount", listing.activeOrdersCount()));
        }
        ListingEntity reserved = listing.withActiveOrdersCount(listing.activeOrdersCount() + 1);
        if (reserved.hasAvailableCapacity()) {
            listingRepository.save(reserved);
            return reserved;
        }
        var paused = reserved.pause(lifecycleContext(context, Map.of("itemId", listing.id(), "reason", "capacity_full")));
        listingRepository.save(paused.entity());
        transitions.add(paused.transition());
        return paused.entity();
    }

    private Integer resolveReservedShares(PostKind postKind, String projectId, ListingEntity listing) {
        if (postKind != PostKind.PROJECT) return null;
        var pool = projectSharePoolService.requireByProjectId(projectId);
        return Math.min(
                pool.rewardPreview(PostItemSupport.difficultyScore(listing.metadata())),
                pool.taskRemaining());
    }

    private BigDecimal settlementAmount(ListingEntity listing) {
        BigDecimal priceAmount = PostItemSupport.metadataAmount(listing.metadata(), "priceAmount");
        if (priceAmount != null) return priceAmount;
        return PostItemSupport.metadataAmount(listing.metadata(), "budgetAmount");
    }

    private BigDecimal orderSettlementAmount(PostKind postKind, ListingEntity listing, Integer reservedShares) {
        if (postKind == PostKind.PROJECT) return BigDecimal.valueOf(reservedShares);
        return settlementAmount(listing);
    }

    private boolean isSelfClaimBlockedPostKind(PostKind postKind) {
        return postKind == PostKind.OFFER || postKind == PostKind.REQUEST;
    }

    private void requireNoActivePostItemOrder(PostKind postKind, String listingId, String actorAccountId) {
        if (postKind != PostKind.OFFER && postKind != PostKind.REQUEST) {
            return;
        }
        orderRepository.findActiveByListingIdAndClaimedByAccountId(listingId, actorAccountId)
                .ifPresent(order -> {
                    throw new ApiStatusException(
                            HttpStatus.CONFLICT,
                            "order.active_for_account",
                            "Post item already has an active order for this account",
                            Map.of("orderNo", order.orderNo()));
                });
    }

    private void updatePostTradeAfterClaim(PostKind postKind, String postId) {
        if (postKind == PostKind.PROJECT) {
            return;
        }
        boolean hasOpenItems = listPostItems(postKind, postId).stream()
                .anyMatch(item -> item.status() == ListingStatus.OPEN && item.hasAvailableCapacity());
        // 中文注释：成交数量每次 claim 都要累加，只有最后一个可交易 item 被占用后才把 Post 收窄为参与方可见。
        boolean closePost = !hasOpenItems;
        if (postKind == PostKind.OFFER) {
            offerRepository.findById(postId).ifPresent(offer -> offerRepository.save(new OfferEntity(
                    offer.id(),
                    offer.offerNo(),
                    offer.actorAccountId(),
                    offer.title(),
                    offer.description(),
                    offer.deliveryStandard(),
                    offer.priceAmount(),
                    offer.currency(),
                    offer.paymentMethod(),
                    offer.paymentProfile(),
                    offer.paymentNetwork(),
                    offer.paymentAsset(),
                    offer.paymentRecipient(),
                    offer.inventoryPolicy(),
                    offer.stockTotal(),
                    offer.stockSold() + 1,
                    closePost ? OfferStatus.CLOSED : offer.status(),
                    postTradeMetadata(offer.metadata(), closePost, "sold_out"),
                    offer.createdAt(),
                    Instant.now())));
            return;
        }
        requestPostRepository.findById(postId).ifPresent(request -> requestPostRepository.save(new RequestEntity(
                request.id(),
                request.requestNo(),
                request.actorAccountId(),
                request.title(),
                request.description(),
                request.deliveryStandard(),
                request.budgetAmount(),
                request.currency(),
                request.paymentMethod(),
                request.paymentProfile(),
                request.paymentNetwork(),
                request.paymentAsset(),
                request.paymentRecipient(),
                request.inventoryPolicy(),
                request.stockTotal(),
                request.stockFilled() + 1,
                closePost ? RequestStatus.CLOSED : request.status(),
                request.deadlineAt(),
                postTradeMetadata(request.metadata(), closePost, "matched"),
                request.createdAt(),
                Instant.now())));
    }

    private List<ListingEntity> listPostItems(PostKind postKind, String postId) {
        return listingRepository.findByMarketId(PostItemSupport.marketIdForPost(postKind, postId)).stream()
                .filter(listing -> listing.kind() == ListingKind.WORK
                        && PostItemSupport.SUBJECT_TYPE.equalsIgnoreCase(listing.subjectType())
                        && postKind == PostItemSupport.postKind(listing.metadata())
                        && postId.equals(PostItemSupport.postId(listing.metadata())))
                .toList();
    }

    private Map<String, Object> postTradeMetadata(Map<String, Object> current, boolean closePost, String closedTradeStatus) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(current == null ? Map.of() : current);
        if (closePost) {
            // 中文注释：售罄后主状态和展示可见性同步收口，详情页与列表页都读取同一闭环结果。
            metadata.put("tradeStatus", closedTradeStatus);
            metadata.put("visibility", "participant_only");
        }
        return metadata;
    }

    private Map<String, Object> buildDeliverySnapshot(PostKind postKind, String postId, ListingEntity listing) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("postKind", postKind.name().toLowerCase(Locale.ROOT));
        snapshot.put("postId", postId);
        snapshot.put("itemId", listing.id());
        snapshot.put("title", listing.title());
        snapshot.put("summary", PostItemSupport.summary(listing.metadata()));
        snapshot.put("deliverableSpec", listing.deliverableSpec());
        snapshot.put("acceptanceSpec", listing.proofSpec());
        snapshot.put("acceptanceCriteria", PostItemSupport.acceptanceCriteria(listing.metadata()));
        snapshot.put("deliveryMode", PostItemSupport.deliveryMode(listing.metadata()));
        snapshot.put("deliverySource", PostItemSupport.deliverySource(listing.metadata()));
        putIfPresent(snapshot, "deliveryProvider", stringValue(listing.metadata().get("deliveryProvider")));
        if (listing.metadata().get("deliveryInputSchema") != null)
            snapshot.put("deliveryInputSchema", listing.metadata().get("deliveryInputSchema"));
        putIfPresent(snapshot, "deliverySlaLabel", stringValue(listing.metadata().get("deliverySlaLabel")));
        putIfPresent(snapshot, "deliveryFailurePolicy", stringValue(listing.metadata().get("deliveryFailurePolicy")));
        snapshot.put("buyerNotePlaceholder", PostItemSupport.buyerNotePlaceholder(listing.metadata()));
        snapshot.put("agentInstruction", PostItemSupport.agentInstruction(listing.metadata()));
        return snapshot;
    }

    private Map<String, Object> buildSettlementSnapshot(ListingEntity listing, Integer reservedShares, String paymentRecipientOverride) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("summary", listing.settlementSpec());
        snapshot.put("settlementType", listing.settlementType().name().toLowerCase(Locale.ROOT));
        if (reservedShares != null) snapshot.put("reservedShares", reservedShares);
        if (reservedShares != null) {
            snapshot.put("rewardSharesPreview", reservedShares);
            snapshot.put("difficultyScore", PostItemSupport.difficultyScore(listing.metadata()));
        }
        putIfPresent(snapshot, "currency", stringValue(listing.metadata().get("currency")));
        putIfPresent(snapshot, "paymentMethod", stringValue(listing.metadata().get("paymentMethod")));
        putIfPresent(snapshot, "paymentNetwork", stringValue(listing.metadata().get("paymentNetwork")));
        putIfPresent(snapshot, "paymentRecipient", paymentRecipientOverride == null ? stringValue(listing.metadata().get("paymentRecipient")) : paymentRecipientOverride);
        BigDecimal priceAmount = PostItemSupport.metadataAmount(listing.metadata(), "priceAmount");
        BigDecimal budgetAmount = PostItemSupport.metadataAmount(listing.metadata(), "budgetAmount");
        if (priceAmount != null) snapshot.put("priceAmount", priceAmount);
        if (budgetAmount != null) snapshot.put("budgetAmount", budgetAmount);
        return snapshot;
    }

    private String requirePostWritableByOwner(PostKind postKind, String postId, String actorAccountId) {
        return switch (postKind) {
            case OFFER -> {
                OfferEntity offer = offerRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
                if (!offer.actorAccountId().equals(actorAccountId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner can create items");
                }
                if (offer.status() != OfferStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer is not open");
                }
                yield offer.actorAccountId();
            }
            case REQUEST -> {
                RequestEntity request = requestPostRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
                if (!request.actorAccountId().equals(actorAccountId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner can create items");
                }
                if (request.status() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not open");
                }
                yield request.actorAccountId();
            }
            case PROJECT -> {
                ProjectEntity project = projectRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
                // 中文注释：项目发布需求走项目能力判定，owner 字段保留为项目活跃负责人。
                organizationAuthorityService.requireProjectCapability(actorAccountId, project.id(), ProjectCapability.MARKET_QUALITY_MANAGE);
                if (project.status() != ProjectStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not active");
                }
                yield project.ownerAccountId();
            }
            case REVIEW -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review post cannot create items");
        };
    }

    private String requirePostClaimable(PostKind postKind, String postId, ListingEntity listing, String actorAccountId) {
        return switch (postKind) {
            case OFFER -> {
                OfferEntity offer = offerRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
                if (offer.status() != OfferStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer is not open");
                }
                yield offer.actorAccountId();
            }
            case REQUEST -> {
                RequestEntity request = requestPostRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
                if (request.status() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not open");
                }
                yield request.actorAccountId();
            }
            case PROJECT -> {
                ProjectEntity project = projectRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
                if (project.status() != ProjectStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not active");
                }
                yield project.ownerAccountId();
            }
            case REVIEW ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review task must be claimed through review task API");
        };
    }

    private String normalizeClaimPaymentRecipient(PostKind postKind, ListingEntity listing, String paymentRecipient) {
        if (listing.settlementType() != SettlementType.MONEY) {
            return null;
        }
        if (postKind == PostKind.REQUEST) {
            String recipient = normalizeEvmRecipient(paymentRecipient, "Request 接单需要执行人 OKX 收款钱包");
            // 中文注释：request 的卖方在接单时才确定，收款钱包写入订单快照后再允许买家付款。
            return recipient;
        }
        String recipient = stringValue(listing.metadata().get("paymentRecipient"));
        return normalizeEvmRecipient(recipient, "OKX Direct Pay requires seller paymentRecipient");
    }

    private String normalizeEvmRecipient(String value, String errorMessage) {
        String recipient = normalizeOptionalText(value, 80);
        if (recipient == null || !EVM_ADDRESS_PATTERN.matcher(recipient).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return recipient;
    }

    private MarketEntity requirePostMarket(PostKind postKind, String postId) {
        return marketRepository.findById(PostItemSupport.marketIdForPost(postKind, postId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post market not found"));
    }

    private String resolveCurrency(PostKind postKind, String postId, String value) {
        if (value != null && !value.isBlank()) return value.trim().toUpperCase(Locale.ROOT);
        return switch (postKind) {
            case OFFER -> offerRepository.findById(postId)
                    .map(offer -> "shares".equalsIgnoreCase(offer.paymentMethod()) ? "SHARES" : offer.currency())
                    .orElse("SHARES");
            case REQUEST -> requestPostRepository.findById(postId)
                    .map(request -> "shares".equalsIgnoreCase(request.paymentMethod()) ? "SHARES" : request.currency())
                    .orElse("SHARES");
            case PROJECT, REVIEW -> "SHARES";
        };
    }

    private String resolvePaymentMethod(PostKind postKind, String postId, String value) {
        if (value != null && !value.isBlank()) return value.trim();
        return switch (postKind) {
            case OFFER ->
                    offerRepository.findById(postId).map(OfferEntity::paymentMethod).filter(method -> !method.isBlank()).orElse("okx_direct_pay");
            case REQUEST ->
                    requestPostRepository.findById(postId).map(RequestEntity::paymentMethod).filter(method -> !method.isBlank()).orElse("okx_direct_pay");
            case PROJECT, REVIEW -> "shares";
        };
    }

    private String resolvePaymentNetwork(PostKind postKind, String postId, String value) {
        if (value != null && !value.isBlank()) return value.trim();
        return switch (postKind) {
            case OFFER -> offerRepository.findById(postId).map(OfferEntity::paymentNetwork).orElse(null);
            case REQUEST -> requestPostRepository.findById(postId).map(RequestEntity::paymentNetwork).orElse(null);
            case PROJECT, REVIEW -> null;
        };
    }

    private String resolvePaymentRecipient(PostKind postKind, String postId, String value) {
        if (value != null && !value.isBlank()) return value.trim();
        return switch (postKind) {
            case OFFER -> offerRepository.findById(postId).map(OfferEntity::paymentRecipient).orElse(null);
            case REQUEST -> requestPostRepository.findById(postId).map(RequestEntity::paymentRecipient).orElse(null);
            case PROJECT, REVIEW -> null;
        };
    }

    private SettlementType resolveSettlementType(PostKind postKind, BigDecimal priceAmount, BigDecimal budgetAmount, String paymentMethod) {
        if ("shares".equalsIgnoreCase(paymentMethod)) {
            return SettlementType.SHARES;
        }
        return switch (postKind) {
            case PROJECT -> SettlementType.SHARES;
            case REQUEST -> SettlementType.MONEY;
            case OFFER -> SettlementType.MONEY;
            case REVIEW -> SettlementType.SHARES;
        };
    }

    private String settlementLabel(PostKind postKind, SettlementType settlementType, BigDecimal priceAmount, BigDecimal budgetAmount, String currency, String paymentMethod) {
        if (postKind == PostKind.PROJECT) return "accepted work item -> mint reserved project shares";
        if (budgetAmount != null) return moneyText(budgetAmount) + " " + currency + " · " + paymentMethod;
        if (priceAmount != null) return moneyText(priceAmount) + " " + currency + " · " + paymentMethod;
        return settlementType.name().toLowerCase(Locale.ROOT) + " settlement";
    }

    private BigDecimal resolveItemPriceAmount(PostKind postKind, BigDecimal amount) {
        if (postKind != PostKind.OFFER) return null;
        return requirePositiveAmount(amount);
    }

    private BigDecimal resolveItemBudgetAmount(PostKind postKind, BigDecimal amount) {
        if (postKind != PostKind.REQUEST) return null;
        return requirePositiveAmount(amount);
    }

    private double resolveDifficultyScore(PostKind postKind, Double difficultyScore) {
        if (postKind != PostKind.PROJECT) return 1d;
        if (difficultyScore == null) return PostItemSupport.DEFAULT_PROJECT_DIFFICULTY_SCORE;
        if (!Double.isFinite(difficultyScore) || difficultyScore < MIN_PROJECT_DIFFICULTY_SCORE || difficultyScore > MAX_PROJECT_DIFFICULTY_SCORE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "difficultyScore must be between " + MIN_PROJECT_DIFFICULTY_SCORE + " and " + MAX_PROJECT_DIFFICULTY_SCORE);
        }
        return difficultyScore;
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_MONEY_AMOUNT) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be at least 0.01");
        }
        // 中文注释：公开买卖金额固定到分，后续 amountMinor 可稳定换算给支付 provider。
        return amount.stripTrailingZeros();
    }

    private List<String> normalizeAcceptanceCriteria(List<String> values, String fallbackSpec) {
        List<String> criteria = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> normalizeText(value, 500, "acceptanceCriteria"))
                .distinct()
                .toList();
        if (!criteria.isEmpty()) {
            return criteria;
        }
        // 中文注释：旧入口只填写 deliveryStandard，这里把它转成首条验收标准，保证争议评审仍有可引用的 criteria。
        return List.of(normalizeText(fallbackSpec, 500, "deliveryStandard"));
    }

    private String acceptanceSpec(List<String> criteria) {
        return String.join("\n", criteria);
    }

    private void validateProjectItemInputs(PostKind postKind, BigDecimal amount, String agentInstruction, Double difficultyScore, Integer quantity) {
        if (postKind != PostKind.PROJECT) {
            if (difficultyScore != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "difficultyScore is only supported for Project items");
            }
            return;
        }
        // 中文注释：Project item 只允许 owner 调整难度分，shares 释放数量仍由 curve 在领取时统一计算。
        if (amount != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project item amount is system-priced and must not be provided");
        }
        if (agentInstruction != null && !agentInstruction.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project item agentInstruction is derived from deliveryStandard and must not be provided");
        }
        if (quantity != null && quantity != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project item quantity must be 1");
        }
        resolveDifficultyScore(postKind, difficultyScore);
    }

    private String resolveAgentInstruction(PostKind postKind, String agentInstruction) {
        return postKind == PostKind.PROJECT ? null : normalizeOptionalText(agentInstruction, 1_000);
    }

    private String moneyText(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String normalizeItemKind(PostKind postKind, String itemType) {
        if (postKind == PostKind.PROJECT) {
            if (itemType == null || itemType.isBlank()) return "normal";
            return switch (itemType.trim().toLowerCase(Locale.ROOT)) {
                // 中文注释：项目任务类型按用户看到的业务类型保存，旧 dev/research/ops 数据在展示层归入普通任务。
                case "normal", "bug", "review", "dispute" -> itemType.trim().toLowerCase(Locale.ROOT);
                case "dev", "growth", "research", "ops" -> "normal";
                default ->
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemType must be normal, bug, review, or dispute");
            };
        }
        return switch (postKind) {
            case OFFER -> "product";
            case REQUEST, PROJECT -> "work";
            case REVIEW -> "review";
        };
    }

    private String normalizeMode(PostKind postKind, String mode) {
        if (mode == null || mode.isBlank()) {
            return PostItemSupport.DELIVERY_MODE_REVIEWED;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        // 中文注释：Post item 只接受最终交付模式，避免发布端再产生人工、自动、即时等旧分叉语义。
        return switch (normalized) {
            case PostItemSupport.DELIVERY_MODE_REVIEWED, PostItemSupport.DELIVERY_MODE_INSTANT,
                 PostItemSupport.DELIVERY_MODE_STOCK -> normalized;
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be reviewed_delivery, instant_fulfillment, or stock_fulfillment");
        };
    }

    private void applyInstantFulfillmentDefaults(Map<String, Object> metadata) {
        if (!PostItemSupport.DELIVERY_MODE_INSTANT.equalsIgnoreCase(String.valueOf(metadata.get("deliveryMode")))) {
            return;
        }
        // 中文注释：直接发货首版固定使用 phone_recharge schema，购买表单和 provider 校验读取同一份字段约束。
        metadata.put("deliveryProvider", "phone_recharge");
        metadata.put("deliverySlaLabel", "1-3 分钟到账");
        metadata.put("deliveryFailurePolicy", "发货失败后可重试或退款");
        metadata.put("deliveryInputSchema", Map.of(
                "phone", Map.of(
                        "type", "string",
                        "label", "手机号",
                        "required", true,
                        "maskInPublic", true,
                        "pattern", "^1[3-9]\\d{9}$"),
                "amount", Map.of(
                        "type", "number",
                        "label", "充值金额",
                        "required", true,
                        "readonly", true)));
    }

    private Map<String, Object> withInstantFulfillmentMetadata(ListingEntity listing, Map<String, Object> orderMetadata, Map<String, Object> deliveryInput) {
        if (!PostItemSupport.isInstantFulfillment(listing.metadata())) {
            return orderMetadata;
        }
        LinkedHashMap<String, Object> next = new LinkedHashMap<>(orderMetadata);
        Map<String, Object> input = normalizeDeliveryInput(deliveryInput);
        validateInstantFulfillmentInput(input);
        next.put("deliveryInput", input);
        next.put("deliveryProvider", stringValue(listing.metadata().getOrDefault("deliveryProvider", "phone_recharge")));
        next.put("deliveryInputSchema", listing.metadata().getOrDefault("deliveryInputSchema", Map.of()));
        next.put("deliverySlaLabel", stringValue(listing.metadata().getOrDefault("deliverySlaLabel", "1-3 分钟到账")));
        next.put("deliveryFailurePolicy", stringValue(listing.metadata().getOrDefault("deliveryFailurePolicy", "发货失败后可重试或退款")));
        return next;
    }

    private Map<String, Object> normalizeDeliveryInput(Map<String, Object> deliveryInput) {
        if (deliveryInput == null || deliveryInput.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instant fulfillment requires deliveryInput");
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (var entry : deliveryInput.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalized.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return normalized;
    }

    private void validateInstantFulfillmentInput(Map<String, Object> input) {
        String phone = stringValue(input.get("phone"));
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
    }

    private String resolveFulfillmentMode(PostKind postKind, String mode) {
        String normalized = normalizeMode(postKind, mode);
        return switch (normalized) {
            case PostItemSupport.DELIVERY_MODE_INSTANT -> PostItemSupport.FULFILLMENT_MODE_INSTANT;
            case PostItemSupport.DELIVERY_MODE_STOCK -> PostItemSupport.FULFILLMENT_MODE_STOCK;
            default -> PostItemSupport.FULFILLMENT_MODE_REVIEWED;
        };
    }

    private String resolveDeliveryMode(PostKind postKind, String mode) {
        return normalizeMode(postKind, mode);
    }

    private String resolveDeliverySource(PostKind postKind, String mode) {
        String normalized = normalizeMode(postKind, mode);
        if (PostItemSupport.DELIVERY_MODE_INSTANT.equals(normalized)) return "provider";
        if (PostItemSupport.DELIVERY_MODE_STOCK.equals(normalized)) return "platform_inventory";
        return "submitted_result";
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isBlank()) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String defaultBuyerNotePlaceholder(String itemKind) {
        return PostItemInputDefaults.buyerNotePlaceholder(itemKind);
    }

    private int normalizeQuantity(PostKind postKind, Integer value) {
        if (value == null || value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than 0");
        }
        if (postKind == PostKind.PROJECT && value != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project item quantity must be 1");
        }
        return value;
    }

    private PostRef requirePostRef(String postNo) {
        if (postNo == null || postNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "post business number is required");
        }
        String normalized = postNo.trim();
        // 中文注释：公开 post item 写入口统一先把业务编号翻译成内部主键，后续权限与市场逻辑只处理领域 id。
        return offerRepository.findByOfferNo(normalized)
                .map(offer -> new PostRef(PostKind.OFFER, offer.id()))
                .or(() -> requestPostRepository.findByRequestNo(normalized).map(request -> new PostRef(PostKind.REQUEST, request.id())))
                .or(() -> projectRepository.findByProjectNo(normalized).map(project -> new PostRef(PostKind.PROJECT, project.id())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private String normalizeText(String value, int maxLength, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String normalizeOptionalItemDescription(String value) {
        return normalizeOptionalText(value, 2_000) == null ? "" : normalizeOptionalText(value, 2_000);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private LifecycleContext lifecycleContext(CommandContext context, Map<String, Object> metadata) {
        return new LifecycleContext(context.actorAccountId(), context.traceId(), Instant.now(), metadata);
    }

    private void saveEvent(String orderId, String eventType, String actorAccountId, Map<String, Object> payload) {
        orderEventRepository.save(new OrderEventEntity(
                "evt-" + UUID.randomUUID(),
                orderId,
                eventType,
                actorAccountId,
                payload,
                Instant.now()));
    }

    private record PostRef(PostKind postKind, String postId) {
    }
}
