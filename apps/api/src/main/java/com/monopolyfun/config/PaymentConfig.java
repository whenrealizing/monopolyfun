package com.monopolyfun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "monopolyfun.payments")
public class PaymentConfig {
    private String provider = "fake";
    private String callbackSecret = "";
    private String publicBaseUrl = "http://localhost:8080";
    private String currency = "USD";
    private boolean fakeCallbackEnabled = false;
    private Okx okx = new Okx();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public void setCallbackSecret(String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isFakeCallbackEnabled() {
        return fakeCallbackEnabled;
    }

    public void setFakeCallbackEnabled(boolean fakeCallbackEnabled) {
        this.fakeCallbackEnabled = fakeCallbackEnabled;
    }

    public Okx getOkx() {
        return okx;
    }

    public void setOkx(Okx okx) {
        this.okx = okx == null ? new Okx() : okx;
    }

    public static class Okx {
        private String apiBaseUrl = "https://web3.okx.com";
        private String apiKey = "";
        private String apiSecret = "";
        private String apiPassphrase = "";
        private String projectId = "";
        private String defaultRecipient = "";
        private String defaultAsset = "USDC";
        private String defaultNetwork = "xlayer";
        private int maxTimeoutSeconds = 300;

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getApiPassphrase() {
            return apiPassphrase;
        }

        public void setApiPassphrase(String apiPassphrase) {
            this.apiPassphrase = apiPassphrase;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getDefaultRecipient() {
            return defaultRecipient;
        }

        public void setDefaultRecipient(String defaultRecipient) {
            this.defaultRecipient = defaultRecipient;
        }

        public String getDefaultAsset() {
            return defaultAsset;
        }

        public void setDefaultAsset(String defaultAsset) {
            this.defaultAsset = defaultAsset;
        }

        public String getDefaultNetwork() {
            return defaultNetwork;
        }

        public void setDefaultNetwork(String defaultNetwork) {
            this.defaultNetwork = defaultNetwork;
        }

        public int getMaxTimeoutSeconds() {
            return maxTimeoutSeconds;
        }

        public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
            this.maxTimeoutSeconds = maxTimeoutSeconds;
        }
    }
}
