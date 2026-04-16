package com.io.appioweb.adapters.web.aisupervisors.response;

import com.io.appioweb.adapters.web.aisupervisors.AiSupervisorAction;

import java.util.List;

public record AiSupervisorSimulateHttpResponse(
        AiSupervisorAction action,
        String targetAgentId,
        String targetAgentName,
        String messageToSend,
        double confidence,
        String reason,
        String humanQueue,
        List<String> evidence
) {
}
