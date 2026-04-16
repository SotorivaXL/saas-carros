package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebmotorsSyncJobRepositoryJpa extends JpaRepository<JpaWebmotorsSyncJobEntity, UUID> {
    Optional<JpaWebmotorsSyncJobEntity> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);

    List<JpaWebmotorsSyncJobEntity> findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextRetryAt
    );
}
