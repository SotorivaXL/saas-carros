package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsLeadPayload(
        String externalLeadId,
        String remoteAdCode,
        String customerName,
        String customerEmail,
        String customerPhone,
        String message,
        String rawPayloadJson
) {
}
