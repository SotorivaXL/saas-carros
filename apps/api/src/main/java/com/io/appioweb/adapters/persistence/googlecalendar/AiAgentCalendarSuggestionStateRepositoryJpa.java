package com.io.appioweb.adapters.persistence.googlecalendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AiAgentCalendarSuggestionStateRepositoryJpa extends JpaRepository<JpaAiAgentCalendarSuggestionStateEntity, UUID> {
    Optional<JpaAiAgentCalendarSuggestionStateEntity> findByCompanyIdAndConversationId(UUID companyId, UUID conversationId);
    void deleteAllByCompanyIdAndConversationIdIn(UUID companyId, Collection<UUID> conversationIds);
}
