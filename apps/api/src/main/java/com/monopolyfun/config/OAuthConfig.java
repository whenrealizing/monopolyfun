package com.monopolyfun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "monopolyfun.oauth.github")
public class OAuthConfig {
    private String clientId = "";
    private String clientSecret = "";
    private String authorizeUrl = "https://github.com/login/oauth/authorize";
    private String tokenUrl = "https://github.com/login/oauth/access_token";
    private String userUrl = "https://api.github.com/user";
    private String redirectUri = "http://localhost:8080/api/v1/auth/oauth/github/callback";
    private String verificationRedirectUri = "http://localhost:8080/api/v1/identity/oauth/github/callback";
    private String webCallbackUrl = "http://localhost:3000/oauth/callback";

    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getUserUrl() {
        return userUrl;
    }

    public void setUserUrl(String userUrl) {
        this.userUrl = userUrl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getWebCallbackUrl() {
        return webCallbackUrl;
    }

    public void setWebCallbackUrl(String webCallbackUrl) {
        this.webCallbackUrl = webCallbackUrl;
    }

    public String getVerificationRedirectUri() {
        return verificationRedirectUri;
    }

    public void setVerificationRedirectUri(String verificationRedirectUri) {
        this.verificationRedirectUri = verificationRedirectUri;
    }
}
