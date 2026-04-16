package com.io.appioweb.adapters.persistence.ioauto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webmotors_credentials")
public class JpaWebmotorsCredentialEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "store_key", nullable = false, length = 80)
    private String storeKey;

    @Column(name = "store_name", nullable = false, length = 160)
    private String storeName;

    @Column(name = "soap_ads_enabled", nullable = false)
    private boolean soapAdsEnabled;

    @Column(name = "rest_leads_enabled", nullable = false)
    private boolean restLeadsEnabled;

    @Column(name = "catalog_sync_enabled", nullable = false)
    private boolean catalogSyncEnabled;

    @Column(name = "lead_pull_enabled", nullable = false)
    private boolean leadPullEnabled;

    @Column(name = "callback_enabled", nullable = false)
    private boolean callbackEnabled;

    @Column(name = "soap_base_url", columnDefinition = "text")
    private String soapBaseUrl;

    @Column(name = "soap_auth_path", columnDefinition = "text")
    private String soapAuthPath;

    @Column(name = "soap_inventory_path", columnDefinition = "text")
    private String soapInventoryPath;

    @Column(name = "soap_catalog_path", columnDefinition = "text")
    private String soapCatalogPath;

    @Column(name = "soap_cnpj_encrypted", columnDefinition = "text")
    private String soapCnpjEncrypted;

    @Column(name = "soap_email_encrypted", columnDefinition = "text")
    private String soapEmailEncrypted;

    @Column(name = "soap_password_encrypted", columnDefinition = "text")
    private String soapPasswordEncrypted;

    @Column(name = "rest_token_url", columnDefinition = "text")
    private String restTokenUrl;

    @Column(name = "rest_api_base_url", columnDefinition = "text")
    private String restApiBaseUrl;

    @Column(name = "rest_username_encrypted", columnDefinition = "text")
    private String restUsernameEncrypted;

    @Column(name = "rest_password_encrypted", columnDefinition = "text")
    private String restPasswordEncrypted;

    @Column(name = "rest_client_id_encrypted", columnDefinition = "text")
    private String restClientIdEncrypted;

    @Column(name = "rest_client_secret_encrypted", columnDefinition = "text")
    private String restClientSecretEncrypted;

    @Column(name = "callback_secret_encrypted", columnDefinition = "text")
    private String callbackSecretEncrypted;

    @Column(name = "last_soap_sync_at")
    private Instant lastSoapSyncAt;

    @Column(name = "last_lead_pull_at")
    private Instant lastLeadPullAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

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
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public boolean isSoapAdsEnabled() { return soapAdsEnabled; }
    public void setSoapAdsEnabled(boolean soapAdsEnabled) { this.soapAdsEnabled = soapAdsEnabled; }
    public boolean isRestLeadsEnabled() { return restLeadsEnabled; }
    public void setRestLeadsEnabled(boolean restLeadsEnabled) { this.restLeadsEnabled = restLeadsEnabled; }
    public boolean isCatalogSyncEnabled() { return catalogSyncEnabled; }
    public void setCatalogSyncEnabled(boolean catalogSyncEnabled) { this.catalogSyncEnabled = catalogSyncEnabled; }
    public boolean isLeadPullEnabled() { return leadPullEnabled; }
    public void setLeadPullEnabled(boolean leadPullEnabled) { this.leadPullEnabled = leadPullEnabled; }
    public boolean isCallbackEnabled() { return callbackEnabled; }
    public void setCallbackEnabled(boolean callbackEnabled) { this.callbackEnabled = callbackEnabled; }
    public String getSoapBaseUrl() { return soapBaseUrl; }
    public void setSoapBaseUrl(String soapBaseUrl) { this.soapBaseUrl = soapBaseUrl; }
    public String getSoapAuthPath() { return soapAuthPath; }
    public void setSoapAuthPath(String soapAuthPath) { this.soapAuthPath = soapAuthPath; }
    public String getSoapInventoryPath() { return soapInventoryPath; }
    public void setSoapInventoryPath(String soapInventoryPath) { this.soapInventoryPath = soapInventoryPath; }
    public String getSoapCatalogPath() { return soapCatalogPath; }
    public void setSoapCatalogPath(String soapCatalogPath) { this.soapCatalogPath = soapCatalogPath; }
    public String getSoapCnpjEncrypted() { return soapCnpjEncrypted; }
    public void setSoapCnpjEncrypted(String soapCnpjEncrypted) { this.soapCnpjEncrypted = soapCnpjEncrypted; }
    public String getSoapEmailEncrypted() { return soapEmailEncrypted; }
    public void setSoapEmailEncrypted(String soapEmailEncrypted) { this.soapEmailEncrypted = soapEmailEncrypted; }
    public String getSoapPasswordEncrypted() { return soapPasswordEncrypted; }
    public void setSoapPasswordEncrypted(String soapPasswordEncrypted) { this.soapPasswordEncrypted = soapPasswordEncrypted; }
    public String getRestTokenUrl() { return restTokenUrl; }
    public void setRestTokenUrl(String restTokenUrl) { this.restTokenUrl = restTokenUrl; }
    public String getRestApiBaseUrl() { return restApiBaseUrl; }
    public void setRestApiBaseUrl(String restApiBaseUrl) { this.restApiBaseUrl = restApiBaseUrl; }
    public String getRestUsernameEncrypted() { return restUsernameEncrypted; }
    public void setRestUsernameEncrypted(String restUsernameEncrypted) { this.restUsernameEncrypted = restUsernameEncrypted; }
    public String getRestPasswordEncrypted() { return restPasswordEncrypted; }
    public void setRestPasswordEncrypted(String restPasswordEncrypted) { this.restPasswordEncrypted = restPasswordEncrypted; }
    public String getRestClientIdEncrypted() { return restClientIdEncrypted; }
    public void setRestClientIdEncrypted(String restClientIdEncrypted) { this.restClientIdEncrypted = restClientIdEncrypted; }
    public String getRestClientSecretEncrypted() { return restClientSecretEncrypted; }
    public void setRestClientSecretEncrypted(String restClientSecretEncrypted) { this.restClientSecretEncrypted = restClientSecretEncrypted; }
    public String getCallbackSecretEncrypted() { return callbackSecretEncrypted; }
    public void setCallbackSecretEncrypted(String callbackSecretEncrypted) { this.callbackSecretEncrypted = callbackSecretEncrypted; }
    public Instant getLastSoapSyncAt() { return lastSoapSyncAt; }
    public void setLastSoapSyncAt(Instant lastSoapSyncAt) { this.lastSoapSyncAt = lastSoapSyncAt; }
    public Instant getLastLeadPullAt() { return lastLeadPullAt; }
    public void setLastLeadPullAt(Instant lastLeadPullAt) { this.lastLeadPullAt = lastLeadPullAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
