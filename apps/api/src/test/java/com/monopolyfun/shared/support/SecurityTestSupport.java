package com.monopolyfun;

import com.monopolyfun.shared.security.CurrentAccount;
import jakarta.servlet.http.Cookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

final class SecurityTestSupport {
    private static final String CSRF_COOKIE = "MONOPOLYFUN_CSRF";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_TOKEN = "test-csrf-token";

    private SecurityTestSupport() {
    }

    static RequestPostProcessor session(JdbcTemplate jdbcTemplate, String accountId) {
        return request -> {
            Map<String, Object> account = jdbcTemplate.queryForMap(
                    "select handle, display_name from accounts where id = ?",
                    accountId);
            CurrentAccount principal = new CurrentAccount(
                    accountId,
                    String.valueOf(account.get("handle")),
                    String.valueOf(account.get("display_name")));
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    java.util.List.of());
            // 中文注释：业务测试直接注入 Spring Security principal，CSRF 仍按真实 Cookie/header 入口校验。
            authentication(token).postProcessRequest(request);
            request.setCookies(new Cookie(CSRF_COOKIE, CSRF_TOKEN));
            request.addHeader(CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
