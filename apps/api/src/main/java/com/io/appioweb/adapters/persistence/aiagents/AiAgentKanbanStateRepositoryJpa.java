package com.io.appioweb.adapters.persistence.aiagents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AiAgentKanbanStateRepositoryJpa extends JpaRepository<JpaAiAgentKanbanStateEntity, JpaAiAgentKanbanStateId> {
    Optional<JpaAiAgentKanbanStateEntity> findByCompanyIdAndAgentIdAndConversationIdAndCardId(
            UUID companyId,
            String agentId,
            UUID conversationId,
            String cardId
    );

    void deleteAllByCompanyIdAndConversationIdIn(UUID companyId, Collection<UUID> conversationIds);
}
