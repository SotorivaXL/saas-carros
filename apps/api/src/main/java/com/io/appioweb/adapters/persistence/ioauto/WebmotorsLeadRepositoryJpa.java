package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebmotorsLeadRepositoryJpa extends JpaRepository<JpaWebmotorsLeadEntity, UUID> {
    Optional<JpaWebmotorsLeadEntity> findByCompanyIdAndDedupeKey(UUID companyId, String dedupeKey);
    List<JpaWebmotorsLeadEntity> findTop100ByCompanyIdOrderByReceivedAtDesc(UUID companyId);
}
