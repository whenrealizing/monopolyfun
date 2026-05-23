package com.monopolyfun.modules.order.service.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReviewTimeoutScheduler.class);

    private final OrderCommandService orderCommandService;

    public ReviewTimeoutScheduler(OrderCommandService orderCommandService) {
        this.orderCommandService = orderCommandService;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void reassignExpiredReviewers() {
        // 中文注释：reviewer 超时改派由服务端定时扫描，订单页只展示 action policy 的当前结果。
        int reassignedCount = orderCommandService.reassignExpiredReviewers();
        if (reassignedCount > 0) {
            log.info("Reassigned {} expired reviewer assignments", reassignedCount);
        }
    }
}
