package com.io.appioweb.adapters.web.aiagents.response;

import java.time.Instant;
import java.util.List;

public record KanbanStageRuleHttpResponse(
        String stageId,
        boolean enabled,
        String prompt,
        Integer priority,
        Boolean onlyForwardOverride,
        List<String> allowedFromStages,
        Instant updatedAt
) {
}
