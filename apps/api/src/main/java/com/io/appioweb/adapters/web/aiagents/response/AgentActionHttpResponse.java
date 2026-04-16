package com.io.appioweb.adapters.web.aiagents.response;

public record AgentActionHttpResponse(
        String toolName,
        String status,
        String summary
) {
}
