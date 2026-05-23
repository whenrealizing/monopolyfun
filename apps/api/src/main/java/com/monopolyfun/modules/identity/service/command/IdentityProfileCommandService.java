package com.monopolyfun.modules.identity.service.command;

import com.monopolyfun.modules.identity.api.request.UpdateIdentityProfileRequest;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityProfileCommandService {
    private static final Logger log = LoggerFactory.getLogger(IdentityProfileCommandService.class);
    private static final String AGENT_SUMMARY_KEY = "agentSummary";
    private static final String AVATAR_URL_KEY = "avatarUrl";
    private static final int MAX_AVATAR_URL_LENGTH = 500;

    private final AccountRepository accountRepository;
    private final AuditEventRecorder auditEventRecorder;
    private final TraceContextHolder traceContextHolder;

    public IdentityProfileCommandService(
            AccountRepository accountRepository,
            AuditEventRecorder auditEventRecorder,
            TraceContextHolder traceContextHolder) {
        this.accountRepository = accountRepository;
        this.auditEventRecorder = auditEventRecorder;
        this.traceContextHolder = traceContextHolder;
    }

    public AccountEntity updateProfile(String accountId, UpdateIdentityProfileRequest request) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        String displayName = normalizeDisplayName(request.displayName());
        String agentSummary = normalizeOptionalText(request.agentSummary());
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());
        Map<String, Object> metadata = new LinkedHashMap<>(account.metadata() == null ? Map.of() : account.metadata());

        // 中文注释：handle 是登录和业务引用稳定键，资料编辑只允许改展示字段、简介和默认头像。
        if (agentSummary == null) {
            metadata.remove(AGENT_SUMMARY_KEY);
        } else {
            metadata.put(AGENT_SUMMARY_KEY, agentSummary);
        }
        if (avatarUrl == null) {
            metadata.remove(AVATAR_URL_KEY);
        } else {
            metadata.put(AVATAR_URL_KEY, avatarUrl);
        }

        AccountEntity updated = new AccountEntity(
                account.id(),
                account.handle(),
                displayName,
                account.passwordHash(),
                account.status(),
                account.riskLevel(),
                account.frozenUntil(),
                account.riskReason(),
                account.riskUpdatedAt(),
                metadata,
                account.createdAt(),
                Instant.now());
        accountRepository.save(updated);
        log.info("identity_profile_update accountId={} handle={} hasAvatar={}", updated.id(), updated.handle(), avatarUrl != null);
        recordAudit(updated, displayName, agentSummary, avatarUrl);
        return updated;
    }

    private String normalizeDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        if (normalized.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is too long");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 240) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent summary is too long");
        }
        return normalized;
    }

    private String normalizeAvatarUrl(String value) {
        String normalized = normalizeOptionalText(value, MAX_AVATAR_URL_LENGTH, "Avatar URL is too long");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar URL must be http or https");
            }
            return uri.toString();
        } catch (URISyntaxException caught) {
            // 中文注释：头像只接受可公开加载的 URL，避免把无效字符串写入账号展示读模型。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar URL must be http or https", caught);
        }
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongReason) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, tooLongReason);
        }
        return normalized;
    }

    private void recordAudit(AccountEntity account, String displayName, String agentSummary, String avatarUrl) {
        auditEventRecorder.record(new AuditEvent(
                "audit-" + UUID.randomUUID(),
                "identity_profile_update",
                "account",
                account.id(),
                account.id(),
                traceContextHolder.currentTraceId().orElse(null),
                "success",
                Map.of(
                        "handle", account.handle(),
                        "displayName", displayName,
                        "hasAgentSummary", agentSummary != null,
                        "hasAvatar", avatarUrl != null),
                Instant.now()));
    }
}
