package com.io.appioweb.adapters.web.aiagents.request;

import java.util.List;

public record KanbanStageRulesHttpRequest(
        List<KanbanStageRuleItemHttpRequest> rules
) {
    public record KanbanStageRuleItemHttpRequest(
            String stageId,
            Boolean enabled,
            String prompt,
            Integer priority,
            Boolean onlyForwardOverride,
            List<String> allowedFromStages
    ) {
    }
}
