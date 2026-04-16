package com.io.appioweb.adapters.persistence.aisupervisors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiSupervisorAgentRuleRepositoryJpa extends JpaRepository<JpaAiSupervisorAgentRuleEntity, UUID> {
    List<JpaAiSupervisorAgentRuleEntity> findAllByCompanyIdAndSupervisorIdOrderByUpdatedAtDesc(UUID companyId, UUID supervisorId);
    List<JpaAiSupervisorAgentRuleEntity> findAllByCompanyIdAndSupervisorIdAndEnabledTrueOrderByUpdatedAtDesc(UUID companyId, UUID supervisorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from JpaAiSupervisorAgentRuleEntity row where row.companyId = :companyId and row.supervisorId = :supervisorId")
    void deleteAllByCompanyIdAndSupervisorIdInBatch(@Param("companyId") UUID companyId, @Param("supervisorId") UUID supervisorId);
}
