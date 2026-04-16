package com.io.appioweb.adapters.persistence.auth;

import com.io.appioweb.application.auth.port.out.RoleRepositoryPort;

import java.util.List;

public class RoleRepositoryAdapter implements RoleRepositoryPort {
    private final RoleRepositoryJpa jpa;

    public RoleRepositoryAdapter(RoleRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<String> findAllRoleNames() {
        return jpa.findAll().stream().map(JpaRoleEntity::getName).toList();
    }

    @Override
    public boolean existsByName(String roleName) {
        return jpa.existsByName(roleName);
    }
}
