package com.monopolyfun.modules.project.api;

import com.monopolyfun.modules.project.api.request.ProjectAgentActionRequest;
import com.monopolyfun.modules.project.service.ProjectAgentProtocolService;
import com.monopolyfun.modules.project.service.view.ProjectAgentActionResultView;
import com.monopolyfun.modules.project.service.view.ProjectAgentInboxView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectNo}/agent")
public class ProjectAgentProtocolController {
    private final ProjectAgentProtocolService agentProtocolService;
    private final CurrentAccountAccess currentAccountAccess;

    public ProjectAgentProtocolController(ProjectAgentProtocolService agentProtocolService, CurrentAccountAccess currentAccountAccess) {
        this.agentProtocolService = agentProtocolService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping("/inbox")
    public ProjectAgentInboxView inbox(@PathVariable String projectNo) {
        return agentProtocolService.inbox(projectNo, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/actions")
    public ProjectAgentActionResultView action(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectAgentActionRequest request) {
        return agentProtocolService.handleAction(projectNo, request, currentAccountAccess.requireAccountId());
    }
}
