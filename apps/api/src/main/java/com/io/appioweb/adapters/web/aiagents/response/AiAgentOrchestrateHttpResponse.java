package com.io.appioweb.adapters.web.aiagents.response;

import java.util.List;

public record AiAgentOrchestrateHttpResponse(
        String finalText,
        List<AgentActionHttpResponse> actions,
        List<ToolExecutionLogHttpResponse> toolLogs,
        boolean handoff,
        String traceId
) {
}
