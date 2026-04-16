package com.io.appioweb.adapters.persistence.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepositoryJpa extends JpaRepository<JpaRoleEntity, UUID> {
    Optional<JpaRoleEntity> findByName(String name);
    boolean existsByName(String name);
}
