package com.io.appioweb.application.ioauto.webmotors;

import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapAuthClient;
import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapCatalogClient;
import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapSessionCache;
import com.io.appioweb.adapters.persistence.ioauto.JpaWebmotorsCatalogMappingEntity;
import com.io.appioweb.adapters.persistence.ioauto.WebmotorsCatalogMappingRepositoryJpa;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCatalogEntry;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCredentialSnapshot;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsSoapAuthResult;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WebmotorsCatalogService {

    private final WebmotorsCatalogMappingRepositoryJpa repository;
    private final WebmotorsCredentialService credentialService;
    private final WebmotorsSoapAuthClient soapAuthClient;
    private final WebmotorsSoapCatalogClient soapCatalogClient;
    private final WebmotorsSoapSessionCache sessionCache;

    public WebmotorsCatalogService(
            WebmotorsCatalogMappingRepositoryJpa repository,
            WebmotorsCredentialService credentialService,
            WebmotorsSoapAuthClient soapAuthClient,
            WebmotorsSoapCatalogClient soapCatalogClient,
            WebmotorsSoapSessionCache sessionCache
    ) {
        this.repository = repository;
        this.credentialService = credentialService;
        this.soapAuthClient = soapAuthClient;
        this.soapCatalogClient = soapCatalogClient;
        this.sessionCache = sessionCache;
    }

    @Transactional(readOnly = true)
    public String resolveCode(UUID companyId, String storeKey, String type, String internalValue) {
        if (internalValue == null || internalValue.trim().isBlank()) {
            return "";
        }
        return repository.findByCompanyIdAndStoreKeyAndMappingTypeAndInternalValueIgnoreCase(companyId, normalize(storeKey), normalize(type), internalValue.trim())
                .map(JpaWebmotorsCatalogMappingEntity::getWebmotorsCode)
                .orElseThrow(() -> new BusinessException("WEBMOTORS_CATALOG_MAPPING_NOT_FOUND", "Mapeamento de catálogo Webmotors não encontrado para " + type + "."));
    }

    @Transactional
    public List<WebmotorsCatalogEntry> refreshCatalog(UUID companyId, String storeKey, String type) {
        WebmotorsCredentialSnapshot credentials = credentialService.getOrCreate(companyId, storeKey);
        if (!credentials.featureFlags().catalogSyncEnabled()) {
            throw new BusinessException("WEBMOTORS_CATALOG_SYNC_DISABLED", "A sincronização de catálogo da Webmotors está desativada para esta loja.");
        }
        String cachedHash = sessionCache.get(companyId, normalize(storeKey));
        String hashAutenticacao = cachedHash;
        if (hashAutenticacao == null || hashAutenticacao.isBlank()) {
            WebmotorsSoapAuthResult auth = soapAuthClient.authenticate(credentials).payload();
            if (!"0".equals(normalize(auth.codigoRetorno())) && !"00".equals(normalize(auth.codigoRetorno()))) {
                throw new BusinessException("WEBMOTORS_SOAP_AUTH_INVALID", "A Webmotors rejeitou a autenticação SOAP do catálogo.");
            }
            hashAutenticacao = auth.hashAutenticacao();
            sessionCache.put(companyId, normalize(storeKey), hashAutenticacao, Instant.now().plusSeconds(20 * 60));
        }
        try {
            List<WebmotorsCatalogEntry> entries = soapCatalogClient.fetchCatalog(credentials, hashAutenticacao, normalize(type)).payload();
            Instant now = Instant.now();
            for (WebmotorsCatalogEntry entry : entries) {
                JpaWebmotorsCatalogMappingEntity entity = repository.findByCompanyIdAndStoreKeyAndMappingTypeAndInternalValueIgnoreCase(
                                companyId,
                                normalize(storeKey),
                                normalize(type),
                                entry.internalValue()
                        )
                        .orElseGet(JpaWebmotorsCatalogMappingEntity::new);
                if (entity.getId() == null) {
                    entity.setId(UUID.randomUUID());
                    entity.setCompanyId(companyId);
                    entity.setStoreKey(normalize(storeKey));
                    entity.setMappingType(normalize(type));
                    entity.setInternalValue(entry.internalValue());
                    entity.setCreatedAt(now);
                }
                entity.setWebmotorsCode(entry.webmotorsCode());
                entity.setWebmotorsLabel(entry.webmotorsLabel());
                entity.setRawPayloadJson(entry.rawPayloadJson());
                entity.setLastRefreshedAt(now);
                entity.setUpdatedAt(now);
                repository.save(entity);
            }
            return entries;
        } catch (BusinessException exception) {
            sessionCache.invalidate(companyId, normalize(storeKey));
            throw exception;
        } catch (Exception exception) {
            sessionCache.invalidate(companyId, normalize(storeKey));
            throw new BusinessException("WEBMOTORS_CATALOG_REFRESH_FAILED", "Não foi possível atualizar o catálogo da Webmotors.");
        }
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? "default" : normalized;
    }
}
