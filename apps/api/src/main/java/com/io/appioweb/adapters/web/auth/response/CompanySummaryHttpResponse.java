package com.io.appioweb.adapters.web.auth.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record CompanySummaryHttpResponse(
        UUID id,
        String name,
        String profileImageUrl,
        String email,
        LocalDate contractEndDate,
        String cnpj,
        LocalDate openedAt,
        String businessHoursStart,
        String businessHoursEnd,
        JsonNode businessHoursWeekly,
        Instant createdAt
) {}
