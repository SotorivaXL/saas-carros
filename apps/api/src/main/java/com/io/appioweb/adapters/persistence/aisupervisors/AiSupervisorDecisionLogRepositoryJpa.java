package com.io.appioweb.adapters.persistence.aisupervisors;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSupervisorDecisionLogRepositoryJpa extends JpaRepository<JpaAiSupervisorDecisionLogEntity, UUID> {
    Optional<JpaAiSupervisorDecisionLogEntity> findFirstByCompanyIdAndSupervisorIdAndConversationIdAndEvaluationKeyOrderByCreatedAtDesc(
            UUID companyId,
            UUID supervisorId,
            UUID conversationId,
            String evaluationKey
    );

    List<JpaAiSupervisorDecisionLogEntity> findTop20ByCompanyIdAndConversationIdOrderByCreatedAtDesc(UUID companyId, UUID conversationId);
}
