package com.monopolyfun.modules.order.service.workflow;

import com.monopolyfun.modules.order.domain.OrderAction;
import com.monopolyfun.modules.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Component
public class OrderWorkflowCatalog {
    private static final List<OrderWorkflowTransition> TRANSITIONS = List.of(
            new OrderWorkflowTransition(OrderAction.CLAIM, null, OrderStatus.CLAIMED, "participant", "order_claimed"),
            new OrderWorkflowTransition(OrderAction.SUBMIT_PROGRESS, OrderStatus.CLAIMED, OrderStatus.CLAIMED, "fulfiller", "reviewed_delivery_progress"),
            new OrderWorkflowTransition(OrderAction.SUBMIT_PROOF, OrderStatus.CLAIMED, OrderStatus.DELIVERED, "fulfiller", "proof_with_acceptance_criteria"),
            new OrderWorkflowTransition(OrderAction.SUBMIT_DELIVERY_RESULT, OrderStatus.CLAIMED, OrderStatus.DELIVERED, "fulfiller", "reviewed_delivery_result"),
            new OrderWorkflowTransition(OrderAction.REQUEST_REVISION, OrderStatus.DELIVERED, OrderStatus.CLAIMED, "payer_or_reviewer", "delivery_revision"),
            new OrderWorkflowTransition(OrderAction.ACCEPT, OrderStatus.DELIVERED, OrderStatus.ACCEPTED_OPEN, "payer_or_reviewer", "acceptance_window"),
            new OrderWorkflowTransition(OrderAction.ACCEPT, OrderStatus.DELIVERED, OrderStatus.FINAL_ACCEPTED, "payer_or_reviewer", "review_order_final_accept"),
            new OrderWorkflowTransition(OrderAction.ACCEPT, OrderStatus.ACCEPTED_OPEN, OrderStatus.FINAL_ACCEPTED, "system_or_authority", "acceptance_window_expired"),
            new OrderWorkflowTransition(OrderAction.ACCEPT, OrderStatus.DISPUTED, OrderStatus.FINAL_ACCEPTED, "reviewer_or_authority", "dispute_accept_original"),
            new OrderWorkflowTransition(OrderAction.OPEN_DISPUTE, OrderStatus.DELIVERED, OrderStatus.DISPUTED, "participant_or_authority", "work_review_item"),
            new OrderWorkflowTransition(OrderAction.OPEN_DISPUTE, OrderStatus.ACCEPTED_OPEN, OrderStatus.DISPUTED, "participant_or_authority", "work_review_item"),
            new OrderWorkflowTransition(OrderAction.CANCEL_DISPUTE, OrderStatus.DISPUTED, OrderStatus.DELIVERED, "dispute_opener", "restore_delivered_state"),
            new OrderWorkflowTransition(OrderAction.CANCEL_DISPUTE, OrderStatus.DISPUTED, OrderStatus.ACCEPTED_OPEN, "dispute_opener", "restore_acceptance_window"),
            new OrderWorkflowTransition(OrderAction.OPEN_APPEAL, OrderStatus.DISPUTED, OrderStatus.DISPUTED, "participant", "appeal_review_item"),
            new OrderWorkflowTransition(OrderAction.ASSIGN_REVIEWER, OrderStatus.DISPUTED, OrderStatus.DISPUTED, "authority", "reviewer_work_item"),
            new OrderWorkflowTransition(OrderAction.OVERRIDE_REVIEW, OrderStatus.DISPUTED, OrderStatus.DISPUTED, "authority", "override_marker"),
            new OrderWorkflowTransition(OrderAction.CLOSE, OrderStatus.CLAIMED, OrderStatus.FINAL_CLOSED, "authority", "order_closed"),
            new OrderWorkflowTransition(OrderAction.CLOSE, OrderStatus.DISPUTED, OrderStatus.FINAL_CLOSED, "reviewer_or_authority", "dispute_close_original"));

    public List<OrderWorkflowTransition> transitions() {
        // 中文注释：先用只读目录把核心订单状态图显式化，后续可直接迁移到 Temporal/Camunda/Spring Statemachine 定义。
        return TRANSITIONS;
    }

    public Optional<OrderWorkflowTransition> find(OrderAction action, OrderStatus fromStatus, OrderStatus toStatus) {
        return TRANSITIONS.stream()
                .filter(transition -> transition.action() == action)
                .filter(transition -> transition.fromStatus() == fromStatus)
                .filter(transition -> transition.toStatus() == toStatus)
                .findFirst();
    }

    public OrderWorkflowTransition require(OrderAction action, OrderStatus fromStatus, OrderStatus toStatus) {
        // 中文注释：命令层先走目录校验，再调用领域实体生成快照，状态图入口保持唯一。
        return find(action, fromStatus, toStatus)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unsupported order workflow transition: " + fromStatus + " -> " + toStatus + " by " + action));
    }

    public record OrderWorkflowTransition(
            OrderAction action,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorPolicy,
            String sideEffect
    ) {
    }
}
