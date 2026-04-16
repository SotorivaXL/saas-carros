package com.io.appioweb.application.auth.usecase;

import com.io.appioweb.application.auth.port.in.RoleQueryUseCase;
import com.io.appioweb.application.auth.port.out.RoleRepositoryPort;

import java.util.List;

public class ListRolesUseCase implements RoleQueryUseCase {
    private final RoleRepositoryPort roles;

    public ListRolesUseCase(RoleRepositoryPort roles) {
        this.roles = roles;
    }

    @Override
    public List<String> listRoles() {
        return roles.findAllRoleNames();
    }
}
