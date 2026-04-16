package com.io.appioweb.adapters.web.aisupervisors.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AiSupervisorRouteHttpRequest(
        @NotNull UUID conversationId,
        @NotNull UUID inboundMessageId
) {
}
