package com.io.appioweb.adapters.persistence.aiagents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_agent_stage_rules")
public class JpaAiAgentStageRuleEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Column(name = "stage_id", nullable = false, length = 120)
    private String stageId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "only_forward_override")
    private Boolean onlyForwardOverride;

    @Column(name = "allowed_from_stages_json", nullable = false, columnDefinition = "text")
    private String allowedFromStagesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getOnlyForwardOverride() {
        return onlyForwardOverride;
    }

    public void setOnlyForwardOverride(Boolean onlyForwardOverride) {
        this.onlyForwardOverride = onlyForwardOverride;
    }

    public String getAllowedFromStagesJson() {
        return allowedFromStagesJson;
    }

    public void setAllowedFromStagesJson(String allowedFromStagesJson) {
        this.allowedFromStagesJson = allowedFromStagesJson;
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
