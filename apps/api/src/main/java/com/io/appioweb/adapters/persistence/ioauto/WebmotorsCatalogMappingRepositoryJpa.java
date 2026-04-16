package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebmotorsCatalogMappingRepositoryJpa extends JpaRepository<JpaWebmotorsCatalogMappingEntity, UUID> {
    Optional<JpaWebmotorsCatalogMappingEntity> findByCompanyIdAndStoreKeyAndMappingTypeAndInternalValueIgnoreCase(
            UUID companyId,
            String storeKey,
            String mappingType,
            String internalValue
    );

    List<JpaWebmotorsCatalogMappingEntity> findAllByCompanyIdAndStoreKeyAndMappingTypeOrderByInternalValueAsc(
            UUID companyId,
            String storeKey,
            String mappingType
    );
}
