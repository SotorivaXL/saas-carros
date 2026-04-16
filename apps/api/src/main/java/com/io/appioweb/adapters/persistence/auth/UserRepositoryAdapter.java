package com.io.appioweb.adapters.persistence.auth;

import com.io.appioweb.adapters.persistence.auth.mapper.UserMapper;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import com.io.appioweb.domain.auth.entity.User;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

public class UserRepositoryAdapter implements UserRepositoryPort {
    private final UserRepositoryJpa jpa;
    private final RoleRepositoryJpa roleJpa;

    public UserRepositoryAdapter(UserRepositoryJpa jpa, RoleRepositoryJpa roleJpa) {
        this.jpa = jpa;
        this.roleJpa = roleJpa;
    }

    @Override
    public Optional<User> findByCompanyIdAndEmail(UUID companyId, String email) {
        return jpa.findByCompanyIdAndEmail(companyId, email.toLowerCase()).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmailGlobal(String email) {
        var list = jpa.findAllByEmail(email.toLowerCase());
        if (list.isEmpty()) return Optional.empty();

        // mínimo: pega o mais antigo (ou o primeiro). Você pode evoluir pra selecionar empresa na tela.
        list.sort(Comparator.comparing(JpaUserEntity::getCreatedAt));
        return Optional.of(UserMapper.toDomain(list.get(0)));
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return jpa.findById(userId).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByIdAndCompanyId(UUID userId, UUID companyId) {
        return jpa.findByIdAndCompanyId(userId, companyId).map(UserMapper::toDomain);
    }

    @Override
    public List<User> findAllByCompanyId(UUID companyId) {
        return jpa.findAllByCompanyId(companyId).stream().map(UserMapper::toDomain).toList();
    }

    @Override
    public long countByCompanyIdAndTeamId(UUID companyId, UUID teamId) {
        return jpa.countByCompanyIdAndTeamId(companyId, teamId);
    }

    @Override
    public void deleteById(UUID userId) {
        jpa.deleteById(userId);
    }

    @Override
    public void deleteByCompanyId(UUID companyId) {
        jpa.deleteByCompanyId(companyId);
    }

    @Override
    @Transactional
    public void save(User user) {
        JpaUserEntity e = new JpaUserEntity();
        e.setId(user.id());
        e.setCompanyId(user.companyId());
        e.setEmail(user.email().toLowerCase());
        e.setPasswordHash(user.passwordHash());
        e.setFullName(user.fullName());
        e.setProfileImageUrl(user.profileImageUrl());
        e.setJobTitle(user.jobTitle());
        e.setBirthDate(user.birthDate());
        e.setPermissionPreset(user.permissionPreset());
        e.setModulePermissions(user.modulePermissions() == null ? null : user.modulePermissions().stream().sorted().collect(Collectors.joining(",")));
        e.setTeamId(user.teamId());
        e.setActive(user.isActive());
        e.setCreatedAt(user.createdAt());

        for (String r : user.roles()) {
            JpaRoleEntity role = roleJpa.findByName(r)
                    .orElseThrow(() -> new BusinessException("ROLE_INVALID", "Role inválida: " + r));
            e.getRoles().add(role);
        }

        jpa.save(e);
    }
}
