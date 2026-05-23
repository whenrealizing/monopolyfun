package com.monopolyfun;

import com.monopolyfun.modules.identity.api.request.PasswordResetConfirmRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetRequest;
import com.monopolyfun.modules.identity.api.response.AdminPasswordResetTokenResponse;
import com.monopolyfun.modules.identity.api.response.PasswordResetRequestResponse;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.PasswordResetTokenEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.infra.PasswordResetTokenRepository;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinProjector;
import com.monopolyfun.modules.identity.service.security.AuthService;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.identity.service.security.SecuritySessionService;
import com.monopolyfun.modules.identity.service.security.TokenHasher;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.modules.risk.service.AccountRiskGuard;
import com.monopolyfun.modules.risk.service.RiskCenterService;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    @Test
    void passwordResetRequestAndConfirmRotatePasswordAndCreateSession() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        InMemoryPasswordResetTokenRepository resetTokenRepository = new InMemoryPasswordResetTokenRepository();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AccountEntity account = new AccountEntity(
                "acct-1",
                "founder",
                "Founder",
                encoder.encode("old-password"),
                RiskAccountStatus.ACTIVE,
                RiskLevel.NORMAL,
                null,
                null,
                null,
                Map.of(),
                Instant.now(),
                Instant.now());
        accountRepository.save(account);

        SecuritySessionService securitySessionService = Mockito.mock(SecuritySessionService.class);
        AuthService authService = new AuthService(
                accountRepository,
                resetTokenRepository,
                new CurrentAccountAccess(),
                new CollectingAuditEventRecorder(),
                new TraceContextHolder(),
                new RiskEventService(new NoopRiskEventRepository(), new TraceContextHolder()),
                Mockito.mock(AccountRiskGuard.class),
                Mockito.mock(RiskCenterService.class),
                new RateLimitService(),
                encoder,
                new AccountSummaryProjector(new InMemoryIdentityFactRepository(), new IdentityDisplaySkinProjector()),
                Mockito.mock(OrganizationAuthorityService.class),
                securitySessionService);

        PasswordResetRequestResponse request = authService.requestPasswordReset(new PasswordResetRequest("founder"));
        assertEquals("founder", request.handle());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-1", "founder", "Founder"),
                null,
                List.of()));
        AdminPasswordResetTokenResponse reset = authService.issuePasswordResetTokenForBackoffice("founder");
        assertNotNull(reset.resetToken());
        assertTrue(resetTokenRepository.findByTokenHash(TokenHasher.sha256(reset.resetToken())).isPresent());

        AccountEntity updatedAccount = authService.confirmPasswordReset(new PasswordResetConfirmRequest(reset.resetToken(), "new-password-123"));
        assertNotNull(updatedAccount);
        assertTrue(encoder.matches("new-password-123", accountRepository.findById("acct-1").orElseThrow().passwordHash()));
        assertTrue(resetTokenRepository.findByTokenHash(TokenHasher.sha256(reset.resetToken())).orElseThrow().usedAt() != null);
        Mockito.verify(securitySessionService).expireAccountSessions("acct-1");
        SecurityContextHolder.clearContext();
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        private final Map<String, AccountEntity> byId = new HashMap<>();

        @Override
        public List<AccountEntity> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<AccountEntity> findPublic(com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return new com.monopolyfun.shared.pagination.PageResult<>(
                    findAll().stream().limit(pageQuery.limit()).toList(),
                    new com.monopolyfun.shared.pagination.PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<AccountEntity> findRiskAccounts(
                String status,
                String riskLevel,
                String q,
                com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return findPublic(pageQuery);
        }

        @Override
        public List<AccountEntity> findByIds(java.util.Collection<String> ids) {
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public Optional<AccountEntity> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<AccountEntity> findByHandle(String handle) {
            return byId.values().stream().filter(account -> account.handle().equals(handle)).findFirst();
        }

        @Override
        public AccountEntity save(AccountEntity account) {
            byId.put(account.id(), account);
            return account;
        }
    }

    private static final class InMemoryIdentityFactRepository implements IdentityFactRepository {
        @Override
        public IdentityFactEntity save(IdentityFactEntity fact) {
            return fact;
        }

        @Override
        public List<IdentityFactEntity> findByAccountId(String accountId) {
            return List.of();
        }

        @Override
        public Optional<IdentityFactEntity> findVerifiedByCertifierAndPlatformUserId(String certifierId, String platformUserId) {
            return Optional.empty();
        }

        @Override
        public void revokeVerifiedByAccountIdAndCertifierId(String accountId, String certifierId, Instant revokedAt) {
        }
    }

    private static final class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {
        private final Map<String, PasswordResetTokenEntity> byHash = new HashMap<>();

        @Override
        public Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public PasswordResetTokenEntity save(PasswordResetTokenEntity token) {
            byHash.put(token.tokenHash(), token);
            return token;
        }
    }

    private static final class CollectingAuditEventRecorder implements AuditEventRecorder {
        @Override
        public void record(AuditEvent event) {
        }
    }

    private static final class NoopRiskEventRepository implements RiskEventRepository {
        @Override
        public RiskEventEntity save(RiskEventEntity event) {
            return event;
        }

        @Override
        public List<RiskEventEntity> findRecent(int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findRecentByAccount(String accountId, int limit) {
            return List.of();
        }

        @Override
        public List<RiskEventEntity> findAll() {
            return List.of();
        }

        @Override
        public Map<String, Long> countBySeverity() {
            return Map.of();
        }
    }
}
