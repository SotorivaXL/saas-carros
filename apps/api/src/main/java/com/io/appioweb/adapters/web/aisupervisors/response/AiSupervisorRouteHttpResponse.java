package com.io.appioweb.adapters.web.aisupervisors.response;

import com.io.appioweb.adapters.web.aisupervisors.AiSupervisorAction;

import java.util.UUID;

public record AiSupervisorRouteHttpResponse(
        AiSupervisorAction action,
        String targetAgentId,
        UUID outboundMessageId,
        String evaluationKey,
        String reason,
        String errorCode,
        boolean duplicate
) {
}
