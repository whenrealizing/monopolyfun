package com.monopolyfun.modules.risk.api;

import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.risk.api.request.ManualRiskActionRequest;
import com.monopolyfun.modules.risk.service.RiskCenterService;
import com.monopolyfun.modules.risk.service.view.RiskAccountView;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/backoffice/risk")
public class RiskCenterController {
    private final RiskCenterService riskCenterService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;

    public RiskCenterController(
            RiskCenterService riskCenterService,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService) {
        this.riskCenterService = riskCenterService;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
    }

    @GetMapping("/accounts")
    @Operation(operationId = "listRiskAccounts")
    public PageResult<RiskAccountView> listAccounts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        requireRiskView();
        return riskCenterService.listAccounts(status, riskLevel, q, PageQuery.of(limit, cursor));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(operationId = "getRiskAccount")
    public RiskAccountView getAccount(@PathVariable String accountId) {
        requireRiskView();
        return riskCenterService.getAccount(accountId);
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @Operation(operationId = "freezeRiskAccount")
    public RiskAccountView freezeAccount(@PathVariable String accountId, @Valid @RequestBody ManualRiskActionRequest request) {
        String actorAccountId = requireRiskManage();
        int freezeHours = request.freezeHours() == null ? 24 : request.freezeHours();
        return riskCenterService.freezeAccount(accountId, actorAccountId, request.reason(), Duration.ofHours(freezeHours));
    }

    @PostMapping("/accounts/{accountId}/unfreeze")
    @Operation(operationId = "unfreezeRiskAccount")
    public RiskAccountView unfreezeAccount(@PathVariable String accountId, @Valid @RequestBody ManualRiskActionRequest request) {
        String actorAccountId = requireRiskManage();
        return riskCenterService.unfreezeAccount(accountId, actorAccountId, request.reason());
    }

    @PostMapping("/accounts/{accountId}/ban")
    @Operation(operationId = "banRiskAccount")
    public RiskAccountView banAccount(@PathVariable String accountId, @Valid @RequestBody ManualRiskActionRequest request) {
        String actorAccountId = requireRiskManage();
        return riskCenterService.banAccount(accountId, actorAccountId, request.reason());
    }

    @PostMapping("/accounts/{accountId}/watch")
    @Operation(operationId = "watchRiskAccount")
    public RiskAccountView watchAccount(@PathVariable String accountId, @Valid @RequestBody ManualRiskActionRequest request) {
        String actorAccountId = requireRiskManage();
        return riskCenterService.watchAccount(accountId, actorAccountId, request.reason());
    }

    private String requireRiskManage() {
        String actorAccountId = currentAccountAccess.requireAccountId();
        // 中文注释：风控中心属于系统级安全操作面，统一绑定 Root Project 的风险管理能力。
        organizationAuthorityService.requireSystemCapability(actorAccountId, ProjectCapability.SECURITY_RISK_MANAGE);
        return actorAccountId;
    }

    private void requireRiskView() {
        String actorAccountId = currentAccountAccess.requireAccountId();
        // 中文注释：风控读取和风控处置分离，结算维护可以查看风险事实，冻结和封禁由协议维护处理。
        organizationAuthorityService.requireSystemCapability(actorAccountId, ProjectCapability.SECURITY_RISK_VIEW);
    }
}
