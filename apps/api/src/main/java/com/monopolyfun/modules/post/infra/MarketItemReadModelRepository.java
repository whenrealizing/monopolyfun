package com.monopolyfun.modules.post.infra;

import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

public interface MarketItemReadModelRepository {
    PageResult<MarketItemRef> findPublic(String kind, String status, String q, String sort, PageQuery pageQuery);

    void upsertOffer(OfferEntity offer);

    void upsertRequest(RequestEntity request);

    void upsertProject(ProjectEntity project);

    record MarketItemRef(String id, String kind, String sourceId, String actorAccountId, String title,
                         java.time.Instant sortAt) {
    }
}
