package com.io.appioweb.domain.ioauto.webmotors;

import java.util.List;

public record WebmotorsInventoryPage(
        int pagina,
        int anunciosPorPagina,
        int totalAnuncios,
        String codigoRetorno,
        List<WebmotorsInventoryItem> anuncios,
        String requestId
) {
}
