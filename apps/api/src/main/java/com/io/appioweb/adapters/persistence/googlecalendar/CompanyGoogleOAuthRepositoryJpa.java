package com.io.appioweb.adapters.persistence.googlecalendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyGoogleOAuthRepositoryJpa extends JpaRepository<JpaCompanyGoogleOAuthEntity, UUID> {
    Optional<JpaCompanyGoogleOAuthEntity> findByCompanyId(UUID companyId);
}
