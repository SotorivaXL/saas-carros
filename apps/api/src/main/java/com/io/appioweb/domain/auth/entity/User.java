package com.io.appioweb.domain.auth.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record User(
        UUID id,
        UUID companyId,
        String email,
        String passwordHash,
        String fullName,
        String profileImageUrl,
        String jobTitle,
        LocalDate birthDate,
        String permissionPreset,
        Set<String> modulePermissions,
        UUID teamId,
        boolean isActive,
        Instant createdAt,
        Set<String> roles
) {}
