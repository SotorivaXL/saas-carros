package com.io.appioweb.adapters.persistence.aisupervisors;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiSupervisorConversationStateRepositoryJpa extends JpaRepository<JpaAiSupervisorConversationStateEntity, UUID> {
    Optional<JpaAiSupervisorConversationStateEntity> findByCompanyIdAndSupervisorIdAndConversationId(
            UUID companyId,
            UUID supervisorId,
            UUID conversationId
    );
}
