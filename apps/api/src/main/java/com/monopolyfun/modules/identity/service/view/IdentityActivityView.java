package com.monopolyfun.modules.identity.service.view;

import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.modules.post.service.view.RequestView;
import com.monopolyfun.modules.project.service.view.ProjectView;
import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.modules.share.service.view.ShareSettlementHoldView;

import java.util.List;
import java.util.Map;

public record IdentityActivityView(
        List<OfferView> myOffers,
        List<RequestView> myRequests,
        List<ProjectView> myProjects,
        List<OrderSummary> myOrders,
        List<SharesLedgerEntryEntity> sharesLedger,
        List<ShareSettlementHoldView> shareSettlementHolds,
        Map<String, Object> agentCapabilitySummary
) {
}
