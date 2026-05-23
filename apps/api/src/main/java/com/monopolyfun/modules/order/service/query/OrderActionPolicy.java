package com.monopolyfun.modules.order.service.query;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.service.view.ActionView;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderActionPolicy {
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final WorkRepository workRepository;
    public OrderActionPolicy(
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService,
            PaymentIntentRepository paymentIntentRepository,
            WorkRepository workRepository) {
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
        this.paymentIntentRepository = paymentIntentRepository;
        this.workRepository = workRepository;
    }

    public List<ActionView> availableActions(OrderEntity order) {
        List<ActionView> actions = new ArrayList<>();
        String currentAccountId = currentAccountAccess.current().map(account -> account.accountId()).orElse(null);
        if (currentAccountId == null) {
            return actions;
        }
        if (order.status() == OrderStatus.CLAIMED) {
            addPaymentActionIfNeeded(actions, order, currentAccountId);
            if (!currentAccountId.equals(order.fulfillerAccountId())) {
                return actions;
            }
            String paymentBlocker = paymentBlocker(order, PaymentBlockerAudience.FULFILLER);
            if (order.kind() == com.monopolyfun.modules.post.domain.ListingKind.REVIEW) {
                actions.add(action("submit_proof", "提交评审证明", "/api/v1/work/items/wb-submit-proof-" + order.orderNo() + "/receipt",
                        "primary", "fulfiller", "review_proof_required", null, false, true, "normal"));
                return actions;
            }
            if ("instant_fulfillment".equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")))) {
                actions.add(action("retry_instant_fulfillment", "重试直接发货",
                        "/api/v1/work/orders/" + order.orderNo() + "/instant-fulfillment/retry", "primary", order.roleFor(currentAccountId),
                        "instant_fulfillment_failed_retry", paymentBlocker, order.settlementType() == SettlementType.MONEY, true, "warning"));
                return actions;
            }
            if ("stock_fulfillment".equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")))) {
                return actions;
            }
            // 中文注释：普通订单默认走提交交付结果，进度和代码会话只是执行辅助入口，最终验收统一读取 delivery result。
            if (!"instant_fulfillment".equalsIgnoreCase(String.valueOf(order.metadata().get("fulfillmentMode")))) {
                actions.add(action("submit_progress", "提交阶段进度", "/api/v1/work/items/wb-delivery-result-" + order.orderNo() + "/progress",
                        "secondary", "fulfiller", "fulfiller_progress_due", paymentBlocker, order.settlementType() == SettlementType.MONEY, false, "normal"));
            }
            if (order.postKind() == com.monopolyfun.modules.post.domain.PostKind.PROJECT) {
                // 中文注释：project 代码任务先显式申请 repo delivery session，OpenClaw 再用短期 token 去 clone / push / PR。
                actions.add(action("start_project_code_delivery", "启动代码交付", "/api/v1/work/repo-delivery-sessions",
                        "secondary", "fulfiller", "project_repo_delivery_session_required", paymentBlocker, order.settlementType() == SettlementType.MONEY, false, "normal"));
            }
            actions.add(action("submit_delivery_result", "提交交付结果",
                    "/api/v1/work/items/wb-delivery-result-" + order.orderNo() + "/receipt", "primary", "fulfiller",
                    "delivery_result_due", paymentBlocker, order.settlementType() == SettlementType.MONEY, true, "normal"));
        }
        if (order.status() == OrderStatus.DELIVERED) {
            addPaymentActionIfNeeded(actions, order, currentAccountId);
            if (currentAccountId.equals(order.acceptorAccountId()) || organizationAuthorityService.canReviewOrder(currentAccountId, order)) {
                String actorRole = currentAccountId.equals(order.acceptorAccountId()) ? OrderEntity.ROLE_PAYER : OrderEntity.ROLE_AUTHORITY;
                PaymentBlockerAudience blockerAudience = currentAccountId.equals(order.acceptorAccountId()) ? PaymentBlockerAudience.PAYER : PaymentBlockerAudience.AUTHORITY;
                actions.add(action("accept_order", "验收并打开争议窗口", "/api/v1/work/items/wb-lead-review-" + order.orderNo() + "/review",
                        "primary", actorRole, actorRole + "_can_accept", paymentBlocker(order, blockerAudience), order.settlementType() == SettlementType.MONEY, true, "normal"));
                actions.add(action("open_dispute", "打开争议", "/api/v1/work/items/wb-lead-review-" + order.orderNo() + "/review",
                        "secondary", actorRole, actorRole + "_can_dispute", null, false, true, "danger"));
            } else if (order.hasParticipant(currentAccountId)) {
                actions.add(action("open_dispute", "打开争议", "/api/v1/work/items/wb-lead-review-" + order.orderNo() + "/review",
                        "secondary", order.roleFor(currentAccountId), "participant_can_dispute", null, false, true, "danger"));
            }
        }
        if (order.status() == OrderStatus.ACCEPTED_OPEN) {
            if (order.hasParticipant(currentAccountId) && OrderEntity.DISPUTE_WINDOW_OPEN.equalsIgnoreCase(order.disputeWindowStatus())) {
                actions.add(action("open_dispute", "打开争议", "/api/v1/work/items/wb-lead-review-" + order.orderNo() + "/review",
                        "secondary", order.roleFor(currentAccountId), "participant_can_dispute_window", null, false, true, "danger"));
            }
        }
        if (order.status() == OrderStatus.DISPUTED) {
            addDisputeActions(actions, order, currentAccountId);
        }
        return actions;
    }

    private void addPaymentActionIfNeeded(List<ActionView> actions, OrderEntity order, String accountId) {
        if (!accountId.equals(order.buyerAccountId()) || order.settlementType() != SettlementType.MONEY) {
            return;
        }
        if (order.status() != OrderStatus.CLAIMED && order.status() != OrderStatus.DELIVERED) {
            return;
        }
        var intent = paymentIntentRepository.findByOrderId(order.id()).orElse(null);
        if (intent == null || intent.status() == PaymentIntentStatus.PENDING || intent.status() == PaymentIntentStatus.FAILED) {
            actions.add(action("complete_money_payment", "完成资金支付", "/api/v1/payments/orders/" + order.orderNo() + "/intent",
                    "primary", OrderEntity.ROLE_PAYER, "payer_payment_required", null, true, false, "normal"));
            if (order.status() == OrderStatus.CLAIMED) {
                actions.add(action("abandon_payment", "放弃付款", "/api/v1/work/orders/" + order.orderNo() + "/abandon-payment",
                        "secondary", OrderEntity.ROLE_PAYER, "payer_can_abandon_unpaid_order", null, false, true, "warning"));
            }
        }
    }

    private void addDisputeActions(List<ActionView> actions, OrderEntity order, String accountId) {
        if (canCancelDispute(order, accountId)) {
            actions.add(action("cancel_dispute", "撤回争议，继续验收", "/api/v1/work/orders/" + order.orderNo() + "/cancel-dispute",
                    "primary", order.roleFor(accountId), "dispute_opener_can_cancel", null, false, true, "warning"));
        }
        if (order.hasParticipant(accountId) && canOpenAppeal(order)) {
            actions.add(action("open_appeal", "申请二审", "/api/v1/work/orders/" + order.orderNo() + "/appeal",
                    "secondary", order.roleFor(accountId), "review_submitted_appeal_available", null, false, true, "warning"));
        }
        if (!organizationAuthorityService.canResolveOrderDispute(accountId, order)) {
            return;
        }
        actions.add(action("assign_reviewer", "改派 reviewer", "/api/v1/work/orders/" + order.orderNo() + "/assign-reviewer",
                "secondary", OrderEntity.ROLE_AUTHORITY, "authority_can_assign_reviewer", null, false, true, "normal"));
        actions.add(action("override_accept_original", "终裁接受原交付", "/api/v1/work/orders/" + order.orderNo() + "/override-review",
                "primary", OrderEntity.ROLE_AUTHORITY, "authority_can_override_accept", paymentBlocker(order, PaymentBlockerAudience.AUTHORITY), order.settlementType() == SettlementType.MONEY, true, "warning"));
        actions.add(action("override_close_original", "终裁关闭原订单", "/api/v1/work/orders/" + order.orderNo() + "/override-review",
                "danger", OrderEntity.ROLE_AUTHORITY, "authority_can_override_close", null, false, true, "danger"));
    }

    private boolean canCancelDispute(OrderEntity order, String accountId) {
        if (accountId == null || !accountId.equals(order.disputeOpenedByAccountId())) {
            return false;
        }
        WorkItemEntity item = latestReviewWorkItem(order);
        return item == null || "ready".equalsIgnoreCase(item.status())
                || "claimed".equalsIgnoreCase(item.status())
                || "revision_requested".equalsIgnoreCase(item.status());
    }

    private boolean canOpenAppeal(OrderEntity order) {
        if (OrderEntity.REVIEW_STATUS_APPEAL_OPEN.equalsIgnoreCase(order.reviewStatus())) {
            return false;
        }
        WorkItemEntity item = latestReviewWorkItem(order);
        return item != null && ("submitted".equalsIgnoreCase(item.status())
                || "accepted".equalsIgnoreCase(item.status())
                || "disputed".equalsIgnoreCase(item.status()));
    }

    private WorkItemEntity latestReviewWorkItem(OrderEntity order) {
        return workRepository.findItemsBySource("order", order.orderNo()).stream()
                .filter(item -> "resolve_disputed_order".equals(item.outputSchema().get("action")))
                .findFirst()
                .orElse(null);
    }

    private String paymentBlocker(OrderEntity order, PaymentBlockerAudience audience) {
        if (order.settlementType() != SettlementType.MONEY) {
            return null;
        }
        var intent = paymentIntentRepository.findByOrderId(order.id()).orElse(null);
        // 中文注释：履约解锁以 capture 为准，authorized 只表示授权中，仍需要支付完成事件。
        if (intent != null && intent.status() == PaymentIntentStatus.CAPTURED) {
            return null;
        }
        // 中文注释：禁用原因按 postKind 翻译付款方，request 显示发布人，offer 显示买家。
        String payerLabel = payerLabel(order);
        return switch (audience) {
            case FULFILLER -> "等待" + payerLabel + "完成付款后再提交交付结果。";
            case PAYER -> "先完成付款，再验收交付结果。";
            case AUTHORITY -> "资金尚未完成。";
        };
    }

    private String payerLabel(OrderEntity order) {
        if (order.postKind() == null) {
            return "付款方";
        }
        return switch (order.postKind()) {
            case REQUEST -> "发布人";
            case OFFER -> "买家";
            case PROJECT -> "验收方";
            default -> "付款方";
        };
    }

    private ActionView action(
            String id,
            String label,
            String href,
            String importance,
            String role,
            String reasonCode,
            String disabledReason,
            boolean requiresPayment,
            boolean requiresProof,
            String dangerLevel) {
        // 中文注释：前端订单页只渲染这里返回的动作，禁用原因和表单语义都由后端策略统一发出。
        return new ActionView(id, label, "POST", href, importance, role, reasonCode, disabledReason, requiresPayment, requiresProof, dangerLevel);
    }

    private enum PaymentBlockerAudience {
        FULFILLER,
        PAYER,
        AUTHORITY
    }
}
