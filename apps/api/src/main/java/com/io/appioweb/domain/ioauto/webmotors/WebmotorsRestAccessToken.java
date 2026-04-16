package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsRestAccessToken(
        String accessToken,
        long expiresInSeconds
) {
}
