package com.monopolyfun.modules.risk.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.identity.service.security.SecuritySessionService;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.modules.risk.service.view.RiskAccountView;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class RiskCenterService {
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOGIN_RATE_LIMIT_ESCALATION_WINDOW = Duration.ofMinutes(30);
    private static final Duration DEFAULT_FREEZE_DURATION = Duration.ofHours(24);

    private final AccountRepository accountRepository;
    private final RiskEventRepository riskEventRepository;
    private final RiskEventService riskEventService;
    private final RateLimitService rateLimitService;
    private final RiskRuleCatalog riskRuleCatalog;
    private final SecuritySessionService securitySessionService;
    private final AuditEventRecorder auditEventRecorder;
    private final TraceContextHolder traceContextHolder;

    public RiskCenterService(
            AccountRepository accountRepository,
            RiskEventRepository riskEventRepository,
            RiskEventService riskEventService,
            RateLimitService rateLimitService,
            RiskRuleCatalog riskRuleCatalog,
            SecuritySessionService securitySessionService,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder) {
        this.accountRepository = accountRepository;
        this.riskEventRepository = riskEventRepository;
        this.riskEventService = riskEventService;
        this.rateLimitService = rateLimitService;
        this.riskRuleCatalog = riskRuleCatalog;
        this.securitySessionService = securitySessionService;
        this.auditEventRecorder = auditEventRecorder;
        this.traceContextHolder = traceContextHolder;
    }

    public RiskDecision evaluateAction(AccountEntity account, RiskAction action) {
        RiskRule rule = riskRuleCatalog.ruleFor(action).orElse(null);
        if (rule == null || rateLimitService.isAllowed("risk_action_" + rule.code(), account.id(), rule.limit(), rule.window())) {
            return RiskDecision.ALLOW;
        }
        return applyDecision(account, rule.decision(), rule.reason(), rule.severity(), rule.code(), rule.freezeDuration(), Map.of(
                "action", action.name(),
                "limit", rule.limit(),
                "windowSeconds", rule.window().toSeconds()));
    }

    public RiskDecision recordLoginFailure(AccountEntity account, String actorRef) {
        boolean allowed = rateLimitService.isAllowed("risk_login_failed_password", account.id(), 7, LOGIN_FAILURE_WINDOW);
        riskEventService.record("auth_login_failed", "account", account.id(), actorRef, "medium", "Invalid password", Map.of(
                "action", RiskAction.AUTH_LOGIN.name(),
                "limit", 8,
                "windowSeconds", LOGIN_FAILURE_WINDOW.toSeconds()));
        if (allowed) {
            return RiskDecision.ALLOW;
        }
        // 中文注释：密码失败达到阈值后直接冻结账号，并清理旧 session，阻断撞库继续复用已登录态。
        return applyDecision(
                account,
                RiskDecision.FREEZE_ACCOUNT,
                "Too many failed login attempts",
                "high",
                "auth_login_failed_threshold",
                DEFAULT_FREEZE_DURATION,
                Map.of("action", RiskAction.AUTH_LOGIN.name()));
    }

    public RiskDecision recordRateLimitExceeded(String scope, String key, String reason, int limit, Duration window) {
        AccountEntity account = accountRepository.findByHandle(normalizeKey(key)).orElse(null);
        Map<String, Object> payload = Map.of("scope", scope, "limit", limit, "windowSeconds", window.toSeconds());
        riskEventService.record(scope + "_rate_limited", "auth", key, key, "high", reason, payload);
        if (account == null) {
            return RiskDecision.ALLOW;
        }
        boolean allowed = rateLimitService.isAllowed("risk_rate_limited_" + scope, account.id(), 2, LOGIN_RATE_LIMIT_ESCALATION_WINDOW);
        if (allowed) {
            return RiskDecision.ALLOW;
        }
        return applyDecision(
                account,
                RiskDecision.FREEZE_ACCOUNT,
                reason,
                "high",
                scope + "_rate_limited_escalation",
                DEFAULT_FREEZE_DURATION,
                payload);
    }

    public AccountEntity refreshFrozenState(AccountEntity account) {
        if (account.status() != RiskAccountStatus.FROZEN || account.frozenUntil() == null || account.frozenUntil().isAfter(Instant.now())) {
            return account;
        }
        AccountEntity refreshed = account.withRiskState(RiskAccountStatus.ACTIVE, RiskLevel.WATCH, null, "Risk freeze expired", Instant.now());
        accountRepository.save(refreshed);
        riskEventService.record("account_auto_unfrozen", "account", refreshed.id(), refreshed.id(), "medium", "Risk freeze expired", Map.of());
        return refreshed;
    }

    public RiskAccountView freezeAccount(String accountId, String actorAccountId, String reason, Duration duration) {
        AccountEntity account = requireAccount(accountId);
        AccountEntity updated = freeze(account, reason, duration, "manual_freeze", Map.of("manual", true));
        recordManualAudit("risk_account_frozen", updated.id(), actorAccountId, reason, Map.of("freezeSeconds", duration.toSeconds()));
        return view(updated);
    }

    public RiskAccountView unfreezeAccount(String accountId, String actorAccountId, String reason) {
        AccountEntity account = requireAccount(accountId);
        AccountEntity updated = account.withRiskState(RiskAccountStatus.ACTIVE, RiskLevel.WATCH, null, normalizeReason(reason), Instant.now());
        accountRepository.save(updated);
        riskEventService.record("manual_account_unfrozen", "account", updated.id(), actorAccountId, "medium", normalizeReason(reason), Map.of());
        recordManualAudit("risk_account_unfrozen", updated.id(), actorAccountId, reason, Map.of());
        return view(updated);
    }

    public RiskAccountView banAccount(String accountId, String actorAccountId, String reason) {
        AccountEntity account = requireAccount(accountId);
        AccountEntity updated = account.withRiskState(RiskAccountStatus.BANNED, RiskLevel.HIGH, null, normalizeReason(reason), Instant.now());
        accountRepository.save(updated);
        securitySessionService.expireAccountSessions(updated.id());
        riskEventService.record("manual_account_banned", "account", updated.id(), actorAccountId, "critical", normalizeReason(reason), Map.of());
        recordManualAudit("risk_account_banned", updated.id(), actorAccountId, reason, Map.of());
        return view(updated);
    }

    public RiskAccountView watchAccount(String accountId, String actorAccountId, String reason) {
        AccountEntity account = requireAccount(accountId);
        AccountEntity updated = account.withRiskState(RiskAccountStatus.ACTIVE, RiskLevel.WATCH, null, normalizeReason(reason), Instant.now());
        accountRepository.save(updated);
        riskEventService.record("manual_account_watch", "account", updated.id(), actorAccountId, "medium", normalizeReason(reason), Map.of());
        recordManualAudit("risk_account_watch", updated.id(), actorAccountId, reason, Map.of());
        return view(updated);
    }

    public PageResult<RiskAccountView> listAccounts(String status, String riskLevel, String q, PageQuery pageQuery) {
        var page = accountRepository.findRiskAccounts(status, riskLevel, q, pageQuery);
        Map<String, List<RiskEventEntity>> recentEventsByAccount = riskEventRepository.findRecentByAccounts(
                page.items().stream().map(AccountEntity::id).toList(),
                5);
        // 中文注释：列表页 recentEvents 批量绑定账号，保持 UI 卡片完整同时避免每个账号单独查事件。
        return new PageResult<>(
                page.items().stream().map(account -> view(account, recentEventsByAccount.getOrDefault(account.id(), List.of()))).toList(),
                page.pageInfo());
    }

    public RiskAccountView getAccount(String accountId) {
        return view(requireAccount(accountId));
    }

    private RiskDecision applyDecision(
            AccountEntity account,
            RiskDecision decision,
            String reason,
            String severity,
            String ruleCode,
            Duration freezeDuration,
            Map<String, Object> payload) {
        Map<String, Object> eventPayload = eventPayload(decision, ruleCode, freezeDuration, payload);
        if (decision == RiskDecision.WATCH) {
            AccountEntity watched = account.withRiskState(RiskAccountStatus.ACTIVE, RiskLevel.WATCH, null, normalizeReason(reason), Instant.now());
            accountRepository.save(watched);
            riskEventService.record(ruleCode, "account", watched.id(), watched.id(), severity, normalizeReason(reason), eventPayload);
            return decision;
        }
        if (decision == RiskDecision.FREEZE_ACCOUNT) {
            freeze(account, reason, freezeDuration, ruleCode, eventPayload);
            return decision;
        }
        if (decision == RiskDecision.BAN_ACCOUNT) {
            AccountEntity banned = account.withRiskState(RiskAccountStatus.BANNED, RiskLevel.HIGH, null, normalizeReason(reason), Instant.now());
            accountRepository.save(banned);
            securitySessionService.expireAccountSessions(banned.id());
            riskEventService.record(ruleCode, "account", banned.id(), banned.id(), severity, normalizeReason(reason), eventPayload);
            return decision;
        }
        return decision;
    }

    private AccountEntity freeze(AccountEntity account, String reason, Duration freezeDuration, String ruleCode, Map<String, Object> payload) {
        Duration duration = freezeDuration == null || freezeDuration.isZero() ? DEFAULT_FREEZE_DURATION : freezeDuration;
        AccountEntity updated = account.withRiskState(
                RiskAccountStatus.FROZEN,
                RiskLevel.HIGH,
                Instant.now().plus(duration),
                normalizeReason(reason),
                Instant.now());
        accountRepository.save(updated);
        securitySessionService.expireAccountSessions(updated.id());
        riskEventService.record(ruleCode, "account", updated.id(), updated.id(), "high", normalizeReason(reason), eventPayload(RiskDecision.FREEZE_ACCOUNT, ruleCode, duration, payload));
        return updated;
    }

    private RiskAccountView view(AccountEntity account) {
        return view(account, recentEvents(account.id()));
    }

    private RiskAccountView view(AccountEntity account, List<RiskEventEntity> recentEvents) {
        return new RiskAccountView(
                account.id(),
                account.handle(),
                account.displayName(),
                account.status().code(),
                account.riskLevel().code(),
                account.frozenUntil(),
                account.riskReason(),
                account.riskUpdatedAt(),
                recentEvents.stream().map(com.monopolyfun.modules.risk.service.mapper.RiskViewMapper::risk).toList());
    }

    private List<RiskEventEntity> recentEvents(String accountId) {
        return riskEventRepository.findRecentByAccount(accountId, 5);
    }

    private AccountEntity requireAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Map<String, Object> eventPayload(RiskDecision decision, String ruleCode, Duration freezeDuration, Map<String, Object> payload) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (payload != null) {
            merged.putAll(payload);
        }
        merged.put("decision", decision.name());
        merged.put("ruleCode", ruleCode);
        if (freezeDuration != null && !freezeDuration.isZero()) {
            merged.put("freezeSeconds", freezeDuration.toSeconds());
        }
        return Map.copyOf(merged);
    }

    private void recordManualAudit(String type, String subjectId, String actorAccountId, String reason, Map<String, Object> payload) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(payload);
        merged.put("reason", normalizeReason(reason));
        auditEventRecorder.record(new AuditEvent(
                "audit-" + UUID.randomUUID(),
                type,
                "account",
                subjectId,
                actorAccountId,
                traceContextHolder.currentTraceId().orElse("trace-risk"),
                "success",
                merged,
                Instant.now()));
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        return normalized.isBlank() ? "Risk control action" : normalized;
    }

    private String normalizeFilter(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
    }
}
