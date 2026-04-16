package com.io.appioweb.adapters.web.aiagents.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record AiAgentOrchestrateHttpRequest(
        @NotNull UUID conversationId,
        @NotBlank String customerMessage,
        @NotBlank String agentId,
        String channel,
        JsonNode customerContext
) {
}
