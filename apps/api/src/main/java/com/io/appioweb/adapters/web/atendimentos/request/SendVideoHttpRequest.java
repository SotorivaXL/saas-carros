package com.io.appioweb.adapters.web.atendimentos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendVideoHttpRequest(
        @NotBlank String phone,
        @NotBlank String video,
        @Min(1) @Max(15) Integer delayMessage,
        String caption,
        Boolean viewOnce,
        String messageId,
        @JsonProperty("async") Boolean asyncSend
) {}
