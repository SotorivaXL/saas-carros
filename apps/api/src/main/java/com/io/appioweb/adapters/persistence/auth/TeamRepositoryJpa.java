package com.io.appioweb.adapters.persistence.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepositoryJpa extends JpaRepository<JpaTeamEntity, UUID> {
    Optional<JpaTeamEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    List<JpaTeamEntity> findAllByCompanyIdOrderByNameAsc(UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
