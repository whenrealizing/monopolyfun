package com.monopolyfun.modules.order.service.view;

import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.post.service.view.OrderPostView;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.share.service.view.ShareReleaseRequestView;
import com.monopolyfun.modules.share.service.view.ShareSettlementHoldView;

import java.util.List;

public record OrderDetailView(
        OrderSummary order,
        OrderPostView post,
        PostItemView item,
        ProofSummary proof,
        List<ProgressUpdateView> progressTimeline,
        PaymentIntentView paymentIntent,
        List<ActionView> availableActions,
        String displayPhase,
        SettlementPreview settlementPreview,
        ShareSettlementHoldView shareSettlementHold,
        ShareReleaseRequestView shareReleaseRequest,
        ReviewContext reviewContext,
        List<OrderEventView> eventTimeline
) {
}
