package com.monopolyfun.modules.identity.api;

import com.monopolyfun.modules.identity.api.request.LoginRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetConfirmRequest;
import com.monopolyfun.modules.identity.api.request.PasswordResetRequest;
import com.monopolyfun.modules.identity.api.request.RegisterAccountRequest;
import com.monopolyfun.modules.identity.api.response.AuthSessionResponse;
import com.monopolyfun.modules.identity.api.response.PasswordResetRequestResponse;
import com.monopolyfun.modules.identity.service.security.AuthService;
import com.monopolyfun.modules.identity.service.security.SecuritySessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SecuritySessionService securitySessionService;

    public AuthController(AuthService authService, SecuritySessionService securitySessionService) {
        this.authService = authService;
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
}
