package com.monopolyfun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class CorsConfig {
    private static final String[] DEV_WEB_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.20.*:*",
            "http://172.21.*:*",
            "http://172.22.*:*",
            "http://172.23.*:*",
            "http://172.24.*:*",
            "http://172.25.*:*",
            "http://172.26.*:*",
            "http://172.27.*:*",
            "http://172.28.*:*",
            "http://172.29.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*"
    };

    private static String[] buildAllowedOriginPatterns(String configuredPatterns, boolean production) {
        String[] configured = splitCsv(configuredPatterns);
        if (production) {
            return configured;
        }

        // 中文注释：开发环境支持手机和局域网设备访问本机 Next，生产环境仍由显式配置控制。
        return Stream.concat(Arrays.stream(DEV_WEB_ORIGIN_PATTERNS), Arrays.stream(configured))
                .distinct()
                .toArray(String[]::new);
    }

    private static String[] splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    @Bean
    WebMvcConfigurer corsConfigurer(
            @Value("${monopolyfun.security.web-origin:http://localhost:3000}") String webOrigin,
            @Value("${monopolyfun.security.web-origin-patterns:}") String webOriginPatterns,
            @Value("${monopolyfun.security.production:false}") boolean production) {
        String[] allowedOrigins = splitCsv(webOrigin);
        String[] allowedOriginPatterns = buildAllowedOriginPatterns(webOriginPatterns, production);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedOriginPatterns(allowedOriginPatterns)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
