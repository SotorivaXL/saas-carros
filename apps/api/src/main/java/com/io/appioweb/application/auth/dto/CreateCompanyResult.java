package com.io.appioweb.application.auth.dto;

import java.util.UUID;

public record CreateCompanyResult(
        UUID companyId,
        UUID ownerUserId,
        String ownerEmail
) {}
