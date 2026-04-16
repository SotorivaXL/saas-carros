package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.adapters.integrations.webmotors.WebmotorsPayloadSanitizer;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCatalogEntry;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsTransportResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class WebmotorsSoapCatalogClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final WebmotorsSoapRequestFactory requestFactory;
    private final WebmotorsSoapResponseParser responseParser;

    public WebmotorsSoapCatalogClient(WebmotorsSoapRequestFactory requestFactory, WebmotorsSoapResponseParser responseParser) {
        this.requestFactory = requestFactory;
        this.responseParser = responseParser;
    }

    public WebmotorsTransportResult<List<WebmotorsCatalogEntry>> fetchCatalog(WebmotorsCredentialSnapshot credentials, String hashAutenticacao, String type) throws Exception {
        String requestBody = requestFactory.buildCatalogRequest(hashAutenticacao, type);
        HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(credentials.soapBaseUrl(), credentials.soapCatalogPath())))
                .header("Content-Type", "text/xml; charset=utf-8")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return new WebmotorsTransportResult<>(
                responseParser.parseCatalog(response.body(), type),
                response.statusCode(),
                WebmotorsPayloadSanitizer.sanitize(requestBody),
                WebmotorsPayloadSanitizer.sanitize(response.body())
        );
    }

    private String resolveUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String suffix = path == null ? "" : path.trim();
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
}
