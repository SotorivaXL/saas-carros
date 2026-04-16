package com.io.appioweb.adapters.web.integrations.google;

import com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record GoogleOAuthConnectionHttpResponse(
        UUID companyId,
        String googleUserEmail,
        String scopes,
        GoogleConnectionStatus status,
        Instant updatedAt
) {
}
