package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IoAutoPublicLinkRepositoryJpa extends JpaRepository<JpaIoAutoPublicLinkEntity, UUID> {
    List<JpaIoAutoPublicLinkEntity> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<JpaIoAutoPublicLinkEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
