package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.persistence.googlecalendar.CompanyGoogleOAuthRepositoryJpa;
import com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus;
import com.io.appioweb.adapters.persistence.googlecalendar.JpaCompanyGoogleOAuthEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.shared.errors.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GoogleCalendarClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarClient.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI REVOKE_URI = URI.create("https://oauth2.googleapis.com/revoke");
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final List<Long> DEFAULT_BACKOFF_MS = List.of(300L, 700L, 1200L);

    private final CompanyGoogleOAuthRepositoryJpa oauthRepository;
    private final SensitiveDataCrypto crypto;
    private final GoogleOAuthProperties properties;
    private final HttpRequestExecutor httpRequestExecutor;
    private final Sleeper sleeper;
    private final Clock clock;
    private final Map<UUID, CircuitState> circuitStates = new ConcurrentHashMap<>();

    @Autowired
    public GoogleCalendarClient(
            CompanyGoogleOAuthRepositoryJpa oauthRepository,
            SensitiveDataCrypto crypto,
            GoogleOAuthProperties properties
    ) {
        this(
                oauthRepository,
                crypto,
                properties,
                request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()),
                millis -> Thread.sleep(millis),
                Clock.systemUTC()
        );
    }

    public GoogleCalendarClient(
            CompanyGoogleOAuthRepositoryJpa oauthRepository,
            SensitiveDataCrypto crypto,
            GoogleOAuthProperties properties,
            HttpRequestExecutor httpRequestExecutor,
            Sleeper sleeper,
            Clock clock
    ) {
        this.oauthRepository = oauthRepository;
        this.crypto = crypto;
        this.properties = properties;
        this.httpRequestExecutor = httpRequestExecutor;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GoogleOAuthConnectionResult getConnectionStatus(UUID companyId) {
        if (companyId == null) {
            throw new BusinessException("COMPANY_NOT_FOUND", "Empresa nao encontrada");
        }
        JpaCompanyGoogleOAuthEntity entity = oauthRepository.findByCompanyId(companyId).orElse(null);
        Instant updatedAt = entity == null ? null : entity.getUpdatedAt();
        return new GoogleOAuthConnectionResult(
                companyId,
                entity == null ? null : entity.getGoogleUserEmail(),
                entity == null ? properties.scopes() : entity.getScopes(),
                entity == null ? GoogleConnectionStatus.DISCONNECTED : entity.getStatus(),
                updatedAt
        );
    }

    @Transactional
    public GoogleOAuthConnectionResult exchangeAuthorizationCode(UUID companyId, String code) {
        properties.validateConfigured();
        String authCode = safeTrim(code);
        if (companyId == null || authCode.isBlank()) {
            throw new BusinessException("GOOGLE_OAUTH_CODE_REQUIRED", "Codigo do OAuth Google invalido");
        }
        JpaCompanyGoogleOAuthEntity existing = oauthRepository.findByCompanyId(companyId).orElse(null);

        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(buildForm(Map.of(
                        "client_id", properties.clientId(),
                        "client_secret", properties.clientSecret(),
                        "code", authCode,
                        "grant_type", "authorization_code",
                        "redirect_uri", properties.redirectUri()
                ))))
                .build();

        HttpResponse<String> response = sendWithRetry(companyId, request, "oauth-code");
        if (response.statusCode() >= 400) {
            GoogleApiError error = parseGoogleApiError(response.body());
            log.warn(
                    "Google OAuth code exchange failed for company {} with status {} redirectUri={} error={} description={}",
                    companyId,
                    response.statusCode(),
                    properties.redirectUri(),
                    error.code(),
                    error.description()
            );
            throw new BusinessException("GOOGLE_OAUTH_CODE_EXCHANGE_FAILED", describeOAuthCodeExchangeFailure(error));
        }

        JsonNode root = parseJson(response.body(), "GOOGLE_OAUTH_CODE_EXCHANGE_FAILED", "Resposta invalida ao concluir OAuth Google");
        String accessToken = safeTrim(root.path("access_token").asText(""));
        String refreshToken = safeTrim(root.path("refresh_token").asText(""));
        if (refreshToken.isBlank() && existing != null) {
            refreshToken = crypto.decrypt(existing.getRefreshTokenEncrypted());
        }
        if (refreshToken.isBlank()) {
            throw new BusinessException("GOOGLE_REFRESH_TOKEN_MISSING", "O Google nao retornou refresh_token. Refaca a conexao e aceite o consentimento");
        }
        if (accessToken.isBlank()) {
            throw new BusinessException("GOOGLE_ACCESS_TOKEN_MISSING", "O Google nao retornou access_token. Tente novamente");
        }

        Instant now = clock.instant();
        JpaCompanyGoogleOAuthEntity entity = existing == null ? new JpaCompanyGoogleOAuthEntity() : existing;
        if (entity.getId() == null) entity.setId(UUID.randomUUID());
        entity.setCompanyId(companyId);
        entity.setGoogleUserEmail(resolveGoogleEmail(root, existing));
        entity.setRefreshTokenEncrypted(crypto.encrypt(refreshToken));
        entity.setAccessTokenEncrypted(crypto.encrypt(accessToken));
        entity.setAccessTokenExpiresAt(resolveAccessTokenExpiry(root, now));
        entity.setScopes(resolveScopes(root, existing));
        entity.setStatus(GoogleConnectionStatus.CONNECTED);
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        oauthRepository.saveAndFlush(entity);
        return new GoogleOAuthConnectionResult(
                entity.getCompanyId(),
                entity.getGoogleUserEmail(),
                entity.getScopes(),
                entity.getStatus(),
                entity.getUpdatedAt()
        );
    }

    @Transactional
    public void disconnect(UUID companyId) {
        if (companyId == null) return;
        JpaCompanyGoogleOAuthEntity entity = oauthRepository.findByCompanyId(companyId).orElse(null);
        if (entity == null) return;

        String refreshToken = crypto.decrypt(entity.getRefreshTokenEncrypted());
        String accessToken = crypto.decrypt(entity.getAccessTokenEncrypted());
        String tokenToRevoke = !refreshToken.isBlank() ? refreshToken : accessToken;
        if (!tokenToRevoke.isBlank()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(REVOKE_URI)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(buildForm(Map.of("token", tokenToRevoke))))
                        .build();
                HttpResponse<String> response = sendWithRetry(companyId, request, "oauth-revoke");
                if (response.statusCode() >= 400) {
                    log.warn("Google token revoke returned status {} for company {}", response.statusCode(), companyId);
                }
            } catch (BusinessException ex) {
                log.warn("Google token revoke failed for company {}: {}", companyId, ex.getMessage());
            }
        }

        entity.setRefreshTokenEncrypted("");
        entity.setAccessTokenEncrypted("");
        entity.setAccessTokenExpiresAt(null);
        entity.setStatus(GoogleConnectionStatus.DISCONNECTED);
        entity.setUpdatedAt(clock.instant());
        oauthRepository.saveAndFlush(entity);
    }

    @Transactional
    public String refreshAccessToken(UUID companyId) {
        properties.validateConfigured();
        JpaCompanyGoogleOAuthEntity entity = requireConnectedEntity(companyId);
        String refreshToken = crypto.decrypt(entity.getRefreshTokenEncrypted());
        if (refreshToken.isBlank()) {
            markIntegrationError(entity);
            throw new BusinessException("GOOGLE_REFRESH_TOKEN_MISSING", "A conexao com o Google expirou. Reconecte a conta da empresa");
        }

        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(buildForm(Map.of(
                        "client_id", properties.clientId(),
                        "client_secret", properties.clientSecret(),
                        "grant_type", "refresh_token",
                        "refresh_token", refreshToken
                ))))
                .build();

        HttpResponse<String> response = sendWithRetry(companyId, request, "oauth-refresh");
        if (response.statusCode() >= 400) {
            GoogleApiError error = parseGoogleApiError(response.body());
            log.warn(
                    "Google token refresh failed for company {} with status {} error={} description={}",
                    companyId,
                    response.statusCode(),
                    error.code(),
                    error.description()
            );
            markIntegrationError(entity);
            throw new BusinessException("GOOGLE_REFRESH_FAILED", "A conexao com o Google expirou. Reconecte a conta da empresa");
        }

        JsonNode root = parseJson(response.body(), "GOOGLE_REFRESH_FAILED", "Resposta invalida ao renovar token do Google");
        String accessToken = safeTrim(root.path("access_token").asText(""));
        if (accessToken.isBlank()) {
            markIntegrationError(entity);
            throw new BusinessException("GOOGLE_ACCESS_TOKEN_MISSING", "A conexao com o Google expirou. Reconecte a conta da empresa");
        }
        entity.setAccessTokenEncrypted(crypto.encrypt(accessToken));
        entity.setAccessTokenExpiresAt(resolveAccessTokenExpiry(root, clock.instant()));
        String refreshedScopes = safeTrim(root.path("scope").asText(""));
        if (!refreshedScopes.isBlank()) entity.setScopes(refreshedScopes);
        entity.setStatus(GoogleConnectionStatus.CONNECTED);
        entity.setUpdatedAt(clock.instant());
        oauthRepository.saveAndFlush(entity);
        return accessToken;
    }

    public List<GoogleCalendarBusyWindow> freeBusy(UUID companyId, Instant timeMin, Instant timeMax, String calendarId) {
        validateTimeRange(timeMin, timeMax);
        String resolvedCalendarId = safeTrim(calendarId).isBlank() ? "primary" : safeTrim(calendarId);
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("timeMin", timeMin.truncatedTo(ChronoUnit.SECONDS).toString());
        payload.put("timeMax", timeMax.truncatedTo(ChronoUnit.SECONDS).toString());
        payload.put("timeZone", "UTC");
        ArrayNode items = OBJECT_MAPPER.createArrayNode();
        ObjectNode item = OBJECT_MAPPER.createObjectNode();
        item.put("id", resolvedCalendarId);
        items.add(item);
        payload.set("items", items);

        HttpResponse<String> response = executeCalendarRequest(
                companyId,
                token -> HttpRequest.newBuilder(URI.create(CALENDAR_API + "/freeBusy"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                        .build(),
                "freebusy"
        );
        JsonNode root = parseJson(response.body(), "GOOGLE_CALENDAR_FREEBUSY_FAILED", "Resposta invalida ao consultar disponibilidade do Google Calendar");
        JsonNode busy = root.path("calendars").path(resolvedCalendarId).path("busy");
        List<GoogleCalendarBusyWindow> result = new ArrayList<>();
        if (busy.isArray()) {
            for (JsonNode busyNode : busy) {
                Instant start = parseInstant(busyNode.path("start").asText(""));
                Instant end = parseInstant(busyNode.path("end").asText(""));
                if (start != null && end != null && end.isAfter(start)) {
                    result.add(new GoogleCalendarBusyWindow(start, end));
                }
            }
        }
        result.sort((a, b) -> a.start().compareTo(b.start()));
        return result;
    }

    public GoogleCalendarEventResult createEventWithMeet(
            UUID companyId,
            String calendarId,
            String summary,
            String description,
            Instant start,
            Instant end,
            String timeZone
    ) {
        validateTimeRange(start, end);
        String resolvedCalendarId = safeTrim(calendarId).isBlank() ? "primary" : safeTrim(calendarId);
        String resolvedZone = safeTrim(timeZone).isBlank() ? "America/Sao_Paulo" : safeTrim(timeZone);
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String requestId = UUID.randomUUID().toString();

        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("id", eventId);
        body.put("summary", safeTrim(summary).isBlank() ? "Reuniao" : safeTrim(summary));
        body.put("description", safeTrim(description));
        body.set("start", buildDateTimeNode(start, resolvedZone));
        body.set("end", buildDateTimeNode(end, resolvedZone));
        ObjectNode conferenceData = OBJECT_MAPPER.createObjectNode();
        ObjectNode createRequest = OBJECT_MAPPER.createObjectNode();
        createRequest.put("requestId", requestId);
        conferenceData.set("createRequest", createRequest);
        body.set("conferenceData", conferenceData);

        HttpResponse<String> response = executeCalendarRequest(
                companyId,
                token -> HttpRequest.newBuilder(URI.create(
                                CALENDAR_API + "/calendars/" + urlEncode(resolvedCalendarId) + "/events?conferenceDataVersion=1"
                        ))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                        .build(),
                "events-insert",
                true
        );

        if (response.statusCode() == 409) {
            return fetchEventWithMeet(companyId, resolvedCalendarId, eventId);
        }
        JsonNode created = parseJson(response.body(), "GOOGLE_CALENDAR_EVENT_CREATE_FAILED", "Resposta invalida ao criar evento no Google Calendar");
        String htmlLink = safeTrim(created.path("htmlLink").asText(""));
        String meetLink = safeTrim(created.path("hangoutLink").asText(""));
        if (meetLink.isBlank()) {
            GoogleCalendarEventResult fetched = fetchEventWithMeet(companyId, resolvedCalendarId, eventId);
            if (!fetched.meetLink().isBlank()) return fetched;
        }
        return new GoogleCalendarEventResult(eventId, htmlLink, meetLink);
    }

    private GoogleCalendarEventResult fetchEventWithMeet(UUID companyId, String calendarId, String eventId) {
        long[] retryMs = {0L, 300L, 700L, 1200L};
        GoogleCalendarEventResult last = new GoogleCalendarEventResult(eventId, "", "");
        for (long waitMs : retryMs) {
            if (waitMs > 0L) sleep(waitMs);
            HttpResponse<String> response = executeCalendarRequest(
                    companyId,
                    token -> HttpRequest.newBuilder(URI.create(
                                    CALENDAR_API + "/calendars/" + urlEncode(calendarId) + "/events/" + urlEncode(eventId) + "?conferenceDataVersion=1"
                            ))
                            .header("Authorization", "Bearer " + token)
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build(),
                    "events-get"
            );
            JsonNode root = parseJson(response.body(), "GOOGLE_CALENDAR_EVENT_READ_FAILED", "Resposta invalida ao consultar evento do Google Calendar");
            last = new GoogleCalendarEventResult(
                    eventId,
                    safeTrim(root.path("htmlLink").asText("")),
                    safeTrim(root.path("hangoutLink").asText(""))
            );
            if (!last.meetLink().isBlank()) return last;
        }
        return last;
    }

    private HttpResponse<String> executeCalendarRequest(UUID companyId, AuthorizedRequestFactory factory, String operation) {
        return executeCalendarRequest(companyId, factory, operation, false);
    }

    private HttpResponse<String> executeCalendarRequest(UUID companyId, AuthorizedRequestFactory factory, String operation, boolean allowConflict) {
        String token = resolveAccessToken(companyId);
        HttpResponse<String> response = sendWithRetry(companyId, factory.build(token), operation);
        if (response.statusCode() == 401) {
            log.info("Google Calendar returned 401 for company {} during {}; refreshing token once", companyId, operation);
            token = refreshAccessToken(companyId);
            response = sendWithRetry(companyId, factory.build(token), operation + "-after-refresh");
        }
        if (response.statusCode() == 401) {
            JpaCompanyGoogleOAuthEntity entity = requireEntity(companyId);
            markIntegrationError(entity);
            throw new BusinessException("GOOGLE_UNAUTHORIZED", "A conexao com o Google expirou. Reconecte a conta da empresa");
        }
        if (response.statusCode() == 403) {
            JpaCompanyGoogleOAuthEntity entity = requireEntity(companyId);
            markIntegrationError(entity);
            throw new BusinessException("GOOGLE_PERMISSION_DENIED", "A integracao Google nao tem permissao suficiente. Reconecte a conta com os escopos corretos");
        }
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw new BusinessException("GOOGLE_TEMPORARILY_UNAVAILABLE", "Google Calendar indisponivel no momento. Tente novamente em instantes");
        }
        if (allowConflict && response.statusCode() == 409) {
            return response;
        }
        if (response.statusCode() >= 400) {
            log.warn("Google Calendar request failed for company {} during {} with status {}", companyId, operation, response.statusCode());
            throw new BusinessException("GOOGLE_CALENDAR_REQUEST_FAILED", "Nao foi possivel concluir a operacao no Google Calendar");
        }
        return response;
    }

    private String resolveAccessToken(UUID companyId) {
        JpaCompanyGoogleOAuthEntity entity = requireConnectedEntity(companyId);
        String accessToken = crypto.decrypt(entity.getAccessTokenEncrypted());
        Instant expiresAt = entity.getAccessTokenExpiresAt();
        Instant now = clock.instant();
        if (!accessToken.isBlank() && expiresAt != null && expiresAt.isAfter(now.plusSeconds(60))) {
            return accessToken;
        }
        return refreshAccessToken(companyId);
    }

    private JpaCompanyGoogleOAuthEntity requireConnectedEntity(UUID companyId) {
        JpaCompanyGoogleOAuthEntity entity = requireEntity(companyId);
        if (entity.getStatus() != GoogleConnectionStatus.CONNECTED) {
            throw new BusinessException("GOOGLE_NOT_CONNECTED", "A conta Google Calendar desta empresa nao esta conectada");
        }
        return entity;
    }

    private JpaCompanyGoogleOAuthEntity requireEntity(UUID companyId) {
        return oauthRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException("GOOGLE_NOT_CONNECTED", "A conta Google Calendar desta empresa nao esta conectada"));
    }

    private void markIntegrationError(JpaCompanyGoogleOAuthEntity entity) {
        entity.setStatus(GoogleConnectionStatus.ERROR);
        entity.setUpdatedAt(clock.instant());
        oauthRepository.saveAndFlush(entity);
    }

    private Instant resolveAccessTokenExpiry(JsonNode root, Instant now) {
        long expiresInSeconds = Math.max(30L, root.path("expires_in").asLong(3600L));
        return now.plusSeconds(expiresInSeconds);
    }

    private String resolveScopes(JsonNode root, JpaCompanyGoogleOAuthEntity existing) {
        String responseScopes = safeTrim(root.path("scope").asText(""));
        if (!responseScopes.isBlank()) return responseScopes;
        if (existing != null && !safeTrim(existing.getScopes()).isBlank()) return existing.getScopes();
        return properties.scopes();
    }

    private String resolveGoogleEmail(JsonNode root, JpaCompanyGoogleOAuthEntity existing) {
        String fromToken = extractEmailFromIdToken(root.path("id_token").asText(""));
        if (!fromToken.isBlank()) return fromToken;
        return existing == null ? null : existing.getGoogleUserEmail();
    }

    private String extractEmailFromIdToken(String idToken) {
        String value = safeTrim(idToken);
        if (value.isBlank()) return "";
        String[] parts = value.split("\\.");
        if (parts.length < 2) return "";
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            return safeTrim(root.path("email").asText(""));
        } catch (Exception ignored) {
            return "";
        }
    }

    private HttpResponse<String> sendWithRetry(UUID companyId, HttpRequest request, String operation) {
        CircuitState circuit = circuitStates.computeIfAbsent(companyId, ignored -> new CircuitState());
        if (circuit.isOpen(clock.instant())) {
            throw new BusinessException("GOOGLE_CIRCUIT_OPEN", "Google Calendar indisponivel no momento. Tente novamente em instantes");
        }

        HttpResponse<String> lastResponse = null;
        for (int attempt = 0; attempt < DEFAULT_BACKOFF_MS.size() + 1; attempt++) {
            if (attempt > 0) sleep(DEFAULT_BACKOFF_MS.get(attempt - 1));
            try {
                HttpResponse<String> response = httpRequestExecutor.send(request);
                lastResponse = response;
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("Google request {} for company {} returned status {} (attempt {}/{})", operation, companyId, response.statusCode(), attempt + 1, DEFAULT_BACKOFF_MS.size() + 1);
                    circuit.recordFailure(clock.instant());
                    continue;
                }
                circuit.recordSuccess();
                return response;
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Google request {} for company {} failed on attempt {}/{}: {}", operation, companyId, attempt + 1, DEFAULT_BACKOFF_MS.size() + 1, ex.getClass().getSimpleName());
                circuit.recordFailure(clock.instant());
            }
        }
        if (lastResponse != null) return lastResponse;
        throw new BusinessException("GOOGLE_REQUEST_FAILED", "Google Calendar indisponivel no momento. Tente novamente em instantes");
    }

    private void validateTimeRange(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException("GOOGLE_CALENDAR_INVALID_RANGE", "Intervalo de tempo invalido para Google Calendar");
        }
    }

    private ObjectNode buildDateTimeNode(Instant instant, String timeZone) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("dateTime", instant.truncatedTo(ChronoUnit.SECONDS).toString());
        node.put("timeZone", timeZone);
        return node;
    }

    private String toJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BusinessException("GOOGLE_JSON_ERROR", "Nao foi possivel montar a requisicao do Google Calendar");
        }
    }

    private JsonNode parseJson(String raw, String code, String message) {
        try {
            return OBJECT_MAPPER.readTree(raw == null ? "{}" : raw);
        } catch (Exception ex) {
            throw new BusinessException(code, message);
        }
    }

    private GoogleApiError parseGoogleApiError(String raw) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw == null ? "{}" : raw);
            String code = safeTrim(root.path("error").asText(""));
            String description = safeTrim(root.path("error_description").asText(""));
            if (!code.isBlank() || !description.isBlank()) {
                return new GoogleApiError(code, description);
            }
        } catch (Exception ignored) {
            // fallback below
        }
        String fallback = safeTrim(raw);
        if (fallback.length() > 240) {
            fallback = fallback.substring(0, 240);
        }
        return new GoogleApiError("", fallback);
    }

    private String describeOAuthCodeExchangeFailure(GoogleApiError error) {
        String code = error.code().toLowerCase();
        String description = error.description().toLowerCase();
        if ("invalid_client".equals(code)) {
            return "Credenciais OAuth do Google invalidas. Revise o client id e o client secret configurados no .env";
        }
        if ("unauthorized_client".equals(code)) {
            return "Esse cliente OAuth do Google nao esta habilitado para este fluxo. Use um OAuth Client do tipo Web Application";
        }
        if ("invalid_grant".equals(code)) {
            return "O Google rejeitou o codigo OAuth. Verifique se a URL de callback no Google Cloud Console e no .env sao identicas e tente conectar novamente";
        }
        if (description.contains("redirect_uri")) {
            return "A URL de callback do Google esta divergente. Garanta que Google Cloud Console e .env usem exatamente a mesma callback";
        }
        return "Nao foi possivel concluir a conexao com o Google. Revise as credenciais OAuth e a URL de callback configuradas";
    }

    private Instant parseInstant(String raw) {
        String value = safeTrim(raw);
        if (value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildForm(Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            parts.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return String.join("&", parts);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    public interface HttpRequestExecutor {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws Exception;
    }

    @FunctionalInterface
    private interface AuthorizedRequestFactory {
        HttpRequest build(String accessToken);
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("GOOGLE_SLEEP_INTERRUPTED", "Google Calendar indisponivel no momento. Tente novamente em instantes");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("GOOGLE_SLEEP_INTERRUPTED", "Google Calendar indisponivel no momento. Tente novamente em instantes");
        }
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private Instant openUntil = Instant.EPOCH;

        synchronized boolean isOpen(Instant now) {
            return openUntil != null && openUntil.isAfter(now);
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
            openUntil = Instant.EPOCH;
        }

        synchronized void recordFailure(Instant now) {
            consecutiveFailures++;
            if (consecutiveFailures >= 3) {
                openUntil = now.plusSeconds(30);
            }
        }
    }

    private record GoogleApiError(String code, String description) { }
}
