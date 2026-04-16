package com.io.appioweb.adapters.web.aiagents.response;

import tools.jackson.databind.JsonNode;

public record AiAgentStateHttpResponse(
        JsonNode providers,
        JsonNode agents,
        JsonNode knowledgeBase
) {
}
