package com.italo.ai.finance;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.finance.oauth")
record FinanceOAuthSettings(String issuer, String clientId, String clientSecret,
                             URI redirectUri, String encryptionKey) {
    static final String CALLBACK = "/login/oauth2/code/finance";
    static final String START = "/finance/oauth/start";

    FinanceOAuthSettings {
        URI issuerUri = URI.create(issuer);
        if (!"https".equals(issuerUri.getScheme()) || issuerUri.getHost() == null
                || issuerUri.getRawUserInfo() != null || issuerUri.getRawQuery() != null
                || issuerUri.getRawFragment() != null || issuer.endsWith("/")) {
            throw new IllegalArgumentException("Configure o issuer HTTPS do realm do Finance, sem barra final.");
        }
        if (redirectUri == null || !CALLBACK.equals(redirectUri.getPath()) || redirectUri.getRawQuery() != null
                || redirectUri.getRawFragment() != null || redirectUri.getRawUserInfo() != null
                || redirectUri.getHost() == null || !("https".equals(redirectUri.getScheme())
                || "http".equals(redirectUri.getScheme()) && "localhost".equals(redirectUri.getHost()))) {
            throw new IllegalArgumentException("Configure o callback HTTPS do Finance ou use HTTP em localhost.");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Configure o client ID e o secret OAuth do Finance.");
        }
    }

    boolean secureCookie() {
        return "https".equals(redirectUri.getScheme());
    }
}
