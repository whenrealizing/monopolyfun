package com.monopolyfun.modules.work.api;

import com.monopolyfun.modules.order.api.request.AcceptOrderRequest;
import com.monopolyfun.modules.order.api.request.AppealOrderRequest;
import com.monopolyfun.modules.order.api.request.AssignReviewerRequest;
import com.monopolyfun.modules.order.api.request.BackofficeOverrideReviewRequest;
import com.monopolyfun.modules.order.api.request.CancelDisputeRequest;
import com.monopolyfun.modules.order.api.request.CloseOrderRequest;
import com.monopolyfun.modules.order.api.request.DisputeOrderRequest;
import com.monopolyfun.modules.order.api.request.SubmitProgressRequest;
import com.monopolyfun.modules.order.api.request.SubmitProofRequest;
import com.monopolyfun.modules.work.api.request.ClaimWorkItemRequest;
import com.monopolyfun.modules.work.api.request.CloseWorkRunRequest;
import com.monopolyfun.modules.work.api.request.RequestWorkHelpRequest;
import com.monopolyfun.modules.work.api.request.ReviewWorkReceiptRequest;
import com.monopolyfun.modules.work.api.request.ReviseWorkReceiptRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkProgressRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkReceiptRequest;
import com.monopolyfun.modules.work.service.WorkCommandService;
import com.monopolyfun.modules.work.service.WorkQueryService;
import com.monopolyfun.modules.work.service.view.WorkItemView;
import com.monopolyfun.shared.command.CommandReceipt;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work")
public class WorkController {
    private final WorkQueryService workQueryService;
    private final WorkCommandService workCommandService;

    public WorkController(WorkQueryService workQueryService, WorkCommandService workCommandService) {
        this.workQueryService = workQueryService;
        this.workCommandService = workCommandService;
    }

    @GetMapping("/items")
    public List<WorkItemView> listWorkItems() {
        return workQueryService.listCurrentAccountWorkItems();
    }

    @GetMapping("/items/{itemId}")
    public WorkItemView getWorkItem(@PathVariable String itemId) {
        return workQueryService.requireAccessibleWorkItem(itemId);
    }

    @PostMapping("/items/{itemId}/claim")
    public CommandReceipt claimWorkItem(@PathVariable String itemId, @Valid @RequestBody ClaimWorkItemRequest request) {
        return workCommandService.claimWorkItem(itemId, request);
    }

    @PostMapping("/items/{itemId}/receipt")
    public CommandReceipt submitWorkReceipt(@PathVariable String itemId, @Valid @RequestBody SubmitWorkReceiptRequest request) {
        return workCommandService.submitReceipt(itemId, request);
    }

    @PostMapping("/items/{itemId}/progress")
    public CommandReceipt submitWorkProgress(@PathVariable String itemId, @Valid @RequestBody SubmitWorkProgressRequest request) {
        return workCommandService.submitProgress(itemId, request);
    }

    @PostMapping("/items/{itemId}/help")
    public CommandReceipt requestWorkHelp(@PathVariable String itemId, @Valid @RequestBody RequestWorkHelpRequest request) {
        return workCommandService.requestHelp(itemId, request);
    }

    @PostMapping("/items/{itemId}/review")
    public CommandReceipt reviewWorkReceipt(@PathVariable String itemId, @Valid @RequestBody ReviewWorkReceiptRequest request) {
        return workCommandService.reviewReceipt(itemId, request);
    }

    @PostMapping("/items/{itemId}/revise")
    public CommandReceipt reviseWorkReceipt(@PathVariable String itemId, @Valid @RequestBody ReviseWorkReceiptRequest request) {
        return workCommandService.reviseReceipt(itemId, request);
    }

    @PostMapping("/orders/{orderNo}/cancel-dispute")
    public CommandReceipt cancelOrderDispute(@PathVariable String orderNo, @Valid @RequestBody CancelDisputeRequest request) {
        return workCommandService.cancelOrderDispute(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/proofs")
    public CommandReceipt submitOrderProof(@PathVariable String orderNo, @Valid @RequestBody SubmitProofRequest request) {
        return workCommandService.submitOrderProof(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/progress")
    public CommandReceipt submitOrderProgress(@PathVariable String orderNo, @Valid @RequestBody SubmitProgressRequest request) {
        return workCommandService.submitOrderProgress(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/accept")
    @Operation(operationId = "acceptWorkOrder")
    public CommandReceipt acceptOrder(@PathVariable String orderNo, @Valid @RequestBody AcceptOrderRequest request) {
        return workCommandService.acceptOrder(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/dispute")
    public CommandReceipt openOrderDispute(@PathVariable String orderNo, @Valid @RequestBody DisputeOrderRequest request) {
        return workCommandService.openOrderDispute(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/appeal")
    public CommandReceipt openOrderAppeal(@PathVariable String orderNo, @Valid @RequestBody AppealOrderRequest request) {
        return workCommandService.openOrderAppeal(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/assign-reviewer")
    public CommandReceipt assignOrderReviewer(@PathVariable String orderNo, @Valid @RequestBody AssignReviewerRequest request) {
        return workCommandService.assignOrderReviewer(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/override-review")
    public CommandReceipt overrideOrderReview(@PathVariable String orderNo, @Valid @RequestBody BackofficeOverrideReviewRequest request) {
        return workCommandService.overrideOrderReview(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/close")
    @Operation(operationId = "closeWorkOrder")
    public CommandReceipt closeOrder(@PathVariable String orderNo, @Valid @RequestBody CloseOrderRequest request) {
        return workCommandService.closeOrder(orderNo, request);
    }

    @PostMapping("/orders/{orderNo}/abandon-payment")
    public CommandReceipt abandonOrderPayment(@PathVariable String orderNo, @Valid @RequestBody CloseOrderRequest request) {
        return workCommandService.abandonOrderPayment(orderNo, request);
    }

    @PostMapping("/items/{itemId}/close")
    public CommandReceipt closeWorkRun(@PathVariable String itemId, @Valid @RequestBody CloseWorkRunRequest request) {
        return workCommandService.closeWorkRun(itemId, request);
    }
}
