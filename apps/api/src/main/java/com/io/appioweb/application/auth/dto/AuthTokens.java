package com.io.appioweb.application.auth.dto;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds
) {}
