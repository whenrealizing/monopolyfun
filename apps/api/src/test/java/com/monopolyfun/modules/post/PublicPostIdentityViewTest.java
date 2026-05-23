package com.monopolyfun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.OfferStatus;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.domain.RequestStatus;
import com.monopolyfun.modules.post.service.mapper.PostViewMapper;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.service.mapper.ProjectViewMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicPostIdentityViewTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publicOfferAndRequestExposeOnlyActorHandle() throws Exception {
        Instant now = Instant.parse("2026-05-15T10:00:00Z");
        var offer = new OfferEntity(
                "offer-id",
                "OFF-1",
                "acct-private",
                "Offer",
                "Offer description",
                "Delivery standard",
                BigDecimal.TEN,
                "USD",
                "okx_direct_pay",
                null,
                null,
                null,
                null,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                OfferStatus.OPEN,
                Map.of(),
                now,
                now);
        var request = new RequestEntity(
                "request-id",
                "REQ-1",
                "acct-private",
                "Request",
                "Request description",
                "Delivery standard",
                BigDecimal.ONE,
                "USD",
                "okx_direct_pay",
                null,
                null,
                null,
                null,
                InventoryPolicy.UNLIMITED,
                null,
                0,
                RequestStatus.OPEN,
                null,
                Map.of(),
                now,
                now);

        var offerView = PostViewMapper.publicOffer(offer, "perf_user_1");
        var requestView = PostViewMapper.publicRequest(request, "perf_user_1");

        assertNull(offerView.actorAccountId());
        assertEquals("perf_user_1", offerView.actorHandle());
        assertFalse(objectMapper.writeValueAsString(offerView).contains("actorAccountId"));
        assertNull(requestView.actorAccountId());
        assertEquals("perf_user_1", requestView.actorHandle());
        assertFalse(objectMapper.writeValueAsString(requestView).contains("actorAccountId"));
    }

    @Test
    void publicProjectExposesOnlyOwnerHandle() throws Exception {
        Instant now = Instant.parse("2026-05-15T10:00:00Z");
        var project = new ProjectEntity(
                "project-id",
                "project-1",
                "acct-private",
                ProjectLevel.CHILD,
                "project-root",
                "Project",
                "Project summary",
                "One sentence",
                InventoryPolicy.UNLIMITED,
                null,
                0,
                ProjectStatus.ACTIVE,
                Map.of(),
                now,
                now);

        var view = ProjectViewMapper.publicProject(project, List.of(), "perf_user_1", Map.of());

        assertNull(view.ownerAccountId());
        assertEquals("perf_user_1", view.ownerHandle());
        assertFalse(objectMapper.writeValueAsString(view).contains("ownerAccountId"));
    }
}
