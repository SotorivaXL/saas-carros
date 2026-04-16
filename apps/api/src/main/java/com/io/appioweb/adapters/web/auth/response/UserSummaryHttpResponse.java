package com.io.appioweb.adapters.web.auth.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UserSummaryHttpResponse(
        UUID id,
        UUID companyId,
        String email,
        String fullName,
        String profileImageUrl,
        String jobTitle,
        LocalDate birthDate,
        String permissionPreset,
        Set<String> modulePermissions,
        UUID teamId,
        String teamName,
        boolean active,
        Instant createdAt,
        Set<String> roles
) {}
