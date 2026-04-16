package com.io.appioweb.adapters.web.auth.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {}
