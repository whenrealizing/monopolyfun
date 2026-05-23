package com.monopolyfun.modules.identity.service.security;

import com.monopolyfun.modules.identity.service.security.OAuthService.OAuthLoginResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GitHubOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private final OAuth2GitHubUserProfileMapper profileMapper;
    private final OAuthService oAuthService;
    private final SecuritySessionService securitySessionService;

    public GitHubOAuth2SuccessHandler(
            OAuth2GitHubUserProfileMapper profileMapper,
            OAuthService oAuthService,
            SecuritySessionService securitySessionService) {
        this.profileMapper = profileMapper;
        this.oAuthService = oAuthService;
        this.securitySessionService = securitySessionService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof OAuth2User user)) {
            throw new ServletException("GitHub OAuth2 principal is unavailable");
        }
        // 中文注释：Spring OAuth2 Login 成功后复用内部 OAuth 绑定，避免 Spring principal 和业务账号形成两套会话。
        OAuthLoginResult result = oAuthService.githubProfileCallback(profileMapper.map(user), consumeReturnTo(request));
        securitySessionService.signIn(result.account(), request, response);
        response.sendRedirect(result.returnTo());
    }

    private String consumeReturnTo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "/market";
        }
        Object value = session.getAttribute(OAuthService.GITHUB_OAUTH_RETURN_TO_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthService.GITHUB_OAUTH_RETURN_TO_SESSION_ATTRIBUTE);
        return value instanceof String returnTo ? returnTo : "/market";
    }
}
