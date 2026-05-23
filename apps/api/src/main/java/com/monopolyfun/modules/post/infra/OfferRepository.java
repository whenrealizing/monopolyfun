package com.monopolyfun.modules.post.infra;

import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository {
    List<OfferEntity> findAll();

    PageResult<OfferEntity> findPublic(String status, String q, String sort, PageQuery pageQuery);

    default List<OfferEntity> findPublicByActorAccountId(String actorAccountId, int limit) {
        return findByActorAccountId(actorAccountId, limit).stream()
                .filter(offer -> {
                    Object visibility = offer.metadata() == null ? null : offer.metadata().get("visibility");
                    return visibility == null || "market_public".equals(String.valueOf(visibility));
                })
                .limit(Math.max(1, limit))
                .toList();
    }

    List<OfferEntity> findByActorAccountId(String actorAccountId, int limit);

    List<OfferEntity> findWorkbenchCandidates(String actorAccountId, int limit);

    Optional<OfferEntity> findById(String id);

    default List<OfferEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    Optional<OfferEntity> findByOfferNo(String offerNo);

    OfferEntity save(OfferEntity offer);
}
