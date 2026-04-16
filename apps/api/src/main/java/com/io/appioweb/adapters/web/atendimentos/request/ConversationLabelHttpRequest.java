package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationLabelHttpRequest(
        @NotBlank @Size(max = 120) String id,
        @NotBlank @Size(max = 180) String title,
        @Size(max = 7) String color
) {
}
