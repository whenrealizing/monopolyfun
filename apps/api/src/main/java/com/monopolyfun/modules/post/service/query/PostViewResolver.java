package com.monopolyfun.modules.post.service.query;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.view.OrderPostView;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PostViewResolver {
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final ListingRepository listingRepository;

    public PostViewResolver(
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            ListingRepository listingRepository) {
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.listingRepository = listingRepository;
    }

    public OrderPostView resolve(OrderEntity order) {
        ListingEntity listing = order.listingId() == null ? null : listingRepository.findById(order.listingId()).orElse(null);
        if (listing != null && PostItemSupport.SUBJECT_TYPE.equalsIgnoreCase(listing.subjectType())) {
            return listingPostView(listing);
        }

        if (order.postKind() == PostKind.OFFER && order.postId() != null) {
            OfferEntity offer = offerRepository.findById(order.postId()).orElse(null);
            if (offer != null) {
                return new OrderPostView(
                        "offer",
                        offer.id(),
                        offer.title(),
                        offer.description(),
                        offer.deliveryStandard(),
                        offer.priceAmount() == null ? "Price pending" : offer.priceAmount() + " " + offer.currency(),
                        inventorySummary(offer.inventoryPolicy().name().toLowerCase(), offer.stockSold(), offer.stockTotal()),
                        offer.status().name().toLowerCase());
            }
        }
        if (order.postKind() == PostKind.REQUEST && order.postId() != null) {
            RequestEntity request = requestPostRepository.findById(order.postId()).orElse(null);
            if (request != null) {
                return new OrderPostView(
                        "request",
                        request.id(),
                        request.title(),
                        request.description(),
                        request.deliveryStandard(),
                        request.budgetAmount() == null ? "Budget pending" : request.budgetAmount() + " " + request.currency(),
                        inventorySummary(request.inventoryPolicy().name().toLowerCase(), request.stockFilled(), request.stockTotal()),
                        request.status().name().toLowerCase());
            }
        }
        if (order.postKind() == PostKind.PROJECT && order.postId() != null) {
            ProjectEntity project = projectRepository.findById(order.postId()).orElse(null);
            if (project != null) {
                return new OrderPostView(
                        "project",
                        project.id(),
                        project.title(),
                        project.summary(),
                        project.oneSentence(),
                        "Project slot",
                        inventorySummary(project.inventoryPolicy().name().toLowerCase(), project.stockSold(), project.stockTotal()),
                        project.status().name().toLowerCase());
            }
        }

        if (listing != null) {
            return new OrderPostView(
                    order.postKind() == null ? "unknown" : order.postKind().name().toLowerCase(),
                    listing.id(),
                    listing.title(),
                    stringValue(listing.metadata().get("summary"), listing.deliverableSpec()),
                    listing.deliverableSpec(),
                    listing.settlementSpec(),
                    inventorySummary("limited", listing.activeOrdersCount(), listing.inventoryLimit()),
                    listing.status().name().toLowerCase());
        }

        Map<String, Object> deliverySnapshot = order.deliverySnapshot();
        return new OrderPostView(
                order.postKind() == null ? "unknown" : order.postKind().name().toLowerCase(),
                order.postId() == null ? order.id() : order.postId(),
                stringValue(deliverySnapshot.get("title"), order.id()),
                stringValue(deliverySnapshot.get("summary"), ""),
                stringValue(deliverySnapshot.get("deliveryStandard"), ""),
                stringValue(order.settlementSnapshot().get("summary"), ""),
                "unknown",
                "unknown");
    }

    private OrderPostView listingPostView(ListingEntity listing) {
        PostKind postKind = PostItemSupport.postKind(listing.metadata());
        String postId = PostItemSupport.postId(listing.metadata());
        return new OrderPostView(
                postKind == null ? "unknown" : postKind.name().toLowerCase(),
                postId,
                listing.title(),
                stringValue(listing.metadata().get("summary"), listing.deliverableSpec()),
                listing.deliverableSpec(),
                listing.settlementSpec(),
                inventorySummary("limited", listing.activeOrdersCount(), listing.inventoryLimit()),
                listing.status().name().toLowerCase());
    }

    private String inventorySummary(String policy, int used, Integer total) {
        if ("unlimited".equals(policy) || total == null) return "unlimited";
        return used + "/" + total;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
