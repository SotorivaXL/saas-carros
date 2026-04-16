package com.io.appioweb.adapters.integrations.webmotors.rest;

import com.io.appioweb.adapters.integrations.webmotors.WebmotorsPayloadSanitizer;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsLeadPayload;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class WebmotorsLeadsApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final HttpRequestExecutor httpRequestExecutor;

    @Autowired
    public WebmotorsLeadsApiClient() {
        this(request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    WebmotorsLeadsApiClient(HttpRequestExecutor httpRequestExecutor) {
        this.httpRequestExecutor = httpRequestExecutor;
    }

    public WebmotorsTransportResult<List<WebmotorsLeadPayload>> fetchLeads(
            WebmotorsCredentialSnapshot credentials,
            WebmotorsRestAccessToken accessToken,
            String since
    ) {
        try {
            String url = safe(credentials.restApiBaseUrl());
            if (url.isBlank()) {
                throw new BusinessException("WEBMOTORS_REST_API_BASE_MISSING", "Configure a URL base REST de leads da Webmotors.");
            }
            String finalUrl = url + (url.contains("?") ? "&" : "?") + "since=" + URLEncoder.encode(safe(since), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(finalUrl))
                    .header("Client-Id", safe(credentials.restClientId()))
                    .header("Authorization", "Bearer " + accessToken.accessToken())
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = httpRequestExecutor.send(request);
            if (response.statusCode() == 401) {
                throw new BusinessException("WEBMOTORS_REST_UNAUTHORIZED", "O access token REST da Webmotors expirou ou foi rejeitado.");
            }
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new BusinessException("WEBMOTORS_REST_TEMPORARILY_UNAVAILABLE", "A API REST de leads da Webmotors está indisponível no momento.");
            }
            if (response.statusCode() >= 400) {
                throw new BusinessException("WEBMOTORS_REST_FETCH_LEADS_FAILED", "Não foi possível consultar os leads da Webmotors.");
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            List<WebmotorsLeadPayload> leads = new ArrayList<>();
            JsonNode items = root.isArray() ? root : root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    leads.add(new WebmotorsLeadPayload(
                            firstNonBlank(item, "externalLeadId", "leadId", "id"),
                            firstNonBlank(item, "remoteAdCode", "codigoAnuncio", "listingId"),
                            firstNonBlank(item, "customerName", "nome", "name"),
                            firstNonBlank(item, "customerEmail", "email"),
                            firstNonBlank(item, "customerPhone", "telefone", "phone"),
                            firstNonBlank(item, "message", "mensagem", "observacao"),
                            OBJECT_MAPPER.writeValueAsString(item)
                    ));
                }
            }
            return new WebmotorsTransportResult<>(
                    leads,
                    response.statusCode(),
                    "",
                    WebmotorsPayloadSanitizer.sanitize(response.body())
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_REST_FETCH_LEADS_FAILED", "Não foi possível consultar os leads da Webmotors.");
        }
    }

    private String firstNonBlank(JsonNode node, String... names) {
        for (String name : names) {
            String value = safe(node.path(name).asText(""));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface HttpRequestExecutor {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }
}
