package com.io.appioweb.adapters.persistence.auth;

import com.io.appioweb.application.auth.port.out.TeamRepositoryPort;
import com.io.appioweb.domain.auth.entity.Team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TeamRepositoryAdapter implements TeamRepositoryPort {
    private final TeamRepositoryJpa jpa;

    public TeamRepositoryAdapter(TeamRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Team> findByIdAndCompanyId(UUID teamId, UUID companyId) {
        return jpa.findByIdAndCompanyId(teamId, companyId).map(this::toDomain);
    }

    @Override
    public List<Team> findAllByCompanyId(UUID companyId) {
        return jpa.findAllByCompanyIdOrderByNameAsc(companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name) {
        return jpa.existsByCompanyIdAndNameIgnoreCase(companyId, name);
    }

    @Override
    public void save(Team team) {
        JpaTeamEntity entity = new JpaTeamEntity();
        entity.setId(team.id());
        entity.setCompanyId(team.companyId());
        entity.setName(team.name());
        entity.setCreatedAt(team.createdAt());
        entity.setUpdatedAt(team.updatedAt());
        jpa.save(entity);
    }

    @Override
    public void deleteById(UUID teamId) {
        jpa.deleteById(teamId);
    }

    private Team toDomain(JpaTeamEntity entity) {
        return new Team(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
