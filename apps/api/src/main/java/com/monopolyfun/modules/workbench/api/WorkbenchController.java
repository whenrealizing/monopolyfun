package com.monopolyfun.modules.workbench.api;

import com.monopolyfun.modules.project.service.ProjectRoleInviteService;
import com.monopolyfun.modules.workbench.api.request.WaitWorkbenchRequest;
import com.monopolyfun.modules.workbench.service.query.WorkbenchQueryService;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemView;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {
    private final WorkbenchQueryService workbenchQueryService;
    private final ProjectRoleInviteService projectRoleInviteService;
    private final CurrentAccountAccess currentAccountAccess;

    public WorkbenchController(
            WorkbenchQueryService workbenchQueryService,
            ProjectRoleInviteService projectRoleInviteService,
            CurrentAccountAccess currentAccountAccess) {
        this.workbenchQueryService = workbenchQueryService;
        this.projectRoleInviteService = projectRoleInviteService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping
    public List<WorkbenchItemView> listWorkbenchItems() {
        return workbenchQueryService.listCurrentAccountItems();
    }

    @PostMapping("/wait")
    public List<WorkbenchItemView> waitWorkbenchItems(@RequestBody(required = false) WaitWorkbenchRequest request) {
        // 中文注释：工作台 wait 入口改成一次性读取，避免 servlet 线程被长轮询占用。
        return workbenchQueryService.listCurrentAccountItems();
    }

    @PostMapping("/{itemId}/dismiss")
    public WorkbenchItemView dismissWorkbenchItem(@PathVariable String itemId) {
        // 中文注释：页面级 agent 删除后，工作台隐藏动作走稳定业务入口，保留用户偏好语义。
        return workbenchQueryService.dismissCurrentAccountItem(itemId);
    }

    @PostMapping("/{itemId}/project-invite/accept")
    public CommandReceipt acceptProjectInvite(@PathVariable String itemId) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        return projectRoleInviteService.accept(itemId, actorAccountId);
    }

    @PostMapping("/{itemId}/project-invite/decline")
    public CommandReceipt declineProjectInvite(@PathVariable String itemId) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        return projectRoleInviteService.decline(itemId, actorAccountId);
    }
}
