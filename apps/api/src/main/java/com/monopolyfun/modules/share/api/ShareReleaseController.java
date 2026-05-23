package com.monopolyfun.modules.share.api;

import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.share.service.ShareReleaseService;
import com.monopolyfun.modules.share.service.view.ShareReleaseRequestView;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/share-release-requests")
public class ShareReleaseController {
    private final ShareReleaseService shareReleaseService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;

    public ShareReleaseController(
            ShareReleaseService shareReleaseService,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory) {
        this.shareReleaseService = shareReleaseService;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
    }

    @GetMapping("/pending/me")
    public List<ShareReleaseRequestView> listMyPendingShareReleaseRequests(
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        return shareReleaseService.pendingForApprover(accountId).stream()
                .map(com.monopolyfun.modules.share.service.mapper.ShareViewMapper::shareReleaseRequest)
                .map(view -> shareReleaseView(view, accountId, includeAgent))
                .toList();
    }

    @GetMapping("/{requestId}")
    public ShareReleaseRequestView getShareReleaseRequest(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        ShareReleaseRequestView view = com.monopolyfun.modules.share.service.mapper.ShareViewMapper.shareReleaseRequest(shareReleaseService.getRequest(requestId));
        // 中文注释：ledger 卡片只向项目参与者展示分红审批状态，避免把项目治理闸门暴露给无关账号。
        organizationAuthorityService.requireProjectCapability(accountId, view.projectId(), ProjectCapability.PROJECT_PARTICIPATE);
        return shareReleaseView(view, accountId, includeAgent);
    }

    @PostMapping("/{requestId}/approve")
    public ShareReleaseRequestView approveShareReleaseRequest(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        String accountId = currentAccountAccess.requireAccountId();
        return shareReleaseView(
                com.monopolyfun.modules.share.service.mapper.ShareViewMapper.shareReleaseRequest(shareReleaseService.approveRequest(requestId, accountId)),
                accountId,
                includeAgent);
    }

    private ShareReleaseRequestView shareReleaseView(ShareReleaseRequestView view, String accountId, boolean includeAgent) {
        if (!includeAgent || view == null) {
            return view;
        }
        var assignedRoles = organizationAuthorityService.assignedRoleCodes(view.projectId(), accountId);
        // 中文注释：Share release 的可执行能力来自项目审批职位，agent 只看到当前账号剩余可签动作。
        return view.withAgentState(
                agentResourceKeyFactory.shareReleaseRequest(view.id()),
                agentCapabilityResolver.shareReleaseRequestCapabilities(view.status(), view.requiredRoleCodes(), view.approvedRoleCodes(), assignedRoles, accountId),
                agentCapabilityResolver.shareReleaseRequestBlockedCapabilities(view.status(), view.requiredRoleCodes(), view.approvedRoleCodes(), assignedRoles, accountId));
    }
}
