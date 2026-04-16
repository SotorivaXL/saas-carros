package com.io.appioweb.adapters.web.aiagents.response;

public record KanbanEvaluateHttpResponse(
        String decision,
        boolean moved,
        String targetStageId,
        Double confidence,
        String reason,
        String errorCode,
        String evaluationKey,
        String llmRequestId
) {
}
