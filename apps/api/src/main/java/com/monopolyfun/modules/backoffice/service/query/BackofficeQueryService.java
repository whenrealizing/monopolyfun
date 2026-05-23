package com.monopolyfun.modules.backoffice.service.query;

import com.monopolyfun.modules.backoffice.service.view.AuditEventView;
import com.monopolyfun.modules.backoffice.service.view.BackofficeDashboardView;
import com.monopolyfun.modules.delivery.domain.DeliveryAttemptEntity;
import com.monopolyfun.modules.delivery.infra.DeliveryAttemptRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.payment.infra.PaymentProviderEventRepository;
import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.modules.risk.service.view.RiskEventView;
import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.modules.upload.infra.ProofAssetRepository;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;
import com.monopolyfun.shared.observability.infra.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BackofficeQueryService {
    private final MarketRepository marketRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final AuditEventRepository auditEventRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentProviderEventRepository paymentProviderEventRepository;
    private final SettlementEventRepository settlementEventRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ProofAssetRepository proofAssetRepository;
    private final RiskEventRepository riskEventRepository;

    public BackofficeQueryService(
            MarketRepository marketRepository,
            ListingRepository listingRepository,
            OrderRepository orderRepository,
            AuditEventRepository auditEventRepository,
            PaymentIntentRepository paymentIntentRepository,
            PaymentProviderEventRepository paymentProviderEventRepository,
            SettlementEventRepository settlementEventRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            ProofAssetRepository proofAssetRepository,
            RiskEventRepository riskEventRepository) {
        this.marketRepository = marketRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentProviderEventRepository = paymentProviderEventRepository;
        this.settlementEventRepository = settlementEventRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.proofAssetRepository = proofAssetRepository;
        this.riskEventRepository = riskEventRepository;
    }

    public BackofficeDashboardView getDashboard() {
        // 中文注释：后台首页统计直接走数据库聚合，避免运营面板随着业务表增长做全表对象映射。
        return new BackofficeDashboardView(
                marketRepository.countByStatus(),
                listingRepository.countByStatus(),
                orderRepository.countByStatus(),
                paymentIntentRepository.countByStatus(),
                proofAssetRepository.countByStatus(),
                riskEventRepository.countBySeverity(),
                listRecentAuditEvents(5),
                listRecentRiskEvents(5),
                listRecentPaymentIntents(5),
                listRecentProofAssets(5));
    }

    public List<AuditEventView> listRecentAuditEvents(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return auditEventRepository.findRecent(resolvedLimit).stream().map(com.monopolyfun.modules.backoffice.service.mapper.BackofficeViewMapper::audit).toList();
    }

    public List<RiskEventView> listRecentRiskEvents(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return riskEventRepository.findRecent(resolvedLimit).stream().map(com.monopolyfun.modules.risk.service.mapper.RiskViewMapper::risk).toList();
    }

    public List<PaymentIntentView> listRecentPaymentIntents(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return paymentIntentRepository.findRecent(resolvedLimit).stream().map(com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper::paymentIntent).toList();
    }

    public List<PaymentProviderEventEntity> listRecentPaymentProviderEvents(int limit) {
        return paymentProviderEventRepository.findRecent(limit);
    }

    public List<SettlementEventEntity> listRecentSettlementEvents(int limit) {
        return settlementEventRepository.findRecent(limit);
    }

    public List<DeliveryAttemptEntity> listRecentDeliveryAttempts(int limit) {
        return deliveryAttemptRepository.findRecent(limit);
    }

    public List<ProofAssetView> listRecentProofAssets(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return proofAssetRepository.findRecent(resolvedLimit).stream().map(com.monopolyfun.modules.upload.service.mapper.UploadViewMapper::proofAsset).toList();
    }

    public List<OrderSummary> listDisputedOrders(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return orderRepository.findDisputed(resolvedLimit).stream()
                .map(com.monopolyfun.modules.order.service.mapper.OrderViewMapper::order)
                .toList();
    }

    public List<ProofAssetView> listUploadExceptions(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return proofAssetRepository.findExceptions(resolvedLimit).stream()
                .map(com.monopolyfun.modules.upload.service.mapper.UploadViewMapper::proofAsset)
                .toList();
    }

    public List<Map<String, Object>> listSettlementAnomalies(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        return orderRepository.findSettlementAnomalies(resolvedLimit).stream()
                .map(anomaly -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("order", com.monopolyfun.modules.order.service.mapper.OrderViewMapper.order(anomaly.order()));
                    row.put("paymentIntent", anomaly.paymentIntent() == null ? null : com.monopolyfun.modules.payment.service.mapper.PaymentViewMapper.paymentIntent(anomaly.paymentIntent()));
                    row.put("reason", anomaly.reason());
                    return row;
                })
                .toList();
    }

    private Map<String, Long> countBy(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
