package com.io.appioweb.adapters.persistence.aiagents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiAgentCompanyStateRepositoryJpa extends JpaRepository<JpaAiAgentCompanyStateEntity, UUID> {
}
