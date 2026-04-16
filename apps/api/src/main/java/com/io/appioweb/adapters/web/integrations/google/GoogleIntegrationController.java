package com.io.appioweb.adapters.web.integrations.google;

import com.io.appioweb.adapters.integrations.google.GoogleCalendarClient;
import com.io.appioweb.adapters.integrations.google.GoogleOAuthConnectionResult;
import com.io.appioweb.adapters.integrations.google.GoogleOAuthProperties;
import com.io.appioweb.adapters.integrations.google.GoogleOAuthStateCodec;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class GoogleIntegrationController {

    private static final String GOOGLE_AUTH_BASE = "https://accounts.google.com/o/oauth2/v2/auth";

    private final CurrentUserPort currentUser;
    private final GoogleOAuthProperties properties;
    private final GoogleOAuthStateCodec stateCodec;
    private final GoogleCalendarClient calendarClient;

    public GoogleIntegrationController(
            CurrentUserPort currentUser,
            GoogleOAuthProperties properties,
            GoogleOAuthStateCodec stateCodec,
            GoogleCalendarClient calendarClient
    ) {
        this.currentUser = currentUser;
        this.properties = properties;
        this.stateCodec = stateCodec;
        this.calendarClient = calendarClient;
    }

    @GetMapping("/api/integrations/google/oauth/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<GoogleOAuthConnectionHttpResponse> status() {
        GoogleOAuthConnectionResult result = calendarClient.getConnectionStatus(currentUser.companyId());
        return ResponseEntity.ok(new GoogleOAuthConnectionHttpResponse(
                result.companyId(),
                result.googleUserEmail(),
                result.scopes(),
                result.status(),
                result.updatedAt()
        ));
    }

    @GetMapping("/api/integrations/google/oauth/start")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<Void> start() {
        properties.validateConfigured();
        UUID companyId = currentUser.companyId();
        String state = stateCodec.encode(companyId);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", properties.clientId());
        query.put("redirect_uri", properties.redirectUri());
        query.put("response_type", "code");
        query.put("scope", properties.scopes());
        query.put("access_type", "offline");
        query.put("prompt", "consent");
        query.put("state", state);
        URI location = URI.create(GOOGLE_AUTH_BASE + "?" + buildQuery(query));
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    @GetMapping("/api/integrations/google/oauth/callback")
    public ResponseEntity<GoogleOAuthConnectionHttpResponse> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error
    ) {
        if (error != null && !error.isBlank()) {
            throw new BusinessException("GOOGLE_OAUTH_DENIED", "A conexao com o Google foi cancelada: " + error.trim());
        }
        GoogleOAuthStateCodec.StatePayload payload = stateCodec.decode(state);
        GoogleOAuthConnectionResult result = calendarClient.exchangeAuthorizationCode(payload.companyId(), code);
        return ResponseEntity.ok(new GoogleOAuthConnectionHttpResponse(
                result.companyId(),
                result.googleUserEmail(),
                result.scopes(),
                result.status(),
                result.updatedAt()
        ));
    }

    @PostMapping("/api/integrations/google/oauth/disconnect")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<Void> disconnect() {
        calendarClient.disconnect(currentUser.companyId());
        return ResponseEntity.noContent().build();
    }

    private String buildQuery(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) sb.append("&");
            first = false;
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
