package com.io.appioweb.adapters.web.aiagents.request;

import java.util.List;
import java.util.UUID;

public record KanbanEvaluateHttpRequest(
        UUID conversationId,
        String cardId,
        String agentId,
        List<String> events,
        Boolean force
) {
}
