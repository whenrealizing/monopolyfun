package com.monopolyfun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String SESSION_COOKIE_SCHEME = "sessionCookie";
    private static final String CSRF_HEADER_SCHEME = "csrfHeader";

    @Bean
    OpenAPI monopolyfunOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MonopolyFun Order-First API")
                        .version("0.1.0")
                        .description("Market -> Listing -> Order -> Proof -> Dispute -> Settlement API."))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .description("Spring Session JDBC HttpOnly cookie issued by /api/v1/auth/login or OAuth callback.")
                                        .name("MONOPOLYFUN_SESSION"))
                        .addSecuritySchemes(CSRF_HEADER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("CSRF token copied from MONOPOLYFUN_CSRF for protected write requests.")
                                        .name("X-CSRF-Token")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(SESSION_COOKIE_SCHEME)
                        .addList(CSRF_HEADER_SCHEME));
    }
}
