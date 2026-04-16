package com.io.appioweb.adapters.persistence.aiagents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiAgentStageRuleRepositoryJpa extends JpaRepository<JpaAiAgentStageRuleEntity, UUID> {
    List<JpaAiAgentStageRuleEntity> findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(UUID companyId, String agentId);
    Optional<JpaAiAgentStageRuleEntity> findByCompanyIdAndAgentIdAndStageId(UUID companyId, String agentId, String stageId);
    void deleteAllByCompanyIdAndAgentId(UUID companyId, String agentId);
}
