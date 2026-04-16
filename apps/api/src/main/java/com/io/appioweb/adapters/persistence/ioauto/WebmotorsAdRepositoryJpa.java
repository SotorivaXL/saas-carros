package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebmotorsAdRepositoryJpa extends JpaRepository<JpaWebmotorsAdEntity, UUID> {
    Optional<JpaWebmotorsAdEntity> findByCompanyIdAndVehicleId(UUID companyId, UUID vehicleId);
    Optional<JpaWebmotorsAdEntity> findByCompanyIdAndRemoteAdCode(UUID companyId, String remoteAdCode);
    List<JpaWebmotorsAdEntity> findAllByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
}
