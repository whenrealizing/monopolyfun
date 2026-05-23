package com.monopolyfun.modules.order.service.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DisputeWindowScheduler {
    private static final Logger log = LoggerFactory.getLogger(DisputeWindowScheduler.class);

    private final OrderCommandService orderCommandService;

    public DisputeWindowScheduler(OrderCommandService orderCommandService) {
        this.orderCommandService = orderCommandService;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void finalizeExpiredDisputeWindows() {
        // 中文注释：验收后的争议窗口到期后自动进入最终完成，用户侧不再继续暴露争议入口。
        int finalizedCount = orderCommandService.finalizeExpiredDisputeWindows();
        if (finalizedCount > 0) {
            log.info("Finalized {} accepted orders after dispute window expiry", finalizedCount);
        }
    }
}
