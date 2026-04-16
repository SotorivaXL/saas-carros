package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsTransportResult<T>(
        T payload,
        int statusCode,
        String sanitizedRequest,
        String sanitizedResponse
) {
}
