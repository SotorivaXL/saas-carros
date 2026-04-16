package com.io.appioweb.adapters.persistence.ioauto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webmotors_ads")
public class JpaWebmotorsAdEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "store_key", nullable = false, length = 80)
    private String storeKey;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "publication_id")
    private UUID publicationId;

    @Column(name = "remote_ad_code", length = 180)
    private String remoteAdCode;

    @Column(name = "remote_status", nullable = false, length = 60)
    private String remoteStatus;

    @Column(length = 200)
    private String title;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String model;

    @Column(length = 160)
    private String version;

    @Column(name = "price_cents")
    private Long priceCents;

    private Integer mileage;

    @Column(name = "catalog_snapshot_json", nullable = false, columnDefinition = "text")
    private String catalogSnapshotJson = "{}";

    @Column(name = "remote_payload_json", nullable = false, columnDefinition = "text")
    private String remotePayloadJson = "{}";

    @Column(name = "last_soap_return_code", length = 60)
    private String lastSoapReturnCode;

    @Column(name = "last_soap_request_id", length = 120)
    private String lastSoapRequestId;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "remote_updated_at")
    private Instant remoteUpdatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public String getRemoteAdCode() { return remoteAdCode; }
    public void setRemoteAdCode(String remoteAdCode) { this.remoteAdCode = remoteAdCode; }
    public String getRemoteStatus() { return remoteStatus; }
    public void setRemoteStatus(String remoteStatus) { this.remoteStatus = remoteStatus; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Long getPriceCents() { return priceCents; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }
    public Integer getMileage() { return mileage; }
    public void setMileage(Integer mileage) { this.mileage = mileage; }
    public String getCatalogSnapshotJson() { return catalogSnapshotJson; }
    public void setCatalogSnapshotJson(String catalogSnapshotJson) { this.catalogSnapshotJson = catalogSnapshotJson; }
    public String getRemotePayloadJson() { return remotePayloadJson; }
    public void setRemotePayloadJson(String remotePayloadJson) { this.remotePayloadJson = remotePayloadJson; }
    public String getLastSoapReturnCode() { return lastSoapReturnCode; }
    public void setLastSoapReturnCode(String lastSoapReturnCode) { this.lastSoapReturnCode = lastSoapReturnCode; }
    public String getLastSoapRequestId() { return lastSoapRequestId; }
    public void setLastSoapRequestId(String lastSoapRequestId) { this.lastSoapRequestId = lastSoapRequestId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getRemoteUpdatedAt() { return remoteUpdatedAt; }
    public void setRemoteUpdatedAt(Instant remoteUpdatedAt) { this.remoteUpdatedAt = remoteUpdatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
