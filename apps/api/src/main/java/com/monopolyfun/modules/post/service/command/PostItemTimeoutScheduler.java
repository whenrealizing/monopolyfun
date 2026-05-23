package com.monopolyfun.modules.post.service.command;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class PostItemTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(PostItemTimeoutScheduler.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final MarketRepository marketRepository;
    private final OrderEventRepository orderEventRepository;
    private final ProjectSharePoolService projectSharePoolService;
    private final PaymentIntentRepository paymentIntentRepository;

    public PostItemTimeoutScheduler(
            OrderRepository orderRepository,
            ListingRepository listingRepository,
            MarketRepository marketRepository,
            OrderEventRepository orderEventRepository,
            ProjectSharePoolService projectSharePoolService,
            PaymentIntentRepository paymentIntentRepository) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.marketRepository = marketRepository;
        this.orderEventRepository = orderEventRepository;
        this.projectSharePoolService = projectSharePoolService;
        this.paymentIntentRepository = paymentIntentRepository;
    }

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void releaseExpiredPaymentLocks() {
        Instant now = Instant.now();
        for (OrderEntity order : orderRepository.findExpiredPaymentLocks(now, 500)) {
            if (order.status() != OrderStatus.CLAIMED
                    || order.kind() == ListingKind.REVIEW) {
                continue;
            }
            if (order.settlementType() == SettlementType.MONEY && hasCapturedPayment(order)) {
                continue;
            }
            Instant paymentDueAt = PostItemSupport.metadataInstant(order.metadata(), "paymentDueAt");
            if (paymentDueAt == null) {
                paymentDueAt = PostItemSupport.metadataInstant(order.metadata(), "lockExpiresAt");
            }
            if (paymentDueAt == null || paymentDueAt.isAfter(now)) {
                continue;
            }
            ListingEntity listing = listingRepository.findById(order.listingId()).orElse(null);
            MarketEntity market = marketRepository.findById(order.marketId()).orElse(null);
            if (listing == null || market == null || !PostItemSupport.SUBJECT_TYPE.equalsIgnoreCase(listing.subjectType())) {
                continue;
            }

            // 中文注释：锁单付款窗口超时释放 item；付款后的交付截止只作为 SLA 提醒，不再自动关闭订单。
            LifecycleContext lifecycleContext = new LifecycleContext(market.leadAccountId(), "scheduler-timeout-release", now, Map.of(
                    "reason", "timeout_release",
                    "itemId", listing.id()));
            OrderEntity closed = order.finalizeClosed(market.leadAccountId(), "timeout_release", lifecycleContext).entity();
            orderRepository.save(closed);

            Integer reservedShares = PostItemSupport.metadataInt(order.metadata(), "reservedShares");
            if (reservedShares != null && reservedShares > 0) {
                // 中文注释：项目任务超时只释放 project_share_pools 的任务预留，market metadata 保持展示职责。
                projectSharePoolService.releaseTaskReservation(order.marketId(), reservedShares);
            }

            ListingEntity reopened = listing.withActiveOrdersCount(Math.max(0, listing.activeOrdersCount() - 1));
            if (reopened.status() != ListingStatus.ARCHIVED) {
                listingRepository.save(reopened.withStatus(ListingStatus.OPEN));
            } else {
                listingRepository.save(reopened);
            }

            orderEventRepository.save(new OrderEventEntity(
                    "evt-" + UUID.randomUUID(),
                    closed.id(),
                    "order_closed",
                    market.leadAccountId(),
                    Map.of("reason", "timeout_release", "itemId", listing.id(), "label", "Item 锁单超时已释放"),
                    now));
            log.info("Released expired reviewed delivery post item order {}", closed.id());
        }
    }

    private boolean hasCapturedPayment(OrderEntity order) {
        return paymentIntentRepository.findByOrderId(order.id())
                .map(paymentIntent -> paymentIntent.status() == PaymentIntentStatus.CAPTURED)
                .orElse(false);
    }
}
