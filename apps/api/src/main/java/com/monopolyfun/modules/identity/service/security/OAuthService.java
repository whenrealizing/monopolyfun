package com.monopolyfun.modules.identity.service.security;

import com.monopolyfun.modules.identity.api.response.OAuthAuthorizeResponse;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.OAuthIdentityEntity;
import com.monopolyfun.modules.identity.domain.OAuthStateEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.OAuthIdentityRepository;
import com.monopolyfun.modules.identity.infra.OAuthStateRepository;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OAuthService {
    public static final String GITHUB_OAUTH_RETURN_TO_SESSION_ATTRIBUTE = "monopolyfun.githubOAuth.returnTo";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String SPRING_GITHUB_AUTHORIZE_URL = "/oauth2/authorization/github";

    private final OAuthStateRepository oAuthStateRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final GitHubOAuthClient gitHubOAuthClient;
    private final com.monopolyfun.config.OAuthConfig oAuthConfig;
    private final RateLimitService rateLimitService;

    public OAuthService(
            com.monopolyfun.config.OAuthConfig oAuthConfig,
            OAuthStateRepository oAuthStateRepository,
            OAuthIdentityRepository oAuthIdentityRepository,
            AccountRepository accountRepository,
            AuthService authService,
            GitHubOAuthClient gitHubOAuthClient,
            RateLimitService rateLimitService) {
        this.oAuthConfig = oAuthConfig;
        this.oAuthStateRepository = oAuthStateRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.gitHubOAuthClient = gitHubOAuthClient;
        this.rateLimitService = rateLimitService;
    }

    public OAuthAuthorizeResponse githubAuthorize(String returnTo) {
        ensureEnabled();
        String normalizedReturnTo = normalizeReturnTo(returnTo);
        return new OAuthAuthorizeResponse("/api/v1/auth/oauth/github/redirect?returnTo=" + URLEncoder.encode(normalizedReturnTo, StandardCharsets.UTF_8));
    }

    public String githubOAuth2AuthorizeUrl(String returnTo) {
        ensureEnabled();
        String normalizedReturnTo = normalizeReturnTo(returnTo);
        enforceRateLimit("auth_oauth_github_authorize", normalizedReturnTo, 20, Duration.ofMinutes(10), "Too many OAuth authorize requests");
        return SPRING_GITHUB_AUTHORIZE_URL;
    }

    public OAuthLoginResult githubCallback(String code, String state) {
        ensureEnabled();
        enforceRateLimit("auth_oauth_github_callback", state, 10, Duration.ofMinutes(10), "Too many OAuth callback requests");
        OAuthStateEntity oauthState = oAuthStateRepository.findByStateToken(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OAuth state"));
        if (!oauthState.isUsableAt(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state expired or already used");
        }

        GitHubOAuthClient.GitHubUserProfile profile = gitHubOAuthClient.fetchUserProfile(code, state, oAuthConfig.getRedirectUri());
        OAuthLoginResult result = completeGithubLogin(profile, oauthState.returnTo());
        oAuthStateRepository.save(oauthState.markUsed(Instant.now()));
        return result;
    }

    public OAuthLoginResult githubProfileCallback(GitHubOAuthClient.GitHubUserProfile profile, String returnTo) {
        ensureEnabled();
        return completeGithubLogin(profile, normalizeReturnTo(returnTo));
    }

    private OAuthLoginResult completeGithubLogin(GitHubOAuthClient.GitHubUserProfile profile, String returnTo) {
        Instant now = Instant.now();
        OAuthIdentityEntity identity = oAuthIdentityRepository.findByProviderAndExternalUserId("github", profile.id())
                .orElse(null);
        AccountEntity account = identity == null ? createGithubAccount(profile, now) : requireBoundAccount(identity);
        if (identity == null) {
            // 中文注释：OAuth 登录只绑定 provider+externalUserId，GitHub handle 仅作为新账号展示名候选。
            oAuthIdentityRepository.save(new OAuthIdentityEntity(
                    "oauth-identity-" + UUID.randomUUID(),
                    "github",
                    profile.id(),
                    account.id(),
                    profile.login(),
                    Map.of("login", profile.login(), "profileUrl", nullToEmpty(profile.profileUrl()), "avatarUrl", nullToEmpty(profile.avatarUrl())),
                    now,
                    now));
        }

        AccountEntity authenticatedAccount = authService.completeOAuthLogin(account, "auth_oauth_github_login");
        return new OAuthLoginResult(returnTo, authenticatedAccount);
    }

    private void enforceRateLimit(String scope, String key, int limit, Duration window, String message) {
        if (rateLimitService.isAllowed(scope, key, limit, window)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private void ensureEnabled() {
        if (!gitHubOAuthClient.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "GitHub OAuth is not configured");
        }
    }

    public String normalizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            return "/market";
        }
        return returnTo;
    }

    private AccountEntity createGithubAccount(GitHubOAuthClient.GitHubUserProfile profile, Instant now) {
        String handle = uniqueOAuthHandle(profile.login(), profile.id());
        return accountRepository.save(new AccountEntity(
                "acct-" + UUID.randomUUID(),
                handle,
                profile.displayName(),
                null,
                RiskAccountStatus.ACTIVE,
                RiskLevel.NORMAL,
                null,
                null,
                null,
                Map.of("oauthProvider", "github", "oauthLogin", profile.login(), "oauthExternalUserId", profile.id()),
                now,
                now));
    }

    private AccountEntity requireBoundAccount(OAuthIdentityEntity identity) {
        return accountRepository.findById(identity.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth account binding is invalid"));
    }

    private String uniqueOAuthHandle(String login, String externalUserId) {
        String base = normalizeHandleCandidate(login);
        if (accountRepository.findByHandle(base).isEmpty()) {
            return base;
        }
        String suffix = externalUserId == null || externalUserId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 6)
                : externalUserId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        for (int index = 0; index < 20; index++) {
            String candidateSuffix = index == 0 ? suffix : suffix + index;
            String prefix = base.substring(0, Math.min(base.length(), Math.max(3, 20 - candidateSuffix.length() - 1)));
            String candidate = prefix + "-" + candidateSuffix;
            if (accountRepository.findByHandle(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "github-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalizeHandleCandidate(String login) {
        String normalized = login == null ? "" : login.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (normalized.length() < 3) {
            normalized = "github-" + normalized;
        }
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20);
        }
        return normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record OAuthLoginResult(
            String returnTo,
            AccountEntity account
    ) {
    }
}
