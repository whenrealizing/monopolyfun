package com.monopolyfun.modules.identity.service.security;

import com.monopolyfun.modules.identity.api.request.LoginRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetConfirmRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetRequest;
import com.monopolyfun.modules.identity.api.request.RegisterAccountRequest;
import com.monopolyfun.modules.identity.api.response.AdminPasswordResetTokenResponse;
import com.monopolyfun.modules.identity.api.response.AuthSessionResponse;
import com.monopolyfun.modules.identity.api.response.PasswordResetRequestResponse;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.PasswordResetTokenEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.PasswordResetTokenRepository;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.risk.service.RiskCenterService;
import com.monopolyfun.modules.risk.service.RiskDecision;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CurrentAccountAccess currentAccountAccess;
    private final AuditEventRecorder auditEventRecorder;
    private final TraceContextHolder traceContextHolder;
    private final RiskEventService riskEventService;
    private final AccountRiskGuard accountRiskGuard;
    private final RiskCenterService riskCenterService;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final AccountSummaryProjector accountSummaryProjector;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final SecuritySessionService securitySessionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AccountRepository accountRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            CurrentAccountAccess currentAccountAccess,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder,
            RiskEventService riskEventService,
            AccountRiskGuard accountRiskGuard,
            RiskCenterService riskCenterService,
            RateLimitService rateLimitService,
            PasswordEncoder passwordEncoder,
            AccountSummaryProjector accountSummaryProjector,
            OrganizationAuthorityService organizationAuthorityService,
            SecuritySessionService securitySessionService) {
        this.accountRepository = accountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.currentAccountAccess = currentAccountAccess;
        this.auditEventRecorder = auditEventRecorder;
        this.traceContextHolder = traceContextHolder;
        this.riskEventService = riskEventService;
        this.accountRiskGuard = accountRiskGuard;
        this.riskCenterService = riskCenterService;
        this.rateLimitService = rateLimitService;
        this.passwordEncoder = passwordEncoder;
        this.accountSummaryProjector = accountSummaryProjector;
        this.organizationAuthorityService = organizationAuthorityService;
        this.securitySessionService = securitySessionService;
    }

    public AccountEntity register(RegisterAccountRequest request) {
        String normalizedHandle = normalizeHandle(request.handle());
        enforceRateLimit("auth_register", normalizedHandle, 5, Duration.ofMinutes(30), "Too many registration attempts");
        if (accountRepository.findByHandle(normalizedHandle).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Handle already exists");
        }
        Instant now = Instant.now();
        AccountEntity account = new AccountEntity(
                "acct-" + UUID.randomUUID(),
                normalizedHandle,
                resolveDisplayName(normalizedHandle),
                passwordEncoder.encode(request.password()),
                com.monopolyfun.modules.risk.domain.RiskAccountStatus.ACTIVE,
                com.monopolyfun.modules.risk.domain.RiskLevel.NORMAL,
                null,
                null,
                null,
                Map.of(),
                now,
                now);
        accountRepository.save(account);
        recordAudit("auth_register", "account", account.id(), account.id(), "success", Map.of("handle", account.handle()));
        return account;
    }

    public AccountEntity login(LoginRequest request) {
        String normalizedHandle = normalizeHandle(request.handle());
        accountRiskGuard.requireLoginAllowed(normalizedHandle);
        enforceRateLimit("auth_login", normalizedHandle, 8, Duration.ofMinutes(10), "Too many login attempts");
        AccountEntity account = accountRepository.findByHandle(normalizedHandle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid handle or password"));
        if (account.passwordHash() == null || account.passwordHash().isBlank()) {
            riskEventService.record("auth_login_unavailable", "account", account.id(), normalizedHandle, "medium", "Password login unavailable", Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password login unavailable for this account");
        }
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            if (riskCenterService.recordLoginFailure(account, normalizedHandle) == RiskDecision.FREEZE_ACCOUNT) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account frozen by risk control");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid handle or password");
        }
        recordAudit("auth_login", "account", account.id(), account.id(), "success", Map.of("handle", account.handle()));
        return account;
    }

    public AuthSessionResponse me() {
        AccountEntity account = accountRepository.findById(currentAccountAccess.requireAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return publicSession(account, null);
    }

    public void recordLogout() {
        recordAudit(
                "auth_logout",
                "auth_session",
                null,
                currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse("anonymous"),
                "success",
                Map.of("sessionStore", "spring_session_jdbc"));
    }

    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request) {
        String normalizedHandle = normalizeHandle(request.handle());
        enforceRateLimit("auth_password_reset", normalizedHandle, 5, Duration.ofMinutes(30), "Too many password reset requests");
        accountRepository.findByHandle(normalizedHandle)
                .ifPresent(account -> recordAudit("auth_password_reset_requested", "account", account.id(), account.id(), "success", Map.of("handle", normalizedHandle)));
        // 中文注释：公开请求始终返回 accepted 语义，避免通过响应差异枚举账号。
        return new PasswordResetRequestResponse(normalizedHandle, Instant.now());
    }

    public AdminPasswordResetTokenResponse issuePasswordResetTokenForBackoffice(String handle) {
        String issuerAccountId = currentAccountAccess.requireAccountId();
        // 中文注释：后台发重置令牌是安全能力，统一绑定 Root Project 职位能力。
        organizationAuthorityService.requireSystemCapability(issuerAccountId, ProjectCapability.SECURITY_PASSWORD_RESET_ISSUE);
        String normalizedHandle = normalizeHandle(handle);
        AccountEntity account = accountRepository.findByHandle(normalizedHandle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        String rawResetToken = generateToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(PASSWORD_RESET_TTL);
        passwordResetTokenRepository.save(new PasswordResetTokenEntity(
                "prt-" + UUID.randomUUID(),
                account.id(),
                TokenHasher.sha256(rawResetToken),
                expiresAt,
                null,
                now));
        recordAudit("auth_password_reset_token_issued", "account", account.id(), issuerAccountId, "success", Map.of("handle", normalizedHandle));
        return new AdminPasswordResetTokenResponse(account.handle(), rawResetToken, expiresAt);
    }

    public AccountEntity confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }
        enforceRateLimit("auth_password_reset_confirm", TokenHasher.sha256(request.resetToken()), 5, Duration.ofMinutes(30), "Too many password reset confirm attempts");
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256(request.resetToken()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
        if (!resetToken.isUsableAt(Instant.now())) {
            riskEventService.record("auth_password_reset_invalid", "account", resetToken.accountId(), "reset-token", "high", "Reset token expired or already used", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token expired or already used");
        }
        AccountEntity account = accountRepository.findById(resetToken.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        AccountEntity updatedAccount = account.withPasswordHash(passwordEncoder.encode(request.newPassword()), Instant.now());
        accountRepository.save(updatedAccount);
        passwordResetTokenRepository.save(resetToken.markUsed(Instant.now()));
        securitySessionService.expireAccountSessions(account.id());
        recordAudit("auth_password_reset_confirmed", "account", account.id(), account.id(), "success", Map.of("handle", account.handle()));
        return updatedAccount;
    }

    public AccountEntity completeOAuthLogin(AccountEntity account, String auditType) {
        recordAudit(auditType, "account", account.id(), account.id(), "success", Map.of("handle", account.handle()));
        return account;
    }

    private AuthSessionResponse publicSession(AccountEntity account, Instant expiresAt) {
        return new AuthSessionResponse("Cookie", expiresAt, accountSummaryProjector.project(account));
    }

    private String normalizeHandle(String handle) {
        String normalized = handle.trim().replaceFirst("^@+", "").toLowerCase();
        if (!normalized.matches("^[a-z0-9_-]{3,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handle must be 3-20 chars of letters, digits, _ or -");
        }
        return normalized;
    }

    private String resolveDisplayName(String handle) {
        return handle;
    }

    private String generateToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void recordAudit(
            String type,
            String subjectType,
            String subjectId,
            String actorAccountId,
            String outcome,
            Map<String, Object> payload) {
        auditEventRecorder.record(new AuditEvent(
                "audit-" + UUID.randomUUID(),
                type,
                subjectType,
                subjectId,
                actorAccountId,
                traceContextHolder.currentTraceId().orElse("trace-auth"),
                outcome,
                payload,
                Instant.now()));
    }

    private void enforceRateLimit(String scope, String key, int limit, Duration window, String message) {
        if (rateLimitService.isAllowed(scope, key, limit, window)) {
            return;
        }
        riskCenterService.recordRateLimitExceeded(scope, key, message, limit, window);
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
