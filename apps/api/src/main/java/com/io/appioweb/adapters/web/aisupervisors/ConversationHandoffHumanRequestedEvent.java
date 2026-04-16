package com.io.appioweb.adapters.web.aisupervisors;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationHandoffHumanRequestedEvent(
        UUID companyId,
        UUID supervisorId,
        UUID conversationId,
        String humanQueue,
        boolean userChoiceEnabled,
        List<String> options,
        String evaluationKey,
        Instant occurredAt
) {
}
