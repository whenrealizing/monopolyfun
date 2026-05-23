package com.monopolyfun.modules.work.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.modules.work.service.view.WorkItemActionView;
import com.monopolyfun.modules.work.service.view.WorkItemView;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemActionView;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemTargetView;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class WorkQueryService {
    private final CurrentAccountAccess currentAccountAccess;
    private final AccountRepository accountRepository;
    private final WorkRepository workRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final OrderRepository orderRepository;

    public WorkQueryService(
            CurrentAccountAccess currentAccountAccess,
            AccountRepository accountRepository,
            WorkRepository workRepository,
            OrganizationAuthorityService organizationAuthorityService,
            OrderRepository orderRepository) {
        this.currentAccountAccess = currentAccountAccess;
        this.accountRepository = accountRepository;
        this.workRepository = workRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.orderRepository = orderRepository;
    }

    public List<WorkItemView> listCurrentAccountWorkItems() {
        AccountEntity account = requireCurrentAccount();
        workRepository.releaseExpiredClaims(java.time.Instant.now());
        // 中文注释：查询只读取 WorkItem 事实，旧 source provider 由维护任务写入，避免读路径继续承载兼容同步。
        return workRepository.findItemsByAccountId(account.id()).stream()
                .filter(this::hasActiveSource)
                .map(this::toView)
                .toList();
    }

    public WorkItemView requireAccessibleWorkItem(String itemNoOrId) {
        AccountEntity account = requireCurrentAccount();
        WorkItemEntity item = workRepository.findItemByNoOrId(itemNoOrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        if (account.id().equals(item.accountId()) || canReviewProjectRoleItem(account.id(), item)) {
            // 中文注释：验收账号可读跨账号授权任务状态，用于 agent 验收后读回 accepted/closed 结果。
            return toView(item);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Work item belongs to another account");
    }

    public WorkbenchItemView requireCurrentAccountWorkbenchItem(String itemNoOrId) {
        return listCurrentAccountWorkbenchItems().stream()
                .filter(item -> item.id().equals(itemNoOrId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workbench item not found"));
    }

    public List<WorkbenchItemView> listCurrentAccountWorkbenchItems() {
        AccountEntity account = requireCurrentAccount();
        List<WorkbenchItemView> ownedItems = listCurrentAccountWorkItems().stream()
                .filter(item -> !isPostOperationsReminder(item))
                .map(this::toWorkbenchView)
                .toList();
        // 中文注释：授权任务提交后进入独立验收队列，执行人账号保留结果状态，协议维护和任务负责人获得 review action。
        List<WorkbenchItemView> reviewItems = workRepository.findSubmittedProjectRoleItems().stream()
                .filter(item -> !account.id().equals(item.accountId()))
                .filter(item -> canReviewProjectRoleItem(account.id(), item))
                .map(this::toView)
                .filter(item -> !isPostOperationsReminder(item))
                .map(this::toWorkbenchView)
                .toList();
        return java.util.stream.Stream.concat(ownedItems.stream(), reviewItems.stream()).toList();
    }

    private boolean isPostOperationsReminder(WorkItemView item) {
        return switch (reason(item)) {
            case "expand_offer_supply", "promote_request", "promote_project" -> true;
            default -> false;
        };
    }

    private boolean hasActiveSource(WorkItemEntity item) {
        if (!"order".equals(item.sourceType())) {
            return true;
        }
        var order = orderRepository.findByOrderNo(item.sourceId());
        if (order == null || order.isEmpty()) {
            return true;
        }
        return order.get().status() != OrderStatus.FINAL_CLOSED && order.get().status() != OrderStatus.FINAL_ACCEPTED;
    }

    public WorkItemEntity requireCurrentAccountWorkItem(String itemNoOrId) {
        String accountId = currentAccountAccess.requireAccountId();
        workRepository.releaseExpiredClaims(java.time.Instant.now());
        WorkItemEntity item = workRepository.findItemByNoOrId(itemNoOrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        // 中文注释：命令入口先解析真实 WorkItem，再返回权限错误，方便业务测试区分不存在与越权操作。
        if (!accountId.equals(item.accountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Work item belongs to another account");
        }
        return item;
    }

    private WorkItemView toView(WorkItemEntity item) {
        return new WorkItemView(
                item.id(),
                item.itemNo(),
                item.title(),
                item.goal(),
                item.status(),
                item.sourceType(),
                item.sourceId(),
                item.urgency(),
                item.requiredRole(),
                item.requiredCapability(),
                item.claimExpiresAt() == null ? null : item.claimExpiresAt().toString(),
                item.acceptanceCriteria(),
                item.inputRefs(),
                item.outputSchema(),
                actions(item),
                item.updatedAt().toString());
    }

    private WorkbenchItemView toWorkbenchView(WorkItemView item) {
        List<String> filterTags = filterTags(item);
        String targetHref = targetHref(item);
        return new WorkbenchItemView(
                item.itemNo(),
                item.title(),
                item.goal(),
                item.requiredRole() == null ? "worker" : item.requiredRole(),
                item.urgency(),
                reason(item),
                filterTags.isEmpty() ? "all" : filterTags.getFirst(),
                filterTags,
                filterTags.isEmpty() ? "all" : filterTags.getFirst(),
                domain(item),
                actionKind(item),
                targetHref,
                summaryFacts(item),
                canDismiss(item),
                new WorkbenchItemTargetView(item.sourceType(), item.sourceId()),
                item.actions().stream().map(action -> workbenchAction(action, targetHref)).toList(),
                item.actions().stream()
                        .map(WorkItemActionView::id)
                        .filter(action -> action.endsWith("work_item") || action.endsWith("progress") || action.endsWith("receipt") || action.endsWith("help") || action.endsWith("run"))
                        .toList(),
                item.acceptanceCriteria(),
                nextAction(item),
                item.requiredCapability(),
                item.requiredRole(),
                item.updatedAt());
    }

    private List<WorkItemActionView> actions(WorkItemEntity item) {
        List<WorkItemActionView> actions = new ArrayList<>();
        actions.add(new WorkItemActionView("open", "进入处理"));
        if (canDismiss(item)) {
            actions.add(new WorkItemActionView("dismiss", "隐藏"));
        }
        if ("ready".equals(item.status()) && !canDismiss(item) && !"project_role_invite".equals(item.sourceType())) {
            actions.add(new WorkItemActionView("claim_work_item", "领取任务"));
        }
        if ("project_role_invite".equals(item.sourceType()) && "ready".equals(item.status())) {
            // 中文注释：项目邀请直接落到被邀请人的 workbench，个人 agent 通过统一 action card 完成入职闭环。
            actions.add(new WorkItemActionView("accept_project_invite", "接受邀请"));
            actions.add(new WorkItemActionView("decline_project_invite", "婉拒邀请"));
        }
        if ("claimed".equals(item.status()) || "revision_requested".equals(item.status())) {
            actions.add(new WorkItemActionView("submit_progress", "提交进度"));
            actions.add(new WorkItemActionView("submit_receipt", "提交结果"));
            actions.add(new WorkItemActionView("request_help", "请求协助"));
            if (!"project_role_task".equals(item.sourceType())) {
                actions.add(new WorkItemActionView("close_work_run", "关闭执行"));
            }
        }
        if ("submitted".equals(item.status()) && canCurrentAccountReview(item)) {
            actions.add(new WorkItemActionView("review_receipt", "验收结果"));
            actions.add(new WorkItemActionView("revise_receipt", "要求返工"));
        }
        if ("submitted".equals(item.status()) && !"project_role_task".equals(item.sourceType())) {
            actions.add(new WorkItemActionView("close_work_run", "关闭执行"));
        }
        return List.copyOf(actions);
    }

    private WorkbenchItemActionView workbenchAction(WorkItemActionView action, String targetHref) {
        String mode = actionMode(action.id());
        return new WorkbenchItemActionView(
                action.id(),
                action.label(),
                mode,
                actionRequiredInputs(action.id()),
                "navigate".equals(mode) || "form".equals(mode) ? targetHref : null,
                actionDestructive(action.id()));
    }

    private String actionMode(String actionId) {
        if ("open".equals(actionId)) {
            return "navigate";
        }
        if ("dismiss".equals(actionId) || "accept_project_invite".equals(actionId) || "decline_project_invite".equals(actionId)) {
            return "direct";
        }
        return "form";
    }

    private List<String> actionRequiredInputs(String actionId) {
        return switch (actionId) {
            case "claim_work_item" -> List.of("actorAccountId");
            case "submit_progress" -> List.of("actorAccountId", "stepTitle", "summary");
            case "submit_receipt" -> List.of("actorAccountId", "summary");
            case "request_help" -> List.of("actorAccountId", "reason");
            case "review_receipt" -> List.of("reviewerAccountId", "decision", "reason");
            case "revise_receipt" -> List.of("reviewerAccountId", "reason");
            case "close_work_run" -> List.of("actorAccountId", "reason");
            default -> List.of();
        };
    }

    private boolean actionDestructive(String actionId) {
        return "decline_project_invite".equals(actionId)
                || "revise_receipt".equals(actionId)
                || "close_work_run".equals(actionId)
                || "request_help".equals(actionId);
    }

    private boolean canCurrentAccountReview(WorkItemEntity item) {
        // 中文注释：授权任务的执行者提交结果后，由验收账号跨账号审核，执行者 workbench 展示执行结果。
        return !"project_role_task".equals(item.sourceType())
                || !currentAccountAccess.requireAccountId().equals(item.accountId());
    }

    private boolean canReviewProjectRoleItem(String accountId, WorkItemEntity item) {
        Object projectId = item.outputSchema().get("projectId");
        return projectId instanceof String id
                && !id.isBlank()
                && organizationAuthorityService.hasProjectCapability(accountId, id, ProjectCapability.MARKET_QUALITY_MANAGE);
    }

    private Map<String, Object> nextAction(WorkItemView item) {
        // 中文注释：Workbench 投影直接携带 Work API 操作入口，agent 只需当前 item 投影即可完成写动作。
        java.util.LinkedHashMap<String, Object> action = new java.util.LinkedHashMap<>(item.outputSchema());
        action.put("claim", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/claim"));
        action.put("submitProgress", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/progress"));
        action.put("submitReceipt", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/receipt"));
        action.put("requestHelp", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/help"));
        action.put("reviewReceipt", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/review"));
        action.put("reviseReceipt", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/revise"));
        action.put("closeRun", Map.of("method", "POST", "path", "/api/v1/work/items/" + item.itemNo() + "/close"));
        return action;
    }

    private boolean canDismiss(WorkItemView item) {
        return false;
    }

    private boolean canDismiss(WorkItemEntity item) {
        return false;
    }

    private String reason(WorkItemView item) {
        Object action = item.outputSchema().get("action");
        if (action instanceof String value && !value.isBlank()) {
            return value;
        }
        return "execute_work_item";
    }

    private List<String> filterTags(WorkItemView item) {
        String lane = item.requiredRole() == null ? "worker" : item.requiredRole();
        String reason = reason(item);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if ("payer".equals(lane) || "complete_money_payment".equals(reason) || "lead_accept_or_dispute".equals(reason)) {
            tags.add("bought");
        }
        if ("fulfiller".equals(lane) || "worker".equals(lane) || "submit_worker_proof".equals(reason) || "auto_delivery_pending".equals(reason)) {
            tags.add("sold");
        }
        if ("lead".equals(lane) || "ecosystem".equals(lane) || "expand_offer_supply".equals(reason) || "promote_request".equals(reason) || "promote_project".equals(reason)) {
            tags.add("published");
        }
        if ("reviewer".equals(lane) || "review".equals(lane) || "review_disputed_order".equals(reason)) {
            tags.add("reviewing");
        }
        if ("settlement".equals(lane) || "share_release_approval".equals(reason)) {
            tags.add("approving");
        }
        return List.copyOf(tags);
    }

    private String domain(WorkItemView item) {
        return switch (item.sourceType()) {
            case "order" -> "order";
            case "offer", "request" -> "market";
            case "project", "project_initiative", "project_role_invite", "project_role_task", "project_ci_check",
                 "project_pr", "project_memory" -> "project";
            case "share_release_request" -> "governance";
            default -> "work";
        };
    }

    private String actionKind(WorkItemView item) {
        String reason = reason(item);
        if (reason.contains("payment")) return "pay";
        if (reason.contains("delivery") || reason.contains("proof") || reason.contains("progress") || reason.contains("receipt"))
            return "deliver";
        if (reason.contains("accept") || reason.contains("review") || reason.contains("revise") || reason.contains("dispute"))
            return "review";
        if (reason.contains("approval")) return "approve";
        if (reason.contains("invite")) return "invite";
        if (reason.contains("ci") || reason.contains("pr") || reason.contains("memory") || reason.contains("source"))
            return "maintain";
        if (reason.contains("promote") || reason.contains("expand")) return "publish";
        return "execute";
    }

    private String targetHref(WorkItemView item) {
        String sourceId = item.sourceId();
        String reason = reason(item);
        String orderNo = stringValue(item.outputSchema().get("orderNo"));
        if (orderNo != null && shouldOpenOrderDetail(item, reason)) {
            return "/orders/" + path(orderNo);
        }
        return switch (item.sourceType()) {
            case "order" -> reason.contains("dispute") || "resolve_disputed_order".equals(reason)
                    ? "/orders/" + path(sourceId) + "/dispute"
                    : "/orders/" + path(sourceId);
            case "offer" -> "/market/offers/" + path(sourceId);
            case "request" -> "/market/requests/" + path(sourceId);
            case "project" -> "/market/projects/" + path(sourceId);
            case "project_initiative", "project_role_invite", "project_role_task" -> projectHref(item);
            case "share_release_request" -> "/shares";
            case "project_ci_check", "project_pr", "project_memory" -> projectHref(item);
            default -> null;
        };
    }

    private boolean shouldOpenOrderDetail(WorkItemView item, String reason) {
        String lane = item.requiredRole() == null ? "" : item.requiredRole();
        return "order".equals(item.sourceType())
                || "fulfiller".equals(lane)
                || reason.contains("delivery")
                || reason.contains("proof")
                || reason.contains("receipt")
                || reason.contains("progress");
    }

    private String projectHref(WorkItemView item) {
        String projectNo = stringValue(item.outputSchema().get("projectNo"));
        if (projectNo == null && "project".equals(item.sourceType())) {
            projectNo = item.sourceId();
        }
        if (projectNo == null) {
            return null;
        }
        return "/market/projects/" + path(projectNo);
    }

    private List<Map<String, String>> summaryFacts(WorkItemView item) {
        List<Map<String, String>> facts = new ArrayList<>();
        String orderNo = stringValue(item.outputSchema().get("orderNo"));
        if ("order".equals(item.sourceType())) {
            addFact(facts, "itemTitle", stringValue(item.outputSchema().get("itemTitle")));
            addFact(facts, "amount", moneyValue(item.outputSchema().get("amount"), item.outputSchema().get("currency")));
            addFact(facts, "orderNo", orderNo == null ? item.sourceId() : orderNo);
        }
        addFact(facts, "target", item.sourceType() + ":" + item.sourceId());
        addFact(facts, "role", "order".equals(item.sourceType()) ? reason(item) : item.requiredRole());
        addFact(facts, "capability", item.requiredCapability());
        addFact(facts, "urgency", item.urgency());
        addFact(facts, "projectNo", stringValue(item.outputSchema().get("projectNo")));
        addFact(facts, "runId", stringValue(item.outputSchema().get("runId")));
        addFact(facts, "prNumber", stringValue(item.outputSchema().get("prNumber")));
        addFact(facts, "checkName", stringValue(item.outputSchema().get("checkName")));
        return List.copyOf(facts);
    }

    private String moneyValue(Object amount, Object currency) {
        String amountText = stringValue(amount);
        if (amountText == null) {
            return null;
        }
        String currencyText = stringValue(currency);
        return currencyText == null ? amountText : amountText + " " + currencyText;
    }

    private void addFact(List<Map<String, String>> facts, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Map<String, String> fact = new LinkedHashMap<>();
        fact.put("key", key);
        fact.put("value", value);
        facts.add(Map.copyOf(fact));
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String path(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private AccountEntity requireCurrentAccount() {
        String accountId = currentAccountAccess.requireAccountId();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }
}
