package com.io.appioweb.adapters.persistence.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crm_company_state")
public class JpaCrmCompanyStateEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "stages_json", nullable = false, columnDefinition = "text")
    private String stagesJson;

    @Column(name = "lead_stage_map_json", nullable = false, columnDefinition = "text")
    private String leadStageMapJson;

    @Column(name = "custom_fields_json", nullable = false, columnDefinition = "text")
    private String customFieldsJson;

    @Column(name = "lead_field_values_json", nullable = false, columnDefinition = "text")
    private String leadFieldValuesJson;

    @Column(name = "lead_fields_order_json", nullable = false, columnDefinition = "text")
    private String leadFieldsOrderJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public String getStagesJson() { return stagesJson; }
    public void setStagesJson(String stagesJson) { this.stagesJson = stagesJson; }

    public String getLeadStageMapJson() { return leadStageMapJson; }
    public void setLeadStageMapJson(String leadStageMapJson) { this.leadStageMapJson = leadStageMapJson; }

    public String getCustomFieldsJson() { return customFieldsJson; }
    public void setCustomFieldsJson(String customFieldsJson) { this.customFieldsJson = customFieldsJson; }

    public String getLeadFieldValuesJson() { return leadFieldValuesJson; }
    public void setLeadFieldValuesJson(String leadFieldValuesJson) { this.leadFieldValuesJson = leadFieldValuesJson; }

    public String getLeadFieldsOrderJson() { return leadFieldsOrderJson; }
    public void setLeadFieldsOrderJson(String leadFieldsOrderJson) { this.leadFieldsOrderJson = leadFieldsOrderJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
