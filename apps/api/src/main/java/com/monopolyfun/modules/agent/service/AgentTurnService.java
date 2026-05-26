package com.monopolyfun.modules.agent.service;

import com.monopolyfun.modules.agent.api.request.AgentSubject;
import com.monopolyfun.modules.agent.api.request.AgentTurnRequest;
import com.monopolyfun.modules.agent.service.view.AgentActionCard;
import com.monopolyfun.modules.agent.service.view.AgentApiOperation;
import com.monopolyfun.modules.agent.service.view.AgentTurnPointer;
import com.monopolyfun.modules.agent.service.view.AgentTurnProjection;
import com.monopolyfun.modules.agent.service.view.AgentTurnResult;
import com.monopolyfun.modules.workbench.service.query.WorkbenchQueryService;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemActionView;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemView;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentTurnService {
    private static final String INTENT_VIEW = "view";
    private static final String SCENE_HOME = "home";
    private static final String SCENE_WORKBENCH = "workbench";

    private final CurrentAccountAccess currentAccountAccess;
    private final WorkbenchQueryService workbenchQueryService;

    public AgentTurnService(CurrentAccountAccess currentAccountAccess, WorkbenchQueryService workbenchQueryService) {
        this.currentAccountAccess = currentAccountAccess;
        this.workbenchQueryService = workbenchQueryService;
    }

    public AgentTurnResult turn(AgentTurnRequest request) {
        String intent = normalize(request.intent(), INTENT_VIEW);
        if (!INTENT_VIEW.equals(intent)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported agent turn intent");
        }

        String scene = normalize(request.scene(), SCENE_HOME);
        return switch (scene) {
            case SCENE_HOME -> homeTurn(request, scene);
            case SCENE_WORKBENCH -> workbenchTurn(request, scene);
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported agent turn scene");
        };
    }

    private AgentTurnResult homeTurn(AgentTurnRequest request, String scene) {
        CurrentAccount account = currentAccountAccess.current()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        // 中文注释：home turn 是 OpenClaw 的启动投影，只暴露账号、入口和下一步，降低运行时理解成本。
        AgentTurnProjection projection = new AgentTurnProjection(
                orderedMap(
                        "accountId", account.accountId(),
                        "handle", account.handle(),
                        "displayName", account.displayName(),
                        "status", "ready"),
                orderedMap(
                        "workbench", "/api/v1/workbench",
                        "projects", "/api/v1/projects",
                        "offers", "/api/v1/offers",
                        "requests", "/api/v1/requests"),
                orderedMap(),
                orderedMap());
        AgentTurnPointer workbenchPointer = pointer(INTENT_VIEW, SCENE_WORKBENCH, null, null);
        List<AgentActionCard> actions = List.of(
                navigationAction("open_workbench", "查看工作台", "读取当前账号可处理的待办。", workbenchPointer),
                navigationAction("browse_projects", "浏览项目", "读取可参与项目列表。", null));
        return result(request, scene, null, "ready", actions, projection, workbenchPointer);
    }

    private AgentTurnResult workbenchTurn(AgentTurnRequest request, String scene) {
        List<WorkbenchItemView> items = workbenchQueryService.listCurrentAccountItems();
        WorkbenchItemView current = chooseCurrent(request.subject(), items);
        // 中文注释：workbench turn 将业务待办压缩成 current + actions，OpenClaw 直接读取 action card 决定下一步 API。
        AgentTurnProjection projection = new AgentTurnProjection(
                orderedMap(
                        "status", items.isEmpty() ? "empty" : "ready",
                        "message", items.isEmpty() ? "当前账号暂无待办" : "当前账号存在可处理待办"),
                orderedMap(
                        "list", "/api/v1/workbench",
                        "wait", "/api/v1/workbench/wait"),
                orderedMap(
                        "items", items.size(),
                        "urgent", countByUrgency(items, "urgent"),
                        "attention", countByUrgency(items, "attention")),
                current == null ? orderedMap() : currentProjection(current));
        AgentSubject subject = current == null ? request.subject() : new AgentSubject("workbench_item", current.id());
        List<AgentActionCard> actions = current == null
                ? List.of(navigationAction("refresh_workbench", "刷新工作台", "重新读取当前账号待办。", pointer(INTENT_VIEW, SCENE_WORKBENCH, null, null)))
                : current.actions().stream().map(action -> actionCard(current, action)).toList();
        return result(request, scene, subject, current == null ? "idle" : "ready", actions, projection, null);
    }

    private WorkbenchItemView chooseCurrent(AgentSubject subject, List<WorkbenchItemView> items) {
        if (subject != null && subject.id() != null && !subject.id().isBlank()) {
            return items.stream()
                    .filter(item -> item.id().equals(subject.id()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workbench item not found"));
        }
        return items.isEmpty() ? null : items.getFirst();
    }

    private AgentActionCard actionCard(WorkbenchItemView item, WorkbenchItemActionView action) {
        AgentTurnPointer nextTurn = pointer(INTENT_VIEW, SCENE_WORKBENCH, new AgentSubject("workbench_item", item.id()), null);
        return new AgentActionCard(
                action.id(),
                action.label(),
                plainInstruction(action),
                nextExpected(action),
                destructive(action) ? "high" : "normal",
                apiOperation(item, action),
                orderedMap(
                        "requiredInputs", action.requiredInputs(),
                        "targetHref", action.targetHref(),
                        "mode", action.mode()),
                inputTemplate(action),
                orderedMap("required", action.requiredInputs()),
                nextTurn,
                destructive(action) ? "state_change" : "routine",
                destructive(action),
                destructive(action) ? "该动作会关闭、返工、婉拒或请求协助，需要先确认业务证据。" : null,
                orderedMap("evidence", action.requiredInputs()));
    }

    private AgentApiOperation apiOperation(WorkbenchItemView item, WorkbenchItemActionView action) {
        return switch (action.id()) {
            case "dismiss" -> operation("dismissWorkbenchItem", "POST", "/api/v1/workbench/{itemId}/dismiss", "itemId", item.id());
            case "accept_project_invite" -> operation("acceptProjectInvite", "POST", "/api/v1/workbench/{itemId}/project-invite/accept", "itemId", item.id());
            case "decline_project_invite" -> operation("declineProjectInvite", "POST", "/api/v1/workbench/{itemId}/project-invite/decline", "itemId", item.id());
            case "claim_work_item" -> operation("claimWorkItem", "POST", "/api/v1/work/items/{itemId}/claim", "itemId", item.id());
            case "submit_progress" -> operation("submitWorkProgress", "POST", "/api/v1/work/items/{itemId}/progress", "itemId", item.id());
            case "submit_receipt" -> operation("submitWorkReceipt", "POST", "/api/v1/work/items/{itemId}/receipt", "itemId", item.id());
            case "request_help" -> operation("requestWorkHelp", "POST", "/api/v1/work/items/{itemId}/help", "itemId", item.id());
            case "review_receipt" -> operation("reviewWorkReceipt", "POST", "/api/v1/work/items/{itemId}/review", "itemId", item.id());
            case "revise_receipt" -> operation("reviseWorkReceipt", "POST", "/api/v1/work/items/{itemId}/revise", "itemId", item.id());
            case "close_work_run" -> operation("closeWorkRun", "POST", "/api/v1/work/items/{itemId}/close", "itemId", item.id());
            default -> null;
        };
    }

    private AgentApiOperation operation(String operationId, String method, String path, String paramName, String paramValue) {
        return new AgentApiOperation(operationId, method, path, orderedMap(paramName, paramValue), orderedMap());
    }

    private Map<String, Object> currentProjection(WorkbenchItemView item) {
        return orderedMap(
                "id", item.id(),
                "title", item.title(),
                "description", item.description(),
                "lane", item.lane(),
                "urgency", item.urgency(),
                "reason", item.reason(),
                "category", item.category(),
                "roleBucket", item.roleBucket(),
                "domain", item.domain(),
                "actionKind", item.actionKind(),
                "targetHref", item.targetHref(),
                "summaryFacts", item.summaryFacts(),
                "target", item.target(),
                "requiredInputs", item.requiredInputs(),
                "acceptanceCriteria", item.acceptanceCriteria(),
                "nextAction", item.nextAction(),
                "requiredCapability", item.requiredCapability(),
                "requiredRoleCode", item.requiredRoleCode(),
                "updatedAt", item.updatedAt());
    }

    private Map<String, Object> inputTemplate(WorkbenchItemActionView action) {
        Map<String, Object> template = new LinkedHashMap<>();
        for (String input : action.requiredInputs()) {
            template.put(input, "");
        }
        return template;
    }

    private String plainInstruction(WorkbenchItemActionView action) {
        return switch (action.id()) {
            case "open" -> "打开业务详情页并读取上下文。";
            case "dismiss" -> "隐藏这条可选提醒。";
            case "claim_work_item" -> "领取当前任务并进入执行。";
            case "submit_progress" -> "提交当前任务的进度记录。";
            case "submit_receipt" -> "提交当前任务的结果和证据。";
            case "request_help" -> "说明阻塞原因并请求协助。";
            case "review_receipt" -> "验收当前提交结果。";
            case "revise_receipt" -> "要求执行者返工。";
            case "close_work_run" -> "关闭当前执行记录。";
            case "accept_project_invite" -> "接受项目角色邀请。";
            case "decline_project_invite" -> "婉拒项目角色邀请。";
            default -> action.label();
        };
    }

    private String nextExpected(WorkbenchItemActionView action) {
        if ("open".equals(action.id())) {
            return "读取详情页后选择业务写动作。";
        }
        return "执行 API 后重新读取 workbench turn 校验状态变化。";
    }

    private boolean destructive(WorkbenchItemActionView action) {
        return action.destructive();
    }

    private AgentActionCard navigationAction(String id, String label, String instruction, AgentTurnPointer nextTurn) {
        return new AgentActionCard(
                id,
                label,
                instruction,
                "跟随 nextTurn 或 refs 继续读取。",
                "normal",
                null,
                orderedMap(),
                orderedMap(),
                orderedMap(),
                nextTurn,
                "navigation",
                false,
                null,
                orderedMap());
    }

    private AgentTurnResult result(
            AgentTurnRequest request,
            String scene,
            AgentSubject subject,
            String state,
            List<AgentActionCard> actions,
            AgentTurnProjection projection,
            AgentTurnPointer nextTurn) {
        return new AgentTurnResult(
                request.turnId() == null || request.turnId().isBlank() ? "turn-" + UUID.randomUUID() : request.turnId(),
                scene,
                subject,
                state,
                actions,
                orderedMap(),
                List.of(),
                projection,
                null,
                nextTurn);
    }

    private AgentTurnPointer pointer(String intent, String scene, AgentSubject subject, String actionId) {
        return new AgentTurnPointer(intent, scene, subject, actionId, orderedMap());
    }

    private long countByUrgency(List<WorkbenchItemView> items, String urgency) {
        return items.stream().filter(item -> urgency.equals(item.urgency())).count();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Map<String, Object> orderedMap(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object key = entries[index];
            Object value = entries[index + 1];
            if (key != null && value != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }
}
