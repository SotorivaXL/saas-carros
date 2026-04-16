package com.io.appioweb.adapters.integrations.webmotors.rest;

import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsFeatureFlags;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsRestAccessToken;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebmotorsLeadsApiClientTest {

    @Test
    void fetchLeadsMapsItemsArrayAndPropagatesSinceParameter() {
        List<HttpRequest> requests = new ArrayList<>();
        WebmotorsLeadsApiClient client = new WebmotorsLeadsApiClient(request -> {
            requests.add(request);
            return new FakeHttpResponse(200, """
                    {
                      "items": [
                        {
                          "leadId": "lead-1",
                          "codigoAnuncio": "wm-100",
                          "nome": "Maria",
                          "email": "maria@example.com",
                          "telefone": "11999999999",
                          "mensagem": "Tenho interesse"
                        }
                      ]
                    }
                    """);
        });

        var transport = client.fetchLeads(
                credentials(),
                new WebmotorsRestAccessToken("token-123", 3600),
                "2026-04-15T10:00:00Z"
        );

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().uri().toString()).contains("since=2026-04-15T10%3A00%3A00Z");
        assertThat(transport.payload()).hasSize(1);
        assertThat(transport.payload().getFirst().externalLeadId()).isEqualTo("lead-1");
        assertThat(transport.payload().getFirst().remoteAdCode()).isEqualTo("wm-100");
        assertThat(transport.payload().getFirst().customerName()).isEqualTo("Maria");
    }

    private static WebmotorsCredentialSnapshot credentials() {
        return new WebmotorsCredentialSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "default",
                "Loja Teste",
                new WebmotorsFeatureFlags(true, true, true, true, true),
                "https://soap.example.test",
                "/auth",
                "/inventory",
                "/catalog",
                "12345678000190",
                "integracao@example.com",
                "senha-super-secreta",
                "https://rest.example.test/token",
                "https://rest.example.test/leads",
                "usuario-rest",
                "senha-rest",
                "client-id",
                "client-secret",
                "callback-secret"
        );
    }

    private record FakeHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://example.test"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
