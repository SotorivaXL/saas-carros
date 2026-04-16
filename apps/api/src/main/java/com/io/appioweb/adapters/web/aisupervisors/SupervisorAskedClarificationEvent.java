package com.io.appioweb.adapters.web.aisupervisors;

import java.time.Instant;
import java.util.UUID;

public record SupervisorAskedClarificationEvent(
        UUID companyId,
        UUID supervisorId,
        UUID conversationId,
        UUID outboundMessageId,
        String evaluationKey,
        Instant occurredAt
) {
}
