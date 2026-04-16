package com.io.appioweb.adapters.persistence.aiagents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_agent_company_state")
public class JpaAiAgentCompanyStateEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "providers_json", nullable = false, columnDefinition = "text")
    private String providersJson;

    @Column(name = "agents_json", nullable = false, columnDefinition = "text")
    private String agentsJson;

    @Column(name = "knowledge_base_json", nullable = false, columnDefinition = "text")
    private String knowledgeBaseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public String getProvidersJson() { return providersJson; }
    public void setProvidersJson(String providersJson) { this.providersJson = providersJson; }

    public String getAgentsJson() { return agentsJson; }
    public void setAgentsJson(String agentsJson) { this.agentsJson = agentsJson; }

    public String getKnowledgeBaseJson() { return knowledgeBaseJson; }
    public void setKnowledgeBaseJson(String knowledgeBaseJson) { this.knowledgeBaseJson = knowledgeBaseJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
