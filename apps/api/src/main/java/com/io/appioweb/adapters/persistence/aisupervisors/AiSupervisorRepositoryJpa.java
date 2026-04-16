package com.io.appioweb.adapters.persistence.aisupervisors;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSupervisorRepositoryJpa extends JpaRepository<JpaAiSupervisorEntity, UUID> {
    Optional<JpaAiSupervisorEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    List<JpaAiSupervisorEntity> findAllByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
    List<JpaAiSupervisorEntity> findAllByCompanyIdAndEnabledTrueOrderByUpdatedAtDesc(UUID companyId);
}
