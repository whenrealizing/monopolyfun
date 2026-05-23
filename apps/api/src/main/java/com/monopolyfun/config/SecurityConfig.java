package com.monopolyfun.config;

import com.monopolyfun.modules.identity.service.security.GitHubOAuth2SuccessHandler;
import com.monopolyfun.shared.observability.TraceFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TraceFilter traceFilter,
            CsrfTokenRepository csrfTokenRepository,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
            GitHubOAuth2SuccessHandler githubOAuth2SuccessHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // 公开写入口在业务层做独立校验；已登录写操作统一走 Spring Security CSRF。
                        .ignoringRequestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/github/app/webhook",
                                "/api/v1/payments/callback/fake",
                                "/api/v1/payments/callback/okx/a2a"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 匿名访问统一返回 401，便于前端和测试区分登录缺失与角色不足。
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 中文注释：部署平台会访问 readiness/liveness 子路径，健康探针必须在鉴权前可达。
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/oauth/github/authorize",
                                "/api/v1/auth/oauth/github/redirect",
                                "/api/v1/auth/oauth/github/callback",
                                "/api/v1/auth/oauth2/callback/*",
                                "/oauth2/authorization/*",
                                "/api/v1/identity/oauth/github/callback",
                                "/api/v1/public/**",
                                "/api/v1/accounts",
                                "/api/v1/accounts/lookup",
                                "/api/v1/projects/root",
                                "/api/v1/projects/*",
                                "/api/v1/projects/*/dashboard",
                                "/api/v1/projects/*/timeline",
                                "/api/v1/projects/*/roles",
                                "/api/v1/offers",
                                "/api/v1/offers/**",
                                "/api/v1/requests",
                                "/api/v1/requests/**",
                                "/api/v1/projects",
                                "/api/v1/posts/*/workspace",
                                "/api/v1/posts/*/items",
                                "/api/v1/items/*",
                                "/api/v1/markets/*/shares-ledger",
                                "/api/v1/accounts/*/shares-ledger")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/github/app/webhook",
                                "/api/v1/payments/callback/fake",
                                "/api/v1/payments/callback/okx/a2a")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(traceFilter, UsernamePasswordAuthenticationFilter.class);
        if (clientRegistrationRepository.getIfAvailable() != null) {
            // 中文注释：GitHub OAuth2 Login 成功后进入业务会话签发，Spring principal 只作为外部身份输入。
            http.oauth2Login(oauth -> oauth
                    .redirectionEndpoint(redirection -> redirection.baseUri("/api/v1/auth/oauth2/callback/*"))
                    .successHandler(githubOAuth2SuccessHandler));
        } else {
            http.oauth2Login(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(SecurityCookieProperties security) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(security.getCsrfCookieName());
        repository.setHeaderName(security.getCsrfHeaderName());
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(security.isCookieSecure())
                .sameSite(security.getCookieSameSite()));
        return repository;
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
