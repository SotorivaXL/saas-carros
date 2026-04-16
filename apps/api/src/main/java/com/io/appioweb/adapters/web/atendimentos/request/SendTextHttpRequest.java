package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendTextHttpRequest(
        @NotBlank String phone,
        @NotBlank String message,
        @Min(1) @Max(15) Integer delayMessage,
        @Min(1) @Max(15) Integer delayTyping,
        String editMessageId
) {}

