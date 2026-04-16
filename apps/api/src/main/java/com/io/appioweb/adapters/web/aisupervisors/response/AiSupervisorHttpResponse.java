package com.io.appioweb.adapters.web.aisupervisors.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiSupervisorHttpResponse(
        UUID id,
        String name,
        String communicationStyle,
        String profile,
        String objective,
        String reasoningModelVersion,
        String provider,
        String model,
        boolean humanHandoffEnabled,
        boolean notifyContactOnAgentTransfer,
        String humanHandoffTeam,
        boolean humanHandoffSendMessage,
        String humanHandoffMessage,
        String agentIssueHandoffTeam,
        boolean agentIssueSendMessage,
        boolean humanUserChoiceEnabled,
        List<String> humanChoiceOptions,
        boolean enabled,
        boolean defaultForCompany,
        DistributionHttpResponse distribution,
        Instant createdAt,
        Instant updatedAt
) {
    public record DistributionHttpResponse(
            String otherRules,
            List<AgentRuleHttpResponse> agents
    ) {
    }

    public record AgentRuleHttpResponse(
            String agentId,
            String agentName,
            boolean enabled,
            String triageText,
            Instant updatedAt
    ) {
    }
}
