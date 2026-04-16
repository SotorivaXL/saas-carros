package com.io.appioweb.adapters.web.aisupervisors;

import java.time.Instant;
import java.util.UUID;

public record ConversationAssignedToAgentEvent(
        UUID companyId,
        UUID supervisorId,
        UUID conversationId,
        String agentId,
        String evaluationKey,
        Instant occurredAt
) {
}
