package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthProperties {

    private static final String DEFAULT_SCOPES = "openid email https://www.googleapis.com/auth/calendar";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String scopes;

    public GoogleOAuthProperties(
            @Value("${GOOGLE_CLIENT_ID:${GOOGLE_OAUTH_CLIENT_ID:}}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:${GOOGLE_OAUTH_CLIENT_SECRET:}}") String clientSecret,
            @Value("${GOOGLE_OAUTH_REDIRECT_URI:${GOOGLE_CALENDAR_OAUTH_CALLBACK_URL:}}") String redirectUri,
            @Value("${GOOGLE_OAUTH_SCOPES:}") String scopes
    ) {
        this.clientId = safeTrim(clientId);
        this.clientSecret = safeTrim(clientSecret);
        this.redirectUri = safeTrim(redirectUri);
        this.scopes = normalizeScopes(scopes);
    }

    public String clientId() { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String redirectUri() { return redirectUri; }
    public String scopes() { return scopes; }

    public void validateConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new BusinessException("GOOGLE_OAUTH_NOT_CONFIGURED", "Configure GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET e GOOGLE_OAUTH_REDIRECT_URI para usar a integracao Google");
        }
    }

    private String normalizeScopes(String raw) {
        String normalized = safeTrim(raw).replaceAll("\\s+", " ");
        return normalized.isBlank() ? DEFAULT_SCOPES : normalized;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
