package com.io.appioweb.domain.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record Team(
        UUID id,
        UUID companyId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {}
