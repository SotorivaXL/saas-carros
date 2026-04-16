package com.io.appioweb.adapters.persistence.aiagents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiAgentKanbanMoveAttemptRepositoryJpa extends JpaRepository<JpaAiAgentKanbanMoveAttemptEntity, UUID> {
    boolean existsByCompanyIdAndAgentIdAndConversationIdAndCardIdAndEvaluationKeyAndDecision(
            UUID companyId,
            String agentId,
            UUID conversationId,
            String cardId,
            String evaluationKey,
            AiAgentKanbanMoveDecision decision
    );

    List<JpaAiAgentKanbanMoveAttemptEntity> findTop50ByCompanyIdAndConversationIdAndCardIdOrderByCreatedAtDesc(
            UUID companyId,
            UUID conversationId,
            String cardId
    );

    void deleteAllByCompanyIdAndConversationIdIn(UUID companyId, Collection<UUID> conversationIds);
}
