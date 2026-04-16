package com.io.appioweb.adapters.web.aiagents.response;

import java.util.UUID;

public record AiAgentToolHandoffHttpResponse(
        boolean transferred,
        UUID targetUserId,
        String protocol
) {
}
