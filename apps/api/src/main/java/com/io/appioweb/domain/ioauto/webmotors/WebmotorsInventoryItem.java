package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsInventoryItem(
        String codigoAnuncio,
        String codigoMarca,
        String codigoModelo,
        String codigoVersao,
        String titulo,
        Long precoVenda,
        Integer quilometragem,
        String status,
        String dataInclusao,
        String dataUltimaAlteracao,
        String rawPayloadJson
) {
}
