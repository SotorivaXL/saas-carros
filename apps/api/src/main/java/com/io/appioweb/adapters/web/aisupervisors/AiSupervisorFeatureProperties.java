package com.io.appioweb.adapters.web.aisupervisors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class AiSupervisorFeatureProperties {

    private final boolean enabled;
    private final Set<UUID> enabledCompanies;
    private final int initialBatchWindowSeconds;
    private final int cooldownSeconds;
    private final int maxInitialMessages;
    private final int maxMessageChars;
    private final int maxCandidateTriageChars;
    private final int maxOtherRulesChars;
    private final int maxClarifyingQuestionChars;
    private final int candidateReductionThreshold;
    private final int maxCandidatesAfterReduction;
    private final int maxOutputTokens;
    private final int retryOnOptimisticConflict;

    public AiSupervisorFeatureProperties(
            @Value("${ai.supervisor.enabled:true}") boolean enabled,
            @Value("${ai.supervisor.enabled-companies:}") String enabledCompaniesRaw,
            @Value("${ai.supervisor.initial-batch-window-seconds:20}") int initialBatchWindowSeconds,
            @Value("${ai.supervisor.cooldown-seconds:15}") int cooldownSeconds,
            @Value("${ai.supervisor.max-initial-messages:3}") int maxInitialMessages,
            @Value("${ai.supervisor.max-message-chars:280}") int maxMessageChars,
            @Value("${ai.supervisor.max-candidate-triage-chars:260}") int maxCandidateTriageChars,
            @Value("${ai.supervisor.max-other-rules-chars:700}") int maxOtherRulesChars,
            @Value("${ai.supervisor.max-clarifying-question-chars:220}") int maxClarifyingQuestionChars,
            @Value("${ai.supervisor.candidate-reduction-threshold:10}") int candidateReductionThreshold,
            @Value("${ai.supervisor.max-candidates-after-reduction:8}") int maxCandidatesAfterReduction,
            @Value("${ai.supervisor.max-output-tokens:180}") int maxOutputTokens,
            @Value("${ai.supervisor.retry-on-optimistic-conflict:1}") int retryOnOptimisticConflict
    ) {
        this.enabled = enabled;
        this.enabledCompanies = parseCompanies(enabledCompaniesRaw);
        this.initialBatchWindowSeconds = clamp(initialBatchWindowSeconds, 5, 60, 20);
        this.cooldownSeconds = clamp(cooldownSeconds, 0, 120, 15);
        this.maxInitialMessages = clamp(maxInitialMessages, 1, 5, 3);
        this.maxMessageChars = clamp(maxMessageChars, 80, 600, 280);
        this.maxCandidateTriageChars = clamp(maxCandidateTriageChars, 80, 400, 260);
        this.maxOtherRulesChars = clamp(maxOtherRulesChars, 120, 2000, 700);
        this.maxClarifyingQuestionChars = clamp(maxClarifyingQuestionChars, 60, 280, 220);
        this.candidateReductionThreshold = clamp(candidateReductionThreshold, 2, 50, 10);
        this.maxCandidatesAfterReduction = clamp(maxCandidatesAfterReduction, 2, 12, 8);
        this.maxOutputTokens = clamp(maxOutputTokens, 80, 300, 180);
        this.retryOnOptimisticConflict = clamp(retryOnOptimisticConflict, 0, 3, 1);
    }

    public boolean isEnabledFor(UUID companyId) {
        if (!enabled) return false;
        if (enabledCompanies.isEmpty()) return true;
        return enabledCompanies.contains(companyId);
    }

    public int initialBatchWindowSeconds() {
        return initialBatchWindowSeconds;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int maxInitialMessages() {
        return maxInitialMessages;
    }

    public int maxMessageChars() {
        return maxMessageChars;
    }

    public int maxCandidateTriageChars() {
        return maxCandidateTriageChars;
    }

    public int maxOtherRulesChars() {
        return maxOtherRulesChars;
    }

    public int maxClarifyingQuestionChars() {
        return maxClarifyingQuestionChars;
    }

    public int candidateReductionThreshold() {
        return candidateReductionThreshold;
    }

    public int maxCandidatesAfterReduction() {
        return maxCandidatesAfterReduction;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public int retryOnOptimisticConflict() {
        return retryOnOptimisticConflict;
    }

    private Set<UUID> parseCompanies(String raw) {
        Set<UUID> companies = new HashSet<>();
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return companies;
        String[] parts = value.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (token.isBlank() || "*".equals(token)) {
                companies.clear();
                return companies;
            }
            try {
                companies.add(UUID.fromString(token));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed ids
            }
        }
        return companies;
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }
}
