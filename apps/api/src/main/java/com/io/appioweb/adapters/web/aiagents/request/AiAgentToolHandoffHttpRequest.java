package com.io.appioweb.adapters.web.aiagents.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AiAgentToolHandoffHttpRequest(
        @NotNull UUID conversationId,
        @NotBlank String reason,
        @NotBlank String agentId
) {
}
