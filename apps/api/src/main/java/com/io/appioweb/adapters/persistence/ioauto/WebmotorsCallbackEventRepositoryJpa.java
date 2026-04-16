package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebmotorsCallbackEventRepositoryJpa extends JpaRepository<JpaWebmotorsCallbackEventEntity, UUID> {
    Optional<JpaWebmotorsCallbackEventEntity> findByPayloadHash(String payloadHash);
}
