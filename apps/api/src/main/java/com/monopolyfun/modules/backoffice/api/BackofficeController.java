package com.monopolyfun.modules.backoffice.api;

import com.monopolyfun.modules.backoffice.service.query.BackofficeQueryService;
import com.monopolyfun.modules.backoffice.service.view.AuditEventView;
import com.monopolyfun.modules.backoffice.service.view.BackofficeDashboardView;
import com.monopolyfun.modules.delivery.domain.DeliveryAttemptEntity;
import com.monopolyfun.modules.identity.api.request.AdminIssuePasswordResetRequest;
import com.monopolyfun.modules.identity.api.response.AdminPasswordResetTokenResponse;
import com.monopolyfun.modules.identity.service.security.AuthService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.service.view.PaymentIntentView;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.risk.service.view.RiskEventView;
import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/backoffice")
public class BackofficeController {
    private final BackofficeQueryService backofficeQueryService;
    private final AuthService authService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;

    public BackofficeController(
            BackofficeQueryService backofficeQueryService,
            AuthService authService,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService) {
        this.backofficeQueryService = backofficeQueryService;
        this.authService = authService;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
    }

    @GetMapping
    @Operation(operationId = "getBackofficeDashboard")
    public BackofficeDashboardView getDashboard() {
        requireBackofficeView();
        return backofficeQueryService.getDashboard();
    }

    @GetMapping("/audit-events")
    @Operation(operationId = "listBackofficeAuditEvents")
    public List<AuditEventView> listAuditEvents(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentAuditEvents(limit);
    }

    @GetMapping("/risk-events")
    @Operation(operationId = "listBackofficeRiskEvents")
    public List<RiskEventView> listRiskEvents(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentRiskEvents(limit);
    }

    @GetMapping("/payment-intents")
    @Operation(operationId = "listBackofficePaymentIntents")
    public List<PaymentIntentView> listPaymentIntents(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentPaymentIntents(limit);
    }

    @GetMapping("/payment-provider-events")
    @Operation(operationId = "listBackofficePaymentProviderEvents")
    public List<PaymentProviderEventEntity> listPaymentProviderEvents(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentPaymentProviderEvents(limit);
    }

    @GetMapping("/settlement-events")
    @Operation(operationId = "listBackofficeSettlementEvents")
    public List<SettlementEventEntity> listSettlementEvents(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentSettlementEvents(limit);
    }

    @GetMapping("/delivery-attempts")
    @Operation(operationId = "listBackofficeDeliveryAttempts")
    public List<DeliveryAttemptEntity> listDeliveryAttempts(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentDeliveryAttempts(limit);
    }

    @GetMapping("/proof-assets")
    @Operation(operationId = "listBackofficeProofAssets")
    public List<ProofAssetView> listProofAssets(@RequestParam(defaultValue = "50") int limit) {
        requireBackofficeView();
        return backofficeQueryService.listRecentProofAssets(limit);
    }

    @PostMapping("/password-reset-tokens")
    @Operation(operationId = "issueBackofficePasswordResetToken")
    public AdminPasswordResetTokenResponse issuePasswordResetToken(@Valid @RequestBody AdminIssuePasswordResetRequest request) {
        requirePasswordResetIssue();
        return authService.issuePasswordResetTokenForBackoffice(request.handle());
    }

    private void requireBackofficeView() {
        // 中文注释：后台读取入口统一走 Root Project 能力，账号只承载登录身份。
        organizationAuthorityService.requireSystemCapability(currentAccountAccess.requireAccountId(), ProjectCapability.BACKOFFICE_VIEW);
    }

    private void requirePasswordResetIssue() {
        // 中文注释：密码重置 token 属于敏感写操作，HTTP 入口直接校验签发能力，保持与服务层策略一致。
        organizationAuthorityService.requireSystemCapability(currentAccountAccess.requireAccountId(), ProjectCapability.SECURITY_PASSWORD_RESET_ISSUE);
    }
}
