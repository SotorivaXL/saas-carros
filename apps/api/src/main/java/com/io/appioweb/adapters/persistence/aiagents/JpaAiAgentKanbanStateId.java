package com.io.appioweb.adapters.persistence.aiagents;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class JpaAiAgentKanbanStateId implements Serializable {

    private UUID companyId;
    private String agentId;
    private UUID conversationId;
    private String cardId;

    public JpaAiAgentKanbanStateId() {
    }

    public JpaAiAgentKanbanStateId(UUID companyId, String agentId, UUID conversationId, String cardId) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.conversationId = conversationId;
        this.cardId = cardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpaAiAgentKanbanStateId that)) return false;
        return Objects.equals(companyId, that.companyId)
                && Objects.equals(agentId, that.agentId)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(cardId, that.cardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, agentId, conversationId, cardId);
    }
}
