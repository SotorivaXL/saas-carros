package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.adapters.integrations.webmotors.WebmotorsPayloadSanitizer;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsInventoryPage;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsSoapOperationResult;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsTransportResult;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WebmotorsSoapInventoryClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final WebmotorsSoapRequestFactory requestFactory;
    private final WebmotorsSoapResponseParser responseParser;
    private final HttpRequestExecutor httpRequestExecutor;
    private final Sleeper sleeper;

    @Autowired
    public WebmotorsSoapInventoryClient(WebmotorsSoapRequestFactory requestFactory, WebmotorsSoapResponseParser responseParser) {
        this(requestFactory, responseParser, request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()), millis -> Thread.sleep(millis));
    }

    WebmotorsSoapInventoryClient(
            WebmotorsSoapRequestFactory requestFactory,
            WebmotorsSoapResponseParser responseParser,
            HttpRequestExecutor httpRequestExecutor,
            Sleeper sleeper
    ) {
        this.requestFactory = requestFactory;
        this.responseParser = responseParser;
        this.httpRequestExecutor = httpRequestExecutor;
        this.sleeper = sleeper;
    }

    public WebmotorsTransportResult<WebmotorsInventoryPage> listCurrentInventoryPage(
            WebmotorsCredentialSnapshot credentials,
            String hashAutenticacao,
            int pagina,
            int tamanho
    ) {
        String requestBody = requestFactory.buildInventoryPageRequest(hashAutenticacao, pagina, tamanho);
        HttpResponse<String> response = send(credentials, requestBody);
        return new WebmotorsTransportResult<>(
                responseParser.parseInventoryPage(response.body()),
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    public WebmotorsTransportResult<WebmotorsSoapOperationResult> publishAd(
            WebmotorsCredentialSnapshot credentials,
            String hashAutenticacao,
            Map<String, String> fields
    ) {
        String requestBody = requestFactory.buildPublishRequest(hashAutenticacao, fields);
        HttpResponse<String> response = send(credentials, requestBody);
        return new WebmotorsTransportResult<>(
                responseParser.parseOperationResult(response.body()),
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    public WebmotorsTransportResult<WebmotorsSoapOperationResult> updateAd(
            WebmotorsCredentialSnapshot credentials,
            String hashAutenticacao,
            Map<String, String> fields
    ) {
        String requestBody = requestFactory.buildUpdateRequest(hashAutenticacao, fields);
        HttpResponse<String> response = send(credentials, requestBody);
        return new WebmotorsTransportResult<>(
                responseParser.parseOperationResult(response.body()),
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    public WebmotorsTransportResult<WebmotorsSoapOperationResult> deleteAd(
            WebmotorsCredentialSnapshot credentials,
            String hashAutenticacao,
            String remoteAdCode
    ) {
        String requestBody = requestFactory.buildDeleteRequest(hashAutenticacao, remoteAdCode);
        HttpResponse<String> response = send(credentials, requestBody);
        return new WebmotorsTransportResult<>(
                responseParser.parseOperationResult(response.body()),
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    private HttpResponse<String> send(WebmotorsCredentialSnapshot credentials, String requestBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(credentials.soapBaseUrl(), credentials.soapInventoryPath())))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return sendWithRetry(request, List.of(300L, 1000L));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_SOAP_REQUEST_FAILED", "Não foi possível chamar o serviço SOAP da Webmotors.");
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, List<Long> backoff) throws Exception {
        HttpResponse<String> last = null;
        for (int attempt = 0; attempt < backoff.size() + 1; attempt++) {
            if (attempt > 0) {
                sleeper.sleep(backoff.get(attempt - 1));
            }
            last = httpRequestExecutor.send(request);
            if (last.statusCode() != 429 && last.statusCode() < 500) {
                return last;
            }
        }
        return last;
    }

    private String resolveUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String suffix = path == null ? "" : path.trim();
        if (base.isBlank()) {
            throw new BusinessException("WEBMOTORS_SOAP_BASE_URL_MISSING", "Configure a URL base SOAP da Webmotors.");
        }
        if (suffix.isBlank()) {
            return base;
        }
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base + suffix.substring(1);
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    @FunctionalInterface
    interface HttpRequestExecutor {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws Exception;
    }
}
