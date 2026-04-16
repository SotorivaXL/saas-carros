package com.io.appioweb.adapters.persistence.ioauto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webmotors_catalog_mappings")
public class JpaWebmotorsCatalogMappingEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "store_key", nullable = false, length = 80)
    private String storeKey;

    @Column(name = "mapping_type", nullable = false, length = 60)
    private String mappingType;

    @Column(name = "internal_value", nullable = false, length = 180)
    private String internalValue;

    @Column(name = "webmotors_code", nullable = false, length = 80)
    private String webmotorsCode;

    @Column(name = "webmotors_label", length = 180)
    private String webmotorsLabel;

    @Column(name = "raw_payload_json", nullable = false, columnDefinition = "text")
    private String rawPayloadJson = "{}";

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getStoreKey() { return storeKey; }
    public void setStoreKey(String storeKey) { this.storeKey = storeKey; }
    public String getMappingType() { return mappingType; }
    public void setMappingType(String mappingType) { this.mappingType = mappingType; }
    public String getInternalValue() { return internalValue; }
    public void setInternalValue(String internalValue) { this.internalValue = internalValue; }
    public String getWebmotorsCode() { return webmotorsCode; }
    public void setWebmotorsCode(String webmotorsCode) { this.webmotorsCode = webmotorsCode; }
    public String getWebmotorsLabel() { return webmotorsLabel; }
    public void setWebmotorsLabel(String webmotorsLabel) { this.webmotorsLabel = webmotorsLabel; }
    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(Instant lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
