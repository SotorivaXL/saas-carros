package com.io.appioweb.adapters.web.aisupervisors.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AiSupervisorUpsertHttpRequest(
        @Size(max = 180) String name,
        @Size(max = 2000) String communicationStyle,
        @Size(max = 2000) String profile,
        @Size(max = 2000) String objective,
        @Size(max = 80) String reasoningModelVersion,
        @Size(max = 40) String provider,
        @Size(max = 120) String model,
        Boolean humanHandoffEnabled,
        Boolean notifyContactOnAgentTransfer,
        @Size(max = 120) String humanHandoffTeam,
        Boolean humanHandoffSendMessage,
        @Size(max = 500) String humanHandoffMessage,
        @Size(max = 120) String agentIssueHandoffTeam,
        Boolean agentIssueSendMessage,
        Boolean humanUserChoiceEnabled,
        List<@Size(max = 80) String> humanChoiceOptions,
        Boolean enabled,
        Boolean defaultForCompany
) {
}
