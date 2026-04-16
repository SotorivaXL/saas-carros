package com.io.appioweb.adapters.web.aisupervisors;

public interface AiSupervisorLlmClient {

    LlmResponse classify(LlmRequest request);

    record LlmRequest(
            String apiKey,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxOutputTokens
    ) {
    }

    record LlmResponse(
            String requestId,
            String outputText,
            int inputTokens,
            int outputTokens
    ) {
    }
}
