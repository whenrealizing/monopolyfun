package com.monopolyfun.modules.post.infra;

import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequestPostRepository {
    List<RequestEntity> findAll();

    PageResult<RequestEntity> findPublic(String status, String q, String sort, PageQuery pageQuery);

    default List<RequestEntity> findPublicByActorAccountId(String actorAccountId, int limit) {
        return findByActorAccountId(actorAccountId, limit).stream()
                .filter(request -> {
                    Object visibility = request.metadata() == null ? null : request.metadata().get("visibility");
                    return visibility == null || "market_public".equals(String.valueOf(visibility));
                })
                .limit(Math.max(1, limit))
                .toList();
    }

    List<RequestEntity> findByActorAccountId(String actorAccountId, int limit);

    List<RequestEntity> findWorkbenchCandidates(String actorAccountId, int limit);

    Optional<RequestEntity> findById(String id);

    default List<RequestEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    Optional<RequestEntity> findByRequestNo(String requestNo);

    RequestEntity save(RequestEntity request);
}
