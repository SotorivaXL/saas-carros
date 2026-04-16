package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IoAutoVehiclePublicationRepositoryJpa extends JpaRepository<JpaIoAutoVehiclePublicationEntity, UUID> {
    List<JpaIoAutoVehiclePublicationEntity> findAllByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
    List<JpaIoAutoVehiclePublicationEntity> findAllByCompanyIdAndVehicleId(UUID companyId, UUID vehicleId);
    List<JpaIoAutoVehiclePublicationEntity> findAllByCompanyIdAndVehicleIdIn(UUID companyId, List<UUID> vehicleIds);
    Optional<JpaIoAutoVehiclePublicationEntity> findByCompanyIdAndVehicleIdAndProviderKey(UUID companyId, UUID vehicleId, String providerKey);
    Optional<JpaIoAutoVehiclePublicationEntity> findByCompanyIdAndProviderKeyAndProviderListingId(UUID companyId, String providerKey, String providerListingId);
}
