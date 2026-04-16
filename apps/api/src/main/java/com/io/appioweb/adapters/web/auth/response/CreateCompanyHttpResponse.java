package com.io.appioweb.adapters.web.auth.response;

import java.util.UUID;

public record CreateCompanyHttpResponse(
        UUID companyId,
        UUID ownerUserId,
        String ownerEmail
) {}
