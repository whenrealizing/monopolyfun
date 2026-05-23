package com.monopolyfun.modules.order.api;

import com.monopolyfun.modules.identity.service.view.AccountSummary;
import com.monopolyfun.modules.order.service.command.OrderCommandService;
import com.monopolyfun.modules.order.service.query.OrderQueryService;
import com.monopolyfun.modules.order.service.view.OrderDetailView;
import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    public OrderController(OrderQueryService orderQueryService, OrderCommandService orderCommandService) {
        this.orderQueryService = orderQueryService;
        this.orderCommandService = orderCommandService;
    }

    @GetMapping
    public PageResult<OrderSummary> listOrders(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return orderQueryService.listCurrentOrders(PageQuery.of(limit, cursor), includeAgent);
    }

    @GetMapping("/{orderNo}")
    public OrderDetailView getOrder(
            @PathVariable String orderNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return orderQueryService.getOrderDetail(orderNo, includeAgent);
    }

    @GetMapping("/{orderNo}/reviewer-candidates")
    public List<AccountSummary> listReviewerCandidates(@PathVariable String orderNo) {
        return orderCommandService.listReviewerCandidates(orderNo).stream()
                .map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::account)
                .toList();
    }
}
