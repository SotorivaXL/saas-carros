package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsSoapAuthResult(
        String hashAutenticacao,
        String codigoRetorno,
        String requestId
) {
}
