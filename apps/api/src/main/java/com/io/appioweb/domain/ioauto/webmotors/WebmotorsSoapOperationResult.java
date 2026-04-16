package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsSoapOperationResult(
        String codigoRetorno,
        String requestId,
        String remoteAdCode,
        String remoteStatus,
        String rawPayloadJson
) {
}
