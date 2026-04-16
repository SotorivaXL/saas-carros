package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendDocumentHttpRequest(
        @NotBlank String phone,
        @NotBlank String document,
        @NotBlank String extension,
        String fileName,
        String caption,
        @Min(1) @Max(15) Integer delayMessage,
        String messageId
) {}
