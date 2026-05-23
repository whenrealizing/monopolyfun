package com.monopolyfun.modules.identity.service.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class OAuth2GitHubUserProfileMapper {
    public GitHubOAuthClient.GitHubUserProfile map(OAuth2User user) {
        return map(user == null ? Map.of() : user.getAttributes());
    }

    public GitHubOAuthClient.GitHubUserProfile map(Map<String, Object> attributes) {
        String login = text(attributes.get("login"));
        String id = text(attributes.get("id"));
        if (login == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user login missing");
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user id missing");
        }
        String displayName = text(attributes.get("name"));
        // 中文注释：Spring OAuth2 返回的 attributes 与手写 GitHub client 统一成同一个 profile，账号绑定只保留一条路径。
        return new GitHubOAuthClient.GitHubUserProfile(
                id,
                login,
                displayName == null ? login : displayName,
                text(attributes.get("avatar_url")),
                text(attributes.get("html_url")));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
