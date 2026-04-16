package com.io.appioweb.adapters.web.aiagents.request;

import tools.jackson.databind.JsonNode;

public record AiAgentStateHttpRequest(
        JsonNode providers,
        JsonNode agents,
        JsonNode knowledgeBase
) {
}
