package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IoAutoVehicleRepositoryJpa extends JpaRepository<JpaIoAutoVehicleEntity, UUID> {
    List<JpaIoAutoVehicleEntity> findAllByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
    Optional<JpaIoAutoVehicleEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
