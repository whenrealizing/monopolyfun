package com.monopolyfun.modules.order.infra;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    List<OrderEntity> findAll();

    java.util.Map<String, Long> countByStatus();

    List<OrderEntity> findByParticipantAccountId(String accountId, int limit);

    PageResult<OrderEntity> findByParticipantAccountId(String accountId, PageQuery pageQuery);

    List<OrderEntity> findWorkbenchCandidates(String accountId, int limit);

    List<OrderEntity> findDisputed(int limit);

    List<OrderEntity> findExpiredPaymentLocks(Instant dueAt, int limit);

    List<OrderEntity> findExpiredDisputeWindows(Instant dueAt, int limit);

    List<SettlementAnomaly> findSettlementAnomalies(int limit);

    List<OrderEntity> findByMarketId(String marketId);

    Optional<OrderEntity> findById(String id);

    default List<OrderEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    Optional<OrderEntity> findByOrderNo(String orderNo);

    default Optional<OrderEntity> findActiveByListingIdAndClaimedByAccountId(String listingId, String accountId) {
        return Optional.empty();
    }

    Optional<OrderEntity> findFirstByListingId(String listingId);

    Optional<OrderEntity> findFirstByParentOrderId(String parentOrderId);

    OrderEntity save(OrderEntity order);

    record SettlementAnomaly(OrderEntity order, PaymentIntentEntity paymentIntent, String reason) {
    }
}
