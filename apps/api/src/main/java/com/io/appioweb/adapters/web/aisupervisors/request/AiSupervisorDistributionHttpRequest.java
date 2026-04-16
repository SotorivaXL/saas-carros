package com.io.appioweb.adapters.web.aisupervisors.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiSupervisorDistributionHttpRequest(
        @Size(max = 2000) String otherRules,
        List<@Valid AiSupervisorAgentDistributionItemHttpRequest> agents
) {
    public record AiSupervisorAgentDistributionItemHttpRequest(
            @Size(max = 120) String agentId,
            Boolean enabled,
            @Size(max = 1200) String triageText
    ) {
    }
}
