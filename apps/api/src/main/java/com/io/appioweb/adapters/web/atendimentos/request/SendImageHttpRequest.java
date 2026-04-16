package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendImageHttpRequest(
        @NotBlank String phone,
        @NotBlank String image,
        String caption,
        @Min(1) @Max(15) Integer delayMessage,
        Boolean viewOnce,
        String messageId
) {}
