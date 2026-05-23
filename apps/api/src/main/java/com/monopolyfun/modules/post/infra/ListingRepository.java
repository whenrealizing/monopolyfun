package com.monopolyfun.modules.post.infra;

import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.PostKind;

import java.util.List;
import java.util.Optional;

public interface ListingRepository {
    List<ListingEntity> findAll();

    java.util.Map<String, Long> countByStatus();

    List<ListingEntity> findByMarketId(String marketId);

    List<PostItemListing> findPostItems(PostKind postKind, String postId);

    List<PostItemListing> findPostItems(PostKind postKind, List<String> postIds);

    Optional<ListingEntity> findById(String id);

    Optional<ListingEntity> findByIdForUpdate(String id);

    Optional<PostItemListing> findPostItemById(String itemId);

    ListingEntity save(ListingEntity listing);

    record PostItemListing(ListingEntity listing, String latestOrderId, String latestPaymentStatus) {
    }
}
