package com.io.appioweb.adapters.web.aisupervisors.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record AiSupervisorSimulateHttpRequest(
        @Size(max = 2000) String message,
        @Valid AiSupervisorDistributionHttpRequest distribution
) {
}
