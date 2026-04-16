package com.io.appioweb.adapters.web.atendimentos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendAudioHttpRequest(
        @NotBlank String phone,
        @NotBlank String audio,
        @Min(1) @Max(15) Integer delayMessage,
        @Min(0) @Max(15) Integer delayTyping,
        Boolean viewOnce,
        Boolean waveform,
        @JsonProperty("async") Boolean asyncSend
) {}
