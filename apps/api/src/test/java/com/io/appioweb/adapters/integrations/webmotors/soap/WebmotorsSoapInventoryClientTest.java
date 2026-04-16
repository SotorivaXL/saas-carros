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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebmotorsSoapInventoryClientTest {

    @Test
    void listCurrentInventoryPageRetriesAndParsesRemoteAds() {
        ArrayDeque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(new FakeHttpResponse(429, "{\"message\":\"rate limit\"}"));
        responses.add(new FakeHttpResponse(200, """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wm="http://webmotors.com.br/">
                  <soap:Body>
                    <wm:ObterEstoqueAtualPaginadoResponse>
                      <wm:Pagina>1</wm:Pagina>
                      <wm:AnunciosPorPagina>2</wm:AnunciosPorPagina>
                      <wm:TotalAnuncios>2</wm:TotalAnuncios>
                      <wm:CodigoRetorno>0</wm:CodigoRetorno>
                      <wm:RequestId>req-page</wm:RequestId>
                      <wm:Anuncio>
                        <wm:CodigoAnuncio>WM-1</wm:CodigoAnuncio>
                        <wm:Titulo>Tracker Premier</wm:Titulo>
                        <wm:PrecoVenda>999900</wm:PrecoVenda>
                        <wm:Quilometragem>10000</wm:Quilometragem>
                        <wm:Status>PUBLISHED</wm:Status>
                      </wm:Anuncio>
                      <wm:Anuncio>
                        <wm:CodigoAnuncio>WM-2</wm:CodigoAnuncio>
                        <wm:Descricao>Nivus Highline</wm:Descricao>
                        <wm:Preco>1200000</wm:Preco>
                        <wm:Km>25000</wm:Km>
                        <wm:Situacao>UPDATED</wm:Situacao>
                      </wm:Anuncio>
                    </wm:ObterEstoqueAtualPaginadoResponse>
                  </soap:Body>
                </soap:Envelope>
                """));
        List<Long> sleeps = new ArrayList<>();

        WebmotorsSoapInventoryClient client = new WebmotorsSoapInventoryClient(
                new WebmotorsSoapRequestFactory(),
                new WebmotorsSoapResponseParser(),
                request -> responses.removeFirst(),
                sleeps::add
        );

        var transport = client.listCurrentInventoryPage(credentials(), "hash-interno", 1, 50);

        assertThat(transport.payload().anuncios()).hasSize(2);
        assertThat(transport.payload().anuncios().getFirst().codigoAnuncio()).isEqualTo("WM-1");
        assertThat(transport.payload().anuncios().get(1).titulo()).isEqualTo("Nivus Highline");
        assertThat(transport.sanitizedRequest()).contains("<HashAutenticacao>***</HashAutenticacao>");
        assertThat(transport.sanitizedRequest()).doesNotContain("hash-interno");
        assertThat(sleeps).containsExactly(300L);
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
