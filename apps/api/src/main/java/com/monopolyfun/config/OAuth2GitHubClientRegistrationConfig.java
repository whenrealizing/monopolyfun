package com.monopolyfun.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
public class OAuth2GitHubClientRegistrationConfig {
    @Bean
    @ConditionalOnExpression("'${monopolyfun.oauth.github.client-id:}' != '' && '${monopolyfun.oauth.github.client-secret:}' != ''")
    ClientRegistrationRepository githubClientRegistrationRepository(OAuthConfig config) {
        // 中文注释：Spring OAuth2 Login 使用独立 callback 路径，旧手写 callback 保留给身份认证器迁移期和已有 GitHub App 配置。
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/api/v1/auth/oauth2/callback/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri(config.getAuthorizeUrl())
                .tokenUri(config.getTokenUrl())
                .userInfoUri(config.getUserUrl())
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
