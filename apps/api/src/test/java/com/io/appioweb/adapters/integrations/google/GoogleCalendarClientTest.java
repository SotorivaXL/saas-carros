package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.persistence.googlecalendar.CompanyGoogleOAuthRepositoryJpa;
import com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus;
import com.io.appioweb.adapters.persistence.googlecalendar.JpaCompanyGoogleOAuthEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleCalendarClientTest {

    @Test
    void exchangeAuthorizationCodeSavesEncryptedRefreshToken() {
        CompanyGoogleOAuthRepositoryJpa repository = mock(CompanyGoogleOAuthRepositoryJpa.class);
        when(repository.findByCompanyId(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SensitiveDataCrypto crypto = new SensitiveDataCrypto("test-encryption-key-1234567890");
        GoogleOAuthProperties properties = new GoogleOAuthProperties(
                "client-id",
                "client-secret",
                "https://app.local/callback",
                "openid email https://www.googleapis.com/auth/calendar"
        );
        Instant now = Instant.parse("2026-03-04T12:00:00Z");

        GoogleCalendarClient client = new GoogleCalendarClient(
                repository,
                crypto,
                properties,
                request -> new FakeHttpResponse(
                        200,
                        "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600,\"scope\":\"scope-a\",\"id_token\":\"" + idTokenWithEmail("owner@example.com") + "\"}"
                ),
                millis -> { },
                Clock.fixed(now, ZoneOffset.UTC)
        );

        GoogleOAuthConnectionResult result = client.exchangeAuthorizationCode(UUID.fromString("11111111-1111-1111-1111-111111111111"), "code-123");

        ArgumentCaptor<JpaCompanyGoogleOAuthEntity> captor = ArgumentCaptor.forClass(JpaCompanyGoogleOAuthEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        JpaCompanyGoogleOAuthEntity saved = captor.getValue();

        assertEquals("refresh-1", crypto.decrypt(saved.getRefreshTokenEncrypted()));
        assertEquals("access-1", crypto.decrypt(saved.getAccessTokenEncrypted()));
        assertEquals("owner@example.com", saved.getGoogleUserEmail());
        assertEquals(GoogleConnectionStatus.CONNECTED, saved.getStatus());
        assertEquals(GoogleConnectionStatus.CONNECTED, result.status());
        assertEquals(now.plusSeconds(3600), saved.getAccessTokenExpiresAt());
    }

    @Test
    void refreshAccessTokenUpdatesStoredAccessToken() {
        CompanyGoogleOAuthRepositoryJpa repository = mock(CompanyGoogleOAuthRepositoryJpa.class);
        SensitiveDataCrypto crypto = new SensitiveDataCrypto("test-encryption-key-1234567890");
        JpaCompanyGoogleOAuthEntity entity = connectedEntity(crypto, "refresh-1", "old-access", Instant.parse("2026-03-04T12:10:00Z"));
        when(repository.findByCompanyId(any())).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GoogleCalendarClient client = new GoogleCalendarClient(
                repository,
                crypto,
                new GoogleOAuthProperties("client-id", "client-secret", "https://app.local/callback", "scope-a"),
                request -> new FakeHttpResponse(200, "{\"access_token\":\"new-access\",\"expires_in\":1800}"),
                millis -> { },
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        String refreshed = client.refreshAccessToken(entity.getCompanyId());

        assertEquals("new-access", refreshed);
        assertEquals("new-access", crypto.decrypt(entity.getAccessTokenEncrypted()));
        assertEquals(Instant.parse("2026-03-04T12:30:00Z"), entity.getAccessTokenExpiresAt());
        assertEquals(GoogleConnectionStatus.CONNECTED, entity.getStatus());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    void freeBusyParsesBusyWindows() {
        SensitiveDataCrypto crypto = new SensitiveDataCrypto("test-encryption-key-1234567890");
        JpaCompanyGoogleOAuthEntity entity = connectedEntity(crypto, "refresh-1", "access-1", Instant.parse("2026-03-04T15:00:00Z"));
        CompanyGoogleOAuthRepositoryJpa repository = mock(CompanyGoogleOAuthRepositoryJpa.class);
        when(repository.findByCompanyId(entity.getCompanyId())).thenReturn(Optional.of(entity));

        GoogleCalendarClient client = new GoogleCalendarClient(
                repository,
                crypto,
                new GoogleOAuthProperties("client-id", "client-secret", "https://app.local/callback", "scope-a"),
                request -> new FakeHttpResponse(200, """
                        {
                          "calendars": {
                            "primary": {
                              "busy": [
                                {"start":"2026-03-05T12:00:00Z","end":"2026-03-05T12:30:00Z"},
                                {"start":"2026-03-05T14:00:00Z","end":"2026-03-05T15:00:00Z"}
                              ]
                            }
                          }
                        }
                        """),
                millis -> { },
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        List<GoogleCalendarBusyWindow> busy = client.freeBusy(
                entity.getCompanyId(),
                Instant.parse("2026-03-05T09:00:00Z"),
                Instant.parse("2026-03-05T18:00:00Z"),
                "primary"
        );

        assertEquals(2, busy.size());
        assertEquals(Instant.parse("2026-03-05T12:00:00Z"), busy.get(0).start());
        assertEquals(Instant.parse("2026-03-05T15:00:00Z"), busy.get(1).end());
    }

    @Test
    void createEventWithMeetReturnsImmediateHangoutLink() {
        SensitiveDataCrypto crypto = new SensitiveDataCrypto("test-encryption-key-1234567890");
        JpaCompanyGoogleOAuthEntity entity = connectedEntity(crypto, "refresh-1", "access-1", Instant.parse("2026-03-04T15:00:00Z"));
        CompanyGoogleOAuthRepositoryJpa repository = mock(CompanyGoogleOAuthRepositoryJpa.class);
        when(repository.findByCompanyId(entity.getCompanyId())).thenReturn(Optional.of(entity));

        List<URI> requestedUris = new ArrayList<>();
        GoogleCalendarClient client = new GoogleCalendarClient(
                repository,
                crypto,
                new GoogleOAuthProperties("client-id", "client-secret", "https://app.local/callback", "scope-a"),
                request -> {
                    requestedUris.add(request.uri());
                    return new FakeHttpResponse(200, "{\"id\":\"evt-1\",\"htmlLink\":\"https://calendar.google.com/event?1\",\"hangoutLink\":\"https://meet.google.com/abc-defg\"}");
                },
                millis -> { },
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        GoogleCalendarEventResult result = client.createEventWithMeet(
                entity.getCompanyId(),
                "primary",
                "Reuniao",
                "Descricao",
                Instant.parse("2026-03-05T13:00:00Z"),
                Instant.parse("2026-03-05T13:30:00Z"),
                "America/Sao_Paulo"
        );

        assertEquals("https://meet.google.com/abc-defg", result.meetLink());
        assertEquals(1, requestedUris.size());
        assertTrue(requestedUris.get(0).toString().contains("conferenceDataVersion=1"));
    }

    @Test
    void createEventWithMeetFetchesEventWhenHangoutLinkIsDelayed() {
        SensitiveDataCrypto crypto = new SensitiveDataCrypto("test-encryption-key-1234567890");
        JpaCompanyGoogleOAuthEntity entity = connectedEntity(crypto, "refresh-1", "access-1", Instant.parse("2026-03-04T15:00:00Z"));
        CompanyGoogleOAuthRepositoryJpa repository = mock(CompanyGoogleOAuthRepositoryJpa.class);
        when(repository.findByCompanyId(entity.getCompanyId())).thenReturn(Optional.of(entity));

        ArrayDeque<HttpResponse<String>> responses = new ArrayDeque<>();
        responses.add(new FakeHttpResponse(200, "{\"id\":\"evt-1\",\"htmlLink\":\"https://calendar.google.com/event?1\"}"));
        responses.add(new FakeHttpResponse(200, "{\"id\":\"evt-1\",\"htmlLink\":\"https://calendar.google.com/event?1\"}"));
        responses.add(new FakeHttpResponse(200, "{\"id\":\"evt-1\",\"htmlLink\":\"https://calendar.google.com/event?1\",\"hangoutLink\":\"https://meet.google.com/xyz-abcd\"}"));

        GoogleCalendarClient client = new GoogleCalendarClient(
                repository,
                crypto,
                new GoogleOAuthProperties("client-id", "client-secret", "https://app.local/callback", "scope-a"),
                request -> responses.removeFirst(),
                millis -> { },
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        GoogleCalendarEventResult result = client.createEventWithMeet(
                entity.getCompanyId(),
                "primary",
                "Reuniao",
                "Descricao",
                Instant.parse("2026-03-05T13:00:00Z"),
                Instant.parse("2026-03-05T13:30:00Z"),
                "America/Sao_Paulo"
        );

        assertEquals("https://meet.google.com/xyz-abcd", result.meetLink());
        assertTrue(responses.isEmpty());
    }

    private static JpaCompanyGoogleOAuthEntity connectedEntity(SensitiveDataCrypto crypto, String refreshToken, String accessToken, Instant expiresAt) {
        JpaCompanyGoogleOAuthEntity entity = new JpaCompanyGoogleOAuthEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompanyId(UUID.randomUUID());
        entity.setRefreshTokenEncrypted(crypto.encrypt(refreshToken));
        entity.setAccessTokenEncrypted(crypto.encrypt(accessToken));
        entity.setAccessTokenExpiresAt(expiresAt);
        entity.setScopes("scope-a");
        entity.setStatus(GoogleConnectionStatus.CONNECTED);
        entity.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        return entity;
    }

    private static String idTokenWithEmail(String email) {
        String payload = "{\"email\":\"" + email + "\"}";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        return "header." + encoded + ".signature";
    }

    private static final class FakeHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        private FakeHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://example.test"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
