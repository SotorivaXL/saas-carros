package com.io.appioweb.adapters.persistence.aisupervisors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_supervisor_company_config")
public class JpaAiSupervisorCompanyConfigEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "default_supervisor_id")
    private UUID defaultSupervisorId;

    @Column(name = "supervisor_enabled", nullable = false)
    private boolean supervisorEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getDefaultSupervisorId() {
        return defaultSupervisorId;
    }

    public void setDefaultSupervisorId(UUID defaultSupervisorId) {
        this.defaultSupervisorId = defaultSupervisorId;
    }

    public boolean isSupervisorEnabled() {
        return supervisorEnabled;
    }

    public void setSupervisorEnabled(boolean supervisorEnabled) {
        this.supervisorEnabled = supervisorEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
