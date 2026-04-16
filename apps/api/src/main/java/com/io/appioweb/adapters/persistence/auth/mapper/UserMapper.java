package com.io.appioweb.adapters.persistence.auth.mapper;

import com.io.appioweb.adapters.persistence.auth.JpaRoleEntity;
import com.io.appioweb.adapters.persistence.auth.JpaUserEntity;
import com.io.appioweb.domain.auth.entity.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {
    public static User toDomain(JpaUserEntity e) {
        Set<String> roles = e.getRoles().stream().map(JpaRoleEntity::getName).collect(Collectors.toSet());
        Set<String> modules = e.getModulePermissions() == null || e.getModulePermissions().isBlank()
                ? Collections.emptySet()
                : Arrays.stream(e.getModulePermissions().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return new User(
                e.getId(),
                e.getCompanyId(),
                e.getEmail(),
                e.getPasswordHash(),
                e.getFullName(),
                e.getProfileImageUrl(),
                e.getJobTitle(),
                e.getBirthDate(),
                e.getPermissionPreset(),
                modules,
                e.getTeamId(),
                e.isActive(),
                e.getCreatedAt(),
                roles
        );
    }
}
