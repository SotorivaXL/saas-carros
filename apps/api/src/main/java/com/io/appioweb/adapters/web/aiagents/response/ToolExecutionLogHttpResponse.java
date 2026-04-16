package com.io.appioweb.adapters.web.aiagents.response;

public record ToolExecutionLogHttpResponse(
        String toolName,
        String status,
        long latencyMs,
        int retries,
        String errorCode
) {
}
