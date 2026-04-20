package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IoAutoPublicLeadEventRepositoryJpa extends JpaRepository<JpaIoAutoPublicLeadEventEntity, UUID> {
    List<JpaIoAutoPublicLeadEventEntity> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
