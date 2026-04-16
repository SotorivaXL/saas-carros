package com.io.appioweb.adapters.web.aiagents.kanban;

import java.time.Instant;
import java.util.UUID;

public record CardStageMovedEvent(
        UUID companyId,
        String agentId,
        UUID conversationId,
        String cardId,
        String fromStageId,
        String toStageId,
        String evaluationKey,
        Instant occurredAt
) {
}
