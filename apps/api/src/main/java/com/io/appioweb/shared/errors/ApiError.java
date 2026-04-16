package com.io.appioweb.shared.errors;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp
) {}
