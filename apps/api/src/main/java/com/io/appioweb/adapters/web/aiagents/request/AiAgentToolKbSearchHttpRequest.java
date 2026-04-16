package com.io.appioweb.adapters.web.aiagents.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record AiAgentToolKbSearchHttpRequest(
        @NotNull UUID conversationId,
        @NotBlank String agentId,
        @NotBlank String query,
        @Min(1) @Max(10) Integer topK,
        JsonNode filters
) {
}
