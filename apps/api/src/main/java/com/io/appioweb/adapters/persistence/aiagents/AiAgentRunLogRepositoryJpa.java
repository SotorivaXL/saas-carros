package com.io.appioweb.adapters.persistence.aiagents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiAgentRunLogRepositoryJpa extends JpaRepository<JpaAiAgentRunLogEntity, UUID> {
    List<JpaAiAgentRunLogEntity> findTop10ByCompanyIdAndConversationIdOrderByCreatedAtDesc(UUID companyId, UUID conversationId);
    void deleteAllByCompanyIdAndConversationIdIn(UUID companyId, Collection<UUID> conversationIds);
}
