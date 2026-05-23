package com.monopolyfun.modules.identity.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.OAuthConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class GitHubOAuthClient {
    private final OAuthConfig oAuthConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GitHubOAuthClient(OAuthConfig oAuthConfig, ObjectMapper objectMapper) {
        this.oAuthConfig = oAuthConfig;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return oAuthConfig.isEnabled();
    }

    public String buildAuthorizeUrl(String redirectUri, String stateToken) {
        return oAuthConfig.getAuthorizeUrl()
                + "?client_id=" + encode(oAuthConfig.getClientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("read:user user:email")
                + "&state=" + encode(stateToken);
    }

    public GitHubUserProfile fetchUserProfile(String code, String state, String redirectUri) {
        String accessToken = exchangeToken(code, state, redirectUri);
        JsonNode user = fetchUser(accessToken);
        String login = readText(user, "login");
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user login missing");
        }
        String id = readText(user, "id");
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user id missing");
        }
        String displayName = readText(user, "name");
        String avatarUrl = readText(user, "avatar_url");
        String profileUrl = readText(user, "html_url");
        return new GitHubUserProfile(
                id,
                login,
                displayName == null || displayName.isBlank() ? login : displayName,
                avatarUrl,
                profileUrl);
    }

    private String exchangeToken(String code, String state, String redirectUri) {
        try {
            String body = "client_id=" + encode(oAuthConfig.getClientId())
                    + "&client_secret=" + encode(oAuthConfig.getClientSecret())
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&state=" + encode(state);
            HttpRequest request = HttpRequest.newBuilder(URI.create(oAuthConfig.getTokenUrl()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = readText(json, "access_token");
            if (response.statusCode() >= 400 || accessToken == null || accessToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub token exchange failed");
            }
            return accessToken;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub token exchange failed");
        }
    }

    private JsonNode fetchUser(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(oAuthConfig.getUserUrl()))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user fetch failed");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub user fetch failed");
        }
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GitHubUserProfile(
            String id,
            String login,
            String displayName,
            String avatarUrl,
            String profileUrl
    ) {
    }
}
