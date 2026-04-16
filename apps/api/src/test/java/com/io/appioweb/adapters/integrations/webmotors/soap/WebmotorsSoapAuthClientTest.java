package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsFeatureFlags;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebmotorsSoapAuthClientTest {

    @Test
    void authenticateRetriesOnTransientFailureAndSanitizesSoapBody() {
        ArrayDeque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(new FakeHttpResponse(503, "<error>temporario</error>"));
        responses.add(new FakeHttpResponse(200, """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wm="http://webmotors.com.br/">
                  <soap:Body>
                    <wm:AutenticarResponse>
                      <wm:HashAutenticacao>hash-abc</wm:HashAutenticacao>
                      <wm:CodigoRetorno>0</wm:CodigoRetorno>
                      <wm:RequestId>req-auth</wm:RequestId>
                    </wm:AutenticarResponse>
                  </soap:Body>
                </soap:Envelope>
                """));
        List<Long> sleeps = new ArrayList<>();

        WebmotorsSoapAuthClient client = new WebmotorsSoapAuthClient(
                new WebmotorsSoapRequestFactory(),
                new WebmotorsSoapResponseParser(),
                request -> responses.removeFirst(),
                sleeps::add
        );

        var transport = client.authenticate(credentials());

        assertThat(transport.payload().hashAutenticacao()).isEqualTo("hash-abc");
        assertThat(transport.payload().codigoRetorno()).isEqualTo("0");
        assertThat(transport.payload().requestId()).isEqualTo("req-auth");
        assertThat(sleeps).containsExactly(300L);
        assertThat(transport.sanitizedRequest()).contains("<Senha>***</Senha>");
        assertThat(transport.sanitizedRequest()).doesNotContain("senha-super-secreta");
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
                "user-rest",
                "pass-rest",
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
