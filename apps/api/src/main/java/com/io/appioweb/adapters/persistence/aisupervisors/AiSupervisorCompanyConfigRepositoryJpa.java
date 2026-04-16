package com.io.appioweb.adapters.persistence.aisupervisors;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiSupervisorCompanyConfigRepositoryJpa extends JpaRepository<JpaAiSupervisorCompanyConfigEntity, UUID> {
}
