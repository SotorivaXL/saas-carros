package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.adapters.integrations.webmotors.WebmotorsPayloadSanitizer;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsSoapAuthResult;
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

@Component
public class WebmotorsSoapAuthClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final WebmotorsSoapRequestFactory requestFactory;
    private final WebmotorsSoapResponseParser responseParser;
    private final HttpRequestExecutor httpRequestExecutor;
    private final Sleeper sleeper;

    @Autowired
    public WebmotorsSoapAuthClient(
            WebmotorsSoapRequestFactory requestFactory,
            WebmotorsSoapResponseParser responseParser
    ) {
        this(requestFactory, responseParser, request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()), millis -> Thread.sleep(millis));
    }

    WebmotorsSoapAuthClient(
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

    public WebmotorsTransportResult<WebmotorsSoapAuthResult> authenticate(WebmotorsCredentialSnapshot credentials) {
        String requestBody = requestFactory.buildAuthRequest(credentials.soapCnpj(), credentials.soapEmail(), credentials.soapPassword());
        HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(credentials.soapBaseUrl(), credentials.soapAuthPath())))
                .header("Content-Type", "text/xml; charset=utf-8")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(request, List.of(300L, 900L), "soap-auth");
        WebmotorsSoapAuthResult parsed = responseParser.parseAuth(response.body());
        return new WebmotorsTransportResult<>(
                parsed,
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, List<Long> backoff, String operation) {
        HttpResponse<String> last = null;
        for (int attempt = 0; attempt < backoff.size() + 1; attempt++) {
            if (attempt > 0) {
                sleep(backoff.get(attempt - 1));
            }
            try {
                last = httpRequestExecutor.send(request);
                if (last.statusCode() >= 500 || last.statusCode() == 429) {
                    continue;
                }
                return last;
            } catch (Exception ignored) {
                // tenta novamente
            }
        }
        if (last != null) {
            return last;
        }
        throw new BusinessException("WEBMOTORS_SOAP_AUTH_FAILED", "Não foi possível autenticar no Webmotors SOAP.");
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("WEBMOTORS_SOAP_AUTH_INTERRUPTED", "A autenticação SOAP da Webmotors foi interrompida.");
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_SOAP_AUTH_FAILED", "Não foi possível autenticar no Webmotors SOAP.");
        }
    }

    private String resolveUrl(String baseUrl, String path) {
        String base = safe(baseUrl);
        String suffix = safe(path);
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
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
