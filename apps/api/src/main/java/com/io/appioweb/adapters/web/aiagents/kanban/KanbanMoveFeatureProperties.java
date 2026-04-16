package com.io.appioweb.adapters.web.aiagents.kanban;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class KanbanMoveFeatureProperties {

    private final boolean enabled;
    private final Set<UUID> enabledCompanies;
    private final boolean defaultOnlyForward;
    private final int maxMessages;
    private final int maxMessageChars;
    private final int cooldownSeconds;
    private final boolean blockWhenFinalStage;
    private final int shortMessageMinLength;
    private final int maxOutputTokens;

    public KanbanMoveFeatureProperties(
            @Value("${ai.kanban-move.enabled:true}") boolean enabled,
            @Value("${ai.kanban-move.enabled-companies:}") String enabledCompaniesRaw,
            @Value("${ai.kanban-move.default-only-forward:true}") boolean defaultOnlyForward,
            @Value("${ai.kanban-move.max-messages:6}") int maxMessages,
            @Value("${ai.kanban-move.max-message-chars:240}") int maxMessageChars,
            @Value("${ai.kanban-move.cooldown-seconds:20}") int cooldownSeconds,
            @Value("${ai.kanban-move.block-when-final-stage:true}") boolean blockWhenFinalStage,
            @Value("${ai.kanban-move.short-message-min-length:4}") int shortMessageMinLength,
            @Value("${ai.kanban-move.max-output-tokens:180}") int maxOutputTokens
    ) {
        this.enabled = enabled;
        this.enabledCompanies = parseCompanies(enabledCompaniesRaw);
        this.defaultOnlyForward = defaultOnlyForward;
        this.maxMessages = clamp(maxMessages, 2, 12, 6);
        this.maxMessageChars = clamp(maxMessageChars, 80, 800, 240);
        this.cooldownSeconds = clamp(cooldownSeconds, 0, 180, 20);
        this.blockWhenFinalStage = blockWhenFinalStage;
        this.shortMessageMinLength = clamp(shortMessageMinLength, 1, 20, 4);
        this.maxOutputTokens = clamp(maxOutputTokens, 80, 300, 180);
    }

    public boolean isEnabledFor(UUID companyId) {
        if (!enabled) return false;
        if (enabledCompanies.isEmpty()) return true;
        return enabledCompanies.contains(companyId);
    }

    public boolean defaultOnlyForward() {
        return defaultOnlyForward;
    }

    public int maxMessages() {
        return maxMessages;
    }

    public int maxMessageChars() {
        return maxMessageChars;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean blockWhenFinalStage() {
        return blockWhenFinalStage;
    }

    public int shortMessageMinLength() {
        return shortMessageMinLength;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
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
                // ignore malformed ids from env
            }
        }
        return companies;
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }
}
