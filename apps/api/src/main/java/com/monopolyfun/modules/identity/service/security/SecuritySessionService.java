package com.monopolyfun.modules.identity.service.security;

import com.monopolyfun.modules.identity.api.response.AuthSessionResponse;
import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.shared.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SecuritySessionService {
    private final AccountSummaryProjector accountSummaryProjector;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final Duration sessionTimeout;

    public SecuritySessionService(
            AccountSummaryProjector accountSummaryProjector,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            CsrfTokenRepository csrfTokenRepository,
            @Value("${spring.session.timeout:30d}") Duration sessionTimeout) {
        this.accountSummaryProjector = accountSummaryProjector;
        this.sessionRepository = sessionRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessionTimeout = sessionTimeout;
    }

    public AuthSessionResponse signIn(AccountEntity account, HttpServletRequest request, HttpServletResponse response) {
        // 中文注释：登录成功后重新生成 session id，覆盖登录前匿名 session 和旧 cookie，降低 session fixation 风险。
        HttpSession session = request.getSession(true);
        request.changeSessionId();

        CurrentAccount principal = new CurrentAccount(account.id(), account.handle(), account.displayName());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        session.setMaxInactiveInterval(Math.toIntExact(Math.min(sessionTimeout.toSeconds(), Integer.MAX_VALUE)));
        securityContextRepository.saveContext(context, request, response);
        issueCsrfToken(request, response);
        // 中文注释：响应只暴露会话过期时间和账号摘要，真实 session id 始终由 HttpOnly Cookie 承载。
        return new AuthSessionResponse("Cookie", Instant.now().plus(sessionTimeout), accountSummaryProjector.project(account));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        csrfTokenRepository.saveToken(null, request, response);
    }

    public void expireAccountSessions(String accountId) {
        // 中文注释：Spring Session 的 principal 索引用账号 id，密码重置后可一次性删除该账号全部活跃 session。
        sessionRepository.findByPrincipalName(accountId)
                .keySet()
                .forEach(sessionRepository::deleteById);
    }

    private void issueCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }
}
