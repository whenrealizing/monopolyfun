package com.monopolyfun.modules.identity.api;

import com.monopolyfun.modules.identity.api.request.LoginRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetConfirmRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetRequest;
import com.monopolyfun.modules.identity.api.request.RegisterAccountRequest;
import com.monopolyfun.modules.identity.api.response.AuthSessionResponse;
import com.monopolyfun.modules.identity.api.response.OAuthAuthorizeResponse;
import com.monopolyfun.modules.identity.api.response.PasswordResetRequestResponse;
import com.monopolyfun.modules.identity.service.security.AuthService;
import com.monopolyfun.modules.identity.service.security.OAuthService;
import com.monopolyfun.modules.identity.service.security.OAuthService.OAuthLoginResult;
import com.monopolyfun.modules.identity.service.security.SecuritySessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final OAuthService oAuthService;
    private final SecuritySessionService securitySessionService;

    public AuthController(AuthService authService, OAuthService oAuthService, SecuritySessionService securitySessionService) {
        this.authService = authService;
        this.oAuthService = oAuthService;
        this.securitySessionService = securitySessionService;
    }

    @PostMapping("/register")
    public AuthSessionResponse register(
            @Valid @RequestBody RegisterAccountRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return securitySessionService.signIn(authService.register(request), servletRequest, servletResponse);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return securitySessionService.signIn(authService.login(request), servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public AuthSessionResponse me() {
        return authService.me();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.recordLogout();
        securitySessionService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public PasswordResetRequestResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    public AuthSessionResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return securitySessionService.signIn(authService.confirmPasswordReset(request), servletRequest, servletResponse);
    }

    @GetMapping("/oauth/github/authorize")
    public OAuthAuthorizeResponse githubAuthorize(@RequestParam(defaultValue = "/market") String returnTo) {
        return oAuthService.githubAuthorize(returnTo);
    }

    @GetMapping("/oauth/github/redirect")
    public ResponseEntity<Void> githubAuthorizeRedirect(
            @RequestParam(defaultValue = "/market") String returnTo,
            HttpServletRequest servletRequest) {
        String normalizedReturnTo = oAuthService.normalizeReturnTo(returnTo);
        // 中文注释：Spring OAuth2 Login 自己管理 state，业务 returnTo 放入同一个浏览器会话，回调成功后再取出。
        servletRequest.getSession(true).setAttribute(OAuthService.GITHUB_OAUTH_RETURN_TO_SESSION_ATTRIBUTE, normalizedReturnTo);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, oAuthService.githubOAuth2AuthorizeUrl(normalizedReturnTo)).build();
    }

    @GetMapping("/oauth/github/callback")
    public ResponseEntity<Void> githubCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        OAuthLoginResult result = oAuthService.githubCallback(code, state);
        securitySessionService.signIn(result.account(), servletRequest, servletResponse);
        return ResponseEntity.status(302)
                .location(URI.create(result.returnTo()))
                .build();
    }
}
