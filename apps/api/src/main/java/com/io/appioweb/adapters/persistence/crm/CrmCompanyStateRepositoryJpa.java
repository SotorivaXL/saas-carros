package com.io.appioweb.adapters.persistence.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CrmCompanyStateRepositoryJpa extends JpaRepository<JpaCrmCompanyStateEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from JpaCrmCompanyStateEntity c where c.companyId = :companyId")
    Optional<JpaCrmCompanyStateEntity> findByCompanyIdForUpdate(@Param("companyId") UUID companyId);
}
