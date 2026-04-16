package com.io.appioweb.adapters.web.auth.response;

import java.time.Instant;
import java.util.UUID;

public record TeamSummaryHttpResponse(
        UUID id,
        UUID companyId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {}
