package com.io.appioweb.adapters.integrations.webmotors.rest;

import com.io.appioweb.adapters.integrations.webmotors.WebmotorsPayloadSanitizer;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsRestAccessToken;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsTransportResult;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebmotorsRestTokenClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final HttpRequestExecutor httpRequestExecutor;

    @Autowired
    public WebmotorsRestTokenClient() {
        this(request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    WebmotorsRestTokenClient(HttpRequestExecutor httpRequestExecutor) {
        this.httpRequestExecutor = httpRequestExecutor;
    }

    public WebmotorsTransportResult<WebmotorsRestAccessToken> getAccessToken(WebmotorsCredentialSnapshot credentials) {
        String cacheKey = credentials.companyId() + "::" + credentials.storeKey();
        CachedToken cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return new WebmotorsTransportResult<>(
                    new WebmotorsRestAccessToken(cached.accessToken(), cached.expiresAt().getEpochSecond() - Instant.now().getEpochSecond()),
                    200,
                    "",
                    ""
            );
        }

        try {
            String authorization = Base64.getEncoder().encodeToString((safe(credentials.restClientId()) + ":" + safe(credentials.restClientSecret()))
                    .getBytes(StandardCharsets.UTF_8));
            String requestBody = buildForm(Map.of(
                    "grant_type", "password",
                    "username", safe(credentials.restUsername()),
                    "password", safe(credentials.restPassword())
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(require(credentials.restTokenUrl(), "Configure a URL do token REST da Webmotors.")))
                    .header("Authorization", "Basic " + authorization)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpRequestExecutor.send(request);
            if (response.statusCode() >= 400) {
                throw new BusinessException("WEBMOTORS_REST_TOKEN_FAILED", "Não foi possível obter o access token da Webmotors.");
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            String accessToken = safe(root.path("access_token").asText(""));
            long expiresIn = Math.max(60L, root.path("expires_in").asLong(3600L));
            if (accessToken.isBlank()) {
                throw new BusinessException("WEBMOTORS_REST_TOKEN_MISSING", "A Webmotors não retornou um access token válido.");
            }
            cache.put(cacheKey, new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn)));
            return new WebmotorsTransportResult<>(
                    new WebmotorsRestAccessToken(accessToken, expiresIn),
                    response.statusCode(),
                    WebmotorsPayloadSanitizer.sanitize(requestBody),
                    WebmotorsPayloadSanitizer.sanitize(response.body())
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_REST_TOKEN_FAILED", "Não foi possível obter o access token da Webmotors.");
        }
    }

    public void invalidate(java.util.UUID companyId, String storeKey) {
        cache.remove(companyId + "::" + safe(storeKey));
    }

    private String buildForm(Map<String, String> fields) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            parts.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }

    private String require(String value, String message) {
        String normalized = safe(value);
        if (normalized.isBlank()) {
            throw new BusinessException("WEBMOTORS_REST_CONFIG_MISSING", message);
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface HttpRequestExecutor {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
