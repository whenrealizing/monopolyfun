package com.monopolyfun.modules.post.service.query;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.service.PublicIdentityRefs;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.modules.identity.service.view.PublicAccountSummary;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository.MarketItemRef;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.view.PostItemSummaryView;
import com.monopolyfun.modules.post.service.view.PublicFeedView;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.shared.pagination.PageQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicFeedQueryService {
    private final AccountRepository accountRepository;
    private final AccountSummaryProjector accountSummaryProjector;
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final MarketItemReadModelRepository marketItemReadModelRepository;
    private final ListingRepository listingRepository;
    private final PostQueryService postQueryService;
    private final RootProjectService rootProjectService;

    public PublicFeedQueryService(
            AccountRepository accountRepository,
            AccountSummaryProjector accountSummaryProjector,
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            MarketItemReadModelRepository marketItemReadModelRepository,
            ListingRepository listingRepository,
            PostQueryService postQueryService,
            RootProjectService rootProjectService) {
        this.accountRepository = accountRepository;
        this.accountSummaryProjector = accountSummaryProjector;
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.marketItemReadModelRepository = marketItemReadModelRepository;
        this.listingRepository = listingRepository;
        this.postQueryService = postQueryService;
        this.rootProjectService = rootProjectService;
    }

    public PublicFeedView homeFeed() {
        PageQuery snapshot = PageQuery.of(36, null);
        List<MarketItemRef> refs = marketItemReadModelRepository.findPublic("all", "open", null, "recent", snapshot).items();
        ProjectEntity root = rootProjectService.ensureRootProject(null);
        // 中文注释：首页聚合从 market_items_read_model 取候选，再按类型批量回源组装视图。
        List<OfferEntity> offers = offersFor(refs);
        List<RequestEntity> requests = requestsFor(refs);
        List<ProjectEntity> projects = projectsFor(refs);
        return feed(offers, requests, projects, root);
    }

    public PublicFeedView marketFeed(String kind, String status, String q, String sort, PageQuery pageQuery) {
        String resolvedKind = kind == null || kind.isBlank() ? "all" : kind.trim().toLowerCase();
        List<MarketItemRef> refs = marketItemReadModelRepository.findPublic(resolvedKind, status, q, sort, pageQuery).items();
        // 中文注释：市场 all tab 以统一 cursor 先定边界，随后只批量读取本页涉及的源实体。
        return feed(offersFor(refs), requestsFor(refs), projectsFor(refs), null);
    }

    private List<OfferEntity> offersFor(List<MarketItemRef> refs) {
        return orderedRefs(refs, "offer", offerRepository.findByIds(sourceIds(refs, "offer")), OfferEntity::id);
    }

    private List<RequestEntity> requestsFor(List<MarketItemRef> refs) {
        return orderedRefs(refs, "request", requestPostRepository.findByIds(sourceIds(refs, "request")), RequestEntity::id);
    }

    private List<ProjectEntity> projectsFor(List<MarketItemRef> refs) {
        return orderedRefs(refs, "project", projectRepository.findByIds(sourceIds(refs, "project")), ProjectEntity::id);
    }

    private List<String> sourceIds(List<MarketItemRef> refs, String kind) {
        return refs.stream()
                .filter(ref -> kind.equals(ref.kind()))
                .map(MarketItemRef::sourceId)
                .toList();
    }

    private <T> List<T> orderedRefs(List<MarketItemRef> refs, String kind, List<T> entities, Function<T, String> idReader) {
        Map<String, T> byId = entities.stream().collect(Collectors.toMap(idReader, Function.identity()));
        return refs.stream()
                .filter(ref -> kind.equals(ref.kind()))
                .map(ref -> byId.get(ref.sourceId()))
                .filter(item -> item != null)
                .toList();
    }

    private PublicFeedView feed(
            List<OfferEntity> offers,
            List<RequestEntity> requests,
            List<ProjectEntity> projects,
            ProjectEntity rootProject) {
        Map<String, String> handlesByAccountId = accountHandlesByAccountId(offers, requests, projects, rootProject);
        Map<String, PostItemSummaryView> offerItemSummaries = itemSummaries(PostKind.OFFER, offers.stream().map(OfferEntity::id).toList());
        Map<String, PostItemSummaryView> requestItemSummaries = itemSummaries(PostKind.REQUEST, requests.stream().map(RequestEntity::id).toList());
        var offerViews = offers.stream()
                .map(offer -> com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicOffer(offer, handlesByAccountId.get(offer.actorAccountId()), offerItemSummaries.get(offer.id())))
                .toList();
        var requestViews = requests.stream()
                .map(request -> com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicRequest(request, handlesByAccountId.get(request.actorAccountId()), requestItemSummaries.get(request.id())))
                .toList();
        var projectViews = postQueryService.projectViews(projects);
        var rootProjectView = rootProject == null ? null : postQueryService.projectViews(List.of(rootProject)).getFirst();
        return new PublicFeedView(
                accountsById(offers, requests, projects, rootProject),
                offerViews,
                requestViews,
                projectViews,
                rootProjectView,
                Map.of(
                        "offer", (long) offerViews.size(),
                        "request", (long) requestViews.size(),
                        "project", (long) projectViews.size() + (rootProjectView == null ? 0L : 1L)));
    }

    private Map<String, PostItemSummaryView> itemSummaries(PostKind postKind, List<String> postIds) {
        Map<String, ItemSummaryAccumulator> summaries = new LinkedHashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        postIds.forEach(postId -> summaries.put(postId, new ItemSummaryAccumulator(postKind)));
        listingRepository.findPostItems(postKind, postIds).forEach(item -> {
            ListingEntity listing = item.listing();
            String postId = PostItemSupport.postId(listing.metadata());
            ItemSummaryAccumulator summary = summaries.get(postId);
            if (summary != null) {
                summary.add(listing);
            }
        });
        return summaries.entrySet().stream()
                .filter(entry -> entry.getValue().itemCount > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toView(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, PublicAccountSummary> accountsById(
            List<OfferEntity> offers,
            List<RequestEntity> requests,
            List<ProjectEntity> projects,
            ProjectEntity rootProject) {
        LinkedHashSet<String> accountIds = new LinkedHashSet<>();
        offers.forEach(offer -> accountIds.add(offer.actorAccountId()));
        requests.forEach(request -> accountIds.add(request.actorAccountId()));
        projects.forEach(project -> accountIds.add(project.ownerAccountId()));
        if (rootProject != null) {
            accountIds.add(rootProject.ownerAccountId());
        }
        Map<String, PublicAccountSummary> accounts = new LinkedHashMap<>();
        accountRepository.findByIds(accountIds).forEach(account -> accounts.put(PublicIdentityRefs.accountId(account.handle()), accountSummaryProjector.publicProject(account)));
        return accounts;
    }

    private Map<String, String> accountHandlesByAccountId(
            List<OfferEntity> offers,
            List<RequestEntity> requests,
            List<ProjectEntity> projects,
            ProjectEntity rootProject) {
        LinkedHashSet<String> accountIds = new LinkedHashSet<>();
        offers.forEach(offer -> accountIds.add(offer.actorAccountId()));
        requests.forEach(request -> accountIds.add(request.actorAccountId()));
        projects.forEach(project -> accountIds.add(project.ownerAccountId()));
        if (rootProject != null) {
            accountIds.add(rootProject.ownerAccountId());
        }
        return accountRepository.findByIds(accountIds).stream()
                .collect(Collectors.toMap(AccountEntity::id, AccountEntity::handle));
    }

    private static final class ItemSummaryAccumulator {
        private final PostKind postKind;
        private long itemCount;
        private long openItemCount;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private int totalQuantity;
        private int remainingQuantity;
        private String currency;

        private ItemSummaryAccumulator(PostKind postKind) {
            this.postKind = postKind;
        }

        private static String metadataText(Map<String, Object> metadata, String key) {
            if (metadata == null) return null;
            Object value = metadata.get(key);
            if (value == null) return null;
            String text = String.valueOf(value).trim();
            return text.isBlank() ? null : text;
        }

        private void add(ListingEntity listing) {
            itemCount++;
            if (listing.status() != ListingStatus.OPEN) {
                return;
            }
            openItemCount++;
            totalQuantity += listing.inventoryLimit();
            remainingQuantity += Math.max(listing.inventoryLimit() - listing.activeOrdersCount(), 0);
            BigDecimal amount = PostItemSupport.metadataAmount(listing.metadata(), postKind == PostKind.REQUEST ? "budgetAmount" : "priceAmount");
            if (amount == null) {
                return;
            }
            if (currency == null) {
                currency = metadataText(listing.metadata(), "currency");
            }
            minAmount = minAmount == null || amount.compareTo(minAmount) < 0 ? amount : minAmount;
            maxAmount = maxAmount == null || amount.compareTo(maxAmount) > 0 ? amount : maxAmount;
        }

        private PostItemSummaryView toView() {
            return new PostItemSummaryView(
                    itemCount,
                    openItemCount,
                    minAmount,
                    maxAmount,
                    openItemCount == 0 ? null : totalQuantity,
                    openItemCount == 0 ? null : remainingQuantity,
                    currency);
        }
    }
}
