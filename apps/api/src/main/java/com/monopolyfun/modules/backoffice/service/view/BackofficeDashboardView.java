package com.monopolyfun.modules.backoffice.service.view;

import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.risk.service.view.RiskEventView;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;

import java.util.List;
import java.util.Map;

public record BackofficeDashboardView(
        Map<String, Long> marketCounts,
        Map<String, Long> listingCounts,
        Map<String, Long> orderCounts,
        Map<String, Long> paymentCounts,
        Map<String, Long> assetCounts,
        Map<String, Long> riskCounts,
        List<AuditEventView> recentAuditEvents,
        List<RiskEventView> recentRiskEvents,
        List<PaymentIntentView> recentPaymentIntents,
        List<ProofAssetView> recentProofAssets
) {
}
