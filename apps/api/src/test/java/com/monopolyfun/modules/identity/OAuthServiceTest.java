package com.monopolyfun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.OAuthConfig;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.OAuthIdentityEntity;
import com.monopolyfun.modules.identity.domain.OAuthStateEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.OAuthIdentityRepository;
import com.monopolyfun.modules.identity.infra.OAuthStateRepository;
import com.monopolyfun.modules.identity.service.security.AuthService;
import com.monopolyfun.modules.identity.service.security.GitHubOAuthClient;
import com.monopolyfun.modules.identity.service.security.OAuth2GitHubUserProfileMapper;
import com.monopolyfun.modules.identity.service.security.OAuthService;
import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.shared.pagination.PageInfo;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OAuthServiceTest {
    @Test
    void githubAuthorizeReturnsReturnToAwareSpringRedirectEntry() {
        OAuthService service = new OAuthService(
                new OAuthConfig(),
                new InMemoryOAuthStateRepository(),
                new InMemoryOAuthIdentityRepository(),
                new InMemoryAccountRepository(),
                Mockito.mock(AuthService.class),
                new StubGitHubOAuthClient(),
                new RateLimitService());

        assertEquals("/api/v1/auth/oauth/github/redirect?returnTo=%2Forders%2FMF1", service.githubAuthorize("/orders/MF1").authorizeUrl());
        assertEquals("/oauth2/authorization/github", service.githubOAuth2AuthorizeUrl("/orders/MF1"));
    }

    @Test
    void githubCallbackBindsByExternalUserIdWhenLoginMatchesExistingHandle() {
        Instant now = Instant.now();
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        accountRepository.save(new AccountEntity(
                "acct-local",
                "octocat",
                "Local Octocat",
                "hash",
                RiskAccountStatus.ACTIVE,
                RiskLevel.NORMAL,
                null,
                null,
                null,
                Map.of(),
                now,
                now));
        InMemoryOAuthStateRepository stateRepository = new InMemoryOAuthStateRepository();
        stateRepository.save(new OAuthStateEntity("state-1", "github", "state-ok", "/market", now.plusSeconds(600), null, now));
        InMemoryOAuthIdentityRepository identityRepository = new InMemoryOAuthIdentityRepository();
        AuthService authService = Mockito.mock(AuthService.class);
        when(authService.completeOAuthLogin(any(AccountEntity.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthService service = new OAuthService(
                new OAuthConfig(),
                stateRepository,
                identityRepository,
                accountRepository,
                authService,
                new StubGitHubOAuthClient(),
                new RateLimitService());

        OAuthService.OAuthLoginResult result = service.githubCallback("oauth-code", "state-ok");

        assertNotEquals("acct-local", result.account().id());
        assertEquals("octocat-123456", result.account().handle());
        OAuthIdentityEntity identity = identityRepository.findByProviderAndExternalUserId("github", "123456").orElseThrow();
        assertEquals(result.account().id(), identity.accountId());
        assertNotNull(stateRepository.findByStateToken("state-ok").orElseThrow().usedAt());
    }

    @Test
    void oauth2ProfileCallbackUsesSameGithubAccountBinding() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        InMemoryOAuthIdentityRepository identityRepository = new InMemoryOAuthIdentityRepository();
        AuthService authService = Mockito.mock(AuthService.class);
        when(authService.completeOAuthLogin(any(AccountEntity.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        OAuthService service = new OAuthService(
                new OAuthConfig(),
                new InMemoryOAuthStateRepository(),
                identityRepository,
                accountRepository,
                authService,
                new StubGitHubOAuthClient(),
                new RateLimitService());

        GitHubOAuthClient.GitHubUserProfile profile = new OAuth2GitHubUserProfileMapper().map(Map.of(
                "id", 987654,
                "login", "spring-octocat",
                "name", "Spring Octocat",
                "avatar_url", "https://avatars.example/spring.png",
                "html_url", "https://github.com/spring-octocat"));
        OAuthService.OAuthLoginResult result = service.githubProfileCallback(profile, "/orders");

        assertEquals("/orders", result.returnTo());
        assertEquals("spring-octocat", result.account().handle());
        assertEquals(result.account().id(), identityRepository.findByProviderAndExternalUserId("github", "987654").orElseThrow().accountId());
    }

    private static final class StubGitHubOAuthClient extends GitHubOAuthClient {
        private StubGitHubOAuthClient() {
            super(new OAuthConfig(), new ObjectMapper());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public GitHubUserProfile fetchUserProfile(String code, String state, String redirectUri) {
            return new GitHubUserProfile("123456", "octocat", "GitHub Octocat", "https://avatars.example/octocat.png", "https://github.com/octocat");
        }
    }

    private static final class InMemoryOAuthStateRepository implements OAuthStateRepository {
        private final Map<String, OAuthStateEntity> byState = new HashMap<>();

        @Override
        public Optional<OAuthStateEntity> findByStateToken(String stateToken) {
            return Optional.ofNullable(byState.get(stateToken));
        }

        @Override
        public OAuthStateEntity save(OAuthStateEntity state) {
            byState.put(state.stateToken(), state);
            return state;
        }
    }

    private static final class InMemoryOAuthIdentityRepository implements OAuthIdentityRepository {
        private final Map<String, OAuthIdentityEntity> byProviderAndExternalId = new HashMap<>();

        @Override
        public Optional<OAuthIdentityEntity> findByProviderAndExternalUserId(String provider, String externalUserId) {
            return Optional.ofNullable(byProviderAndExternalId.get(key(provider, externalUserId)));
        }

        @Override
        public OAuthIdentityEntity save(OAuthIdentityEntity identity) {
            byProviderAndExternalId.put(key(identity.provider(), identity.externalUserId()), identity);
            return identity;
        }

        private String key(String provider, String externalUserId) {
            return provider + ":" + externalUserId;
        }
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        private final Map<String, AccountEntity> byId = new HashMap<>();
        private final Map<String, AccountEntity> byHandle = new HashMap<>();

        @Override
        public List<AccountEntity> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public PageResult<AccountEntity> findPublic(PageQuery pageQuery) {
            return new PageResult<>(findAll(), new PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public PageResult<AccountEntity> findRiskAccounts(String status, String riskLevel, String q, PageQuery pageQuery) {
            return findPublic(pageQuery);
        }

        @Override
        public List<AccountEntity> findByIds(Collection<String> ids) {
            return ids.stream().map(byId::get).filter(account -> account != null).toList();
        }

        @Override
        public Optional<AccountEntity> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<AccountEntity> findByHandle(String handle) {
            return Optional.ofNullable(byHandle.get(handle));
        }

        @Override
        public AccountEntity save(AccountEntity account) {
            byId.put(account.id(), account);
            byHandle.put(account.handle(), account);
            return account;
        }
    }
}
