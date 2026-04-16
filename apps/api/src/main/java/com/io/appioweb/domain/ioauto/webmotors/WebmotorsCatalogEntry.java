package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsCatalogEntry(
        String type,
        String internalValue,
        String webmotorsCode,
        String webmotorsLabel,
        String rawPayloadJson
) {
}
