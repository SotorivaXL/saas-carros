package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebmotorsSyncLogRepositoryJpa extends JpaRepository<JpaWebmotorsSyncLogEntity, UUID> {
}
