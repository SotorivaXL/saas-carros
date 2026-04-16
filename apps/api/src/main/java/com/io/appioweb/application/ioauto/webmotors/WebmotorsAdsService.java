package com.io.appioweb.application.ioauto.webmotors;

import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapAuthClient;
import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapInventoryClient;
import com.io.appioweb.adapters.integrations.webmotors.soap.WebmotorsSoapSessionCache;
import com.io.appioweb.adapters.persistence.ioauto.*;
import com.io.appioweb.domain.ioauto.webmotors.*;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WebmotorsAdsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROVIDER_KEY = "webmotors";

    private final WebmotorsCredentialService credentialService;
    private final WebmotorsCatalogService catalogService;
    private final WebmotorsAdRepositoryJpa adRepository;
    private final WebmotorsSyncJobRepositoryJpa jobRepository;
    private final WebmotorsSyncLogRepositoryJpa logRepository;
    private final IoAutoVehicleRepositoryJpa vehicleRepository;
    private final IoAutoVehiclePublicationRepositoryJpa publicationRepository;
    private final WebmotorsSoapAuthClient soapAuthClient;
    private final WebmotorsSoapInventoryClient soapInventoryClient;
    private final WebmotorsSoapSessionCache sessionCache;

    public WebmotorsAdsService(
            WebmotorsCredentialService credentialService,
            WebmotorsCatalogService catalogService,
            WebmotorsAdRepositoryJpa adRepository,
            WebmotorsSyncJobRepositoryJpa jobRepository,
            WebmotorsSyncLogRepositoryJpa logRepository,
            IoAutoVehicleRepositoryJpa vehicleRepository,
            IoAutoVehiclePublicationRepositoryJpa publicationRepository,
            WebmotorsSoapAuthClient soapAuthClient,
            WebmotorsSoapInventoryClient soapInventoryClient,
            WebmotorsSoapSessionCache sessionCache
    ) {
        this.credentialService = credentialService;
        this.catalogService = catalogService;
        this.adRepository = adRepository;
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.vehicleRepository = vehicleRepository;
        this.publicationRepository = publicationRepository;
        this.soapAuthClient = soapAuthClient;
        this.soapInventoryClient = soapInventoryClient;
        this.sessionCache = sessionCache;
    }

    @Transactional(readOnly = true)
    public List<JpaWebmotorsAdEntity> listAds(UUID companyId) {
        return adRepository.findAllByCompanyIdOrderByUpdatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public JpaWebmotorsAdEntity getAd(UUID companyId, UUID vehicleId) {
        return adRepository.findByCompanyIdAndVehicleId(companyId, vehicleId)
                .orElseThrow(() -> new BusinessException("WEBMOTORS_AD_NOT_FOUND", "Anúncio Webmotors não encontrado para este veículo."));
    }

    @Transactional
    public JpaWebmotorsSyncJobEntity enqueuePublish(UUID companyId, UUID vehicleId, String storeKey) {
        JpaIoAutoVehicleEntity vehicle = requireVehicle(companyId, vehicleId);
        JpaWebmotorsAdEntity existingAd = adRepository.findByCompanyIdAndVehicleId(companyId, vehicleId).orElse(null);
        WebmotorsJobType jobType = existingAd != null && safe(existingAd.getRemoteAdCode()).isBlank() == false
                ? WebmotorsJobType.UPDATE_AD
                : WebmotorsJobType.PUBLISH_AD;
        return enqueueVehicleJob(companyId, normalizeStoreKey(storeKey), vehicle, existingAd, jobType);
    }

    @Transactional
    public JpaWebmotorsSyncJobEntity enqueueDelete(UUID companyId, UUID vehicleId, String storeKey) {
        JpaIoAutoVehicleEntity vehicle = requireVehicle(companyId, vehicleId);
        JpaWebmotorsAdEntity existingAd = adRepository.findByCompanyIdAndVehicleId(companyId, vehicleId).orElse(null);
        return enqueueVehicleJob(companyId, normalizeStoreKey(storeKey), vehicle, existingAd, WebmotorsJobType.DELETE_AD);
    }

    @Transactional
    public int reconcileRemoteInventory(UUID companyId, String storeKey, int pageSize) {
        WebmotorsCredentialSnapshot credentials = credentialService.getOrCreate(companyId, normalizeStoreKey(storeKey));
        assertSoapAdsEnabled(credentials);
        String hash = resolveSoapHash(credentials);
        int page = 1;
        int processed = 0;
        while (true) {
            WebmotorsTransportResult<WebmotorsInventoryPage> transport = soapInventoryClient.listCurrentInventoryPage(credentials, hash, page, Math.max(1, pageSize));
            logSoap(companyId, null, "ObterEstoqueAtualPaginado", transport.statusCode(), transport.payload().codigoRetorno(), transport.payload().requestId(), transport.sanitizedRequest(), transport.sanitizedResponse());
            ensureReturnCodeOk(transport.payload().codigoRetorno(), "listagem paginada do estoque");
            for (WebmotorsInventoryItem item : transport.payload().anuncios()) {
                upsertReplicaFromRemote(companyId, normalizeStoreKey(storeKey), item);
                processed++;
            }
            if (transport.payload().anuncios().isEmpty() || transport.payload().pagina() * Math.max(1, transport.payload().anunciosPorPagina()) >= Math.max(1, transport.payload().totalAnuncios())) {
                break;
            }
            page++;
        }
        credentialService.markSoapSync(companyId, normalizeStoreKey(storeKey), Instant.now(), null);
        return processed;
    }

    @Transactional
    public List<WebmotorsCatalogEntry> refreshCatalog(UUID companyId, String storeKey, String type) {
        return catalogService.refreshCatalog(companyId, normalizeStoreKey(storeKey), type);
    }

    @Transactional
    public List<JpaWebmotorsSyncJobEntity> processPendingJobs() {
        List<JpaWebmotorsSyncJobEntity> jobs = jobRepository.findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                List.of(WebmotorsJobStatus.PENDING.name()),
                Instant.now()
        );
        for (JpaWebmotorsSyncJobEntity job : jobs) {
            processSingleJob(job);
        }
        return jobs;
    }

    private JpaWebmotorsSyncJobEntity enqueueVehicleJob(
            UUID companyId,
            String storeKey,
            JpaIoAutoVehicleEntity vehicle,
            JpaWebmotorsAdEntity existingAd,
            WebmotorsJobType jobType
    ) {
        WebmotorsCredentialSnapshot credentials = credentialService.getOrCreate(companyId, storeKey);
        assertSoapAdsEnabled(credentials);
        String idempotencyKey = buildIdempotencyKey(jobType, vehicle, existingAd);
        JpaWebmotorsSyncJobEntity existingJob = jobRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey).orElse(null);
        if (existingJob != null) {
            return existingJob;
        }
        Instant now = Instant.now();
        JpaWebmotorsSyncJobEntity job = new JpaWebmotorsSyncJobEntity();
        job.setId(UUID.randomUUID());
        job.setCompanyId(companyId);
        job.setStoreKey(storeKey);
        job.setJobType(jobType.name());
        job.setAggregateId(vehicle.getId());
        job.setIdempotencyKey(idempotencyKey);
        job.setPayloadJson(writeJson(Map.of("vehicleId", vehicle.getId().toString(), "storeKey", storeKey)));
        job.setStatus(WebmotorsJobStatus.PENDING.name());
        job.setAttempts(0);
        job.setNextRetryAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobRepository.save(job);
        upsertPublication(companyId, vehicle.getId(), "SYNC_QUEUED", null, null, null);
        return job;
    }

    private void processSingleJob(JpaWebmotorsSyncJobEntity job) {
        try {
            job.setStatus(WebmotorsJobStatus.PROCESSING.name());
            job.setAttempts(job.getAttempts() + 1);
            job.setStartedAt(Instant.now());
            job.setLockedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);

            Map<String, String> payload = OBJECT_MAPPER.readValue(job.getPayloadJson(), new TypeReference<Map<String, String>>() {});
            UUID vehicleId = UUID.fromString(payload.get("vehicleId"));
            WebmotorsJobType jobType = WebmotorsJobType.valueOf(job.getJobType());
            switch (jobType) {
                case PUBLISH_AD -> executePublish(job, vehicleId, false);
                case UPDATE_AD -> executePublish(job, vehicleId, true);
                case DELETE_AD -> executeDelete(job, vehicleId);
                case SYNC_ADS -> reconcileRemoteInventory(job.getCompanyId(), job.getStoreKey(), 50);
            }
            job.setStatus(WebmotorsJobStatus.COMPLETED.name());
            job.setFinishedAt(Instant.now());
            job.setLastError(null);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        } catch (BusinessException exception) {
            failJob(job, exception.code(), exception.getMessage());
        } catch (Exception exception) {
            failJob(job, "WEBMOTORS_JOB_FAILED", "Não foi possível processar o job da Webmotors.");
        }
    }

    private void executePublish(JpaWebmotorsSyncJobEntity job, UUID vehicleId, boolean update) {
        UUID companyId = job.getCompanyId();
        String storeKey = normalizeStoreKey(job.getStoreKey());
        JpaIoAutoVehicleEntity vehicle = requireVehicle(companyId, vehicleId);
        JpaWebmotorsAdEntity existingAd = adRepository.findByCompanyIdAndVehicleId(companyId, vehicleId).orElse(null);
        WebmotorsCredentialSnapshot credentials = credentialService.getOrCreate(companyId, storeKey);
        String hash = resolveSoapHash(credentials);
        Map<String, String> requestPayload = buildAdPayload(companyId, storeKey, vehicle, update, existingAd);
        upsertPublication(companyId, vehicleId, "SYNC_IN_PROGRESS", null, null, null);
        WebmotorsTransportResult<WebmotorsSoapOperationResult> transport = update
                ? soapInventoryClient.updateAd(credentials, hash, requestPayload)
                : soapInventoryClient.publishAd(credentials, hash, requestPayload);
        logSoap(companyId, job.getId(), update ? "AlterarAnuncio" : "IncluirAnuncio", transport.statusCode(), transport.payload().codigoRetorno(), transport.payload().requestId(), transport.sanitizedRequest(), transport.sanitizedResponse());
        ensureReturnCodeOk(transport.payload().codigoRetorno(), update ? "edição do anúncio" : "publicação do anúncio");
        Instant now = Instant.now();
        JpaWebmotorsAdEntity ad = existingAd == null ? new JpaWebmotorsAdEntity() : existingAd;
        if (ad.getId() == null) {
            ad.setId(UUID.randomUUID());
            ad.setCompanyId(companyId);
            ad.setStoreKey(storeKey);
            ad.setVehicleId(vehicleId);
            ad.setCreatedAt(now);
        }
        JpaIoAutoVehiclePublicationEntity publication = upsertPublication(companyId, vehicleId, "PUBLISHED", transport.payload().remoteAdCode(), null, now);
        ad.setPublicationId(publication.getId());
        ad.setRemoteAdCode(firstNonBlank(transport.payload().remoteAdCode(), ad.getRemoteAdCode()));
        ad.setRemoteStatus(firstNonBlank(transport.payload().remoteStatus(), update ? "UPDATED" : "PUBLISHED"));
        ad.setTitle(vehicle.getTitle());
        ad.setBrand(vehicle.getBrand());
        ad.setModel(vehicle.getModel());
        ad.setVersion(vehicle.getVersion());
        ad.setPriceCents(vehicle.getPriceCents());
        ad.setMileage(vehicle.getMileage());
        ad.setCatalogSnapshotJson(writeJson(buildCatalogSnapshot(companyId, storeKey, vehicle)));
        ad.setRemotePayloadJson(transport.payload().rawPayloadJson());
        ad.setLastSoapReturnCode(transport.payload().codigoRetorno());
        ad.setLastSoapRequestId(transport.payload().requestId());
        ad.setLastError(null);
        ad.setLastSyncAt(now);
        ad.setRemoteUpdatedAt(now);
        ad.setUpdatedAt(now);
        if (ad.getPublishedAt() == null) {
            ad.setPublishedAt(now);
        }
        adRepository.save(ad);
        credentialService.markSoapSync(companyId, storeKey, now, null);
    }

    private void executeDelete(JpaWebmotorsSyncJobEntity job, UUID vehicleId) {
        UUID companyId = job.getCompanyId();
        String storeKey = normalizeStoreKey(job.getStoreKey());
        JpaWebmotorsAdEntity ad = adRepository.findByCompanyIdAndVehicleId(companyId, vehicleId)
                .orElseThrow(() -> new BusinessException("WEBMOTORS_AD_NOT_FOUND", "Não existe anúncio Webmotors vinculado a este veículo."));
        WebmotorsCredentialSnapshot credentials = credentialService.getOrCreate(companyId, storeKey);
        String hash = resolveSoapHash(credentials);
        WebmotorsTransportResult<WebmotorsSoapOperationResult> transport = soapInventoryClient.deleteAd(credentials, hash, ad.getRemoteAdCode());
        logSoap(companyId, job.getId(), "ExcluirAnuncio", transport.statusCode(), transport.payload().codigoRetorno(), transport.payload().requestId(), transport.sanitizedRequest(), transport.sanitizedResponse());
        ensureReturnCodeOk(transport.payload().codigoRetorno(), "exclusão do anúncio");
        Instant now = Instant.now();
        ad.setRemoteStatus("REMOVED");
        ad.setDeletedAt(now);
        ad.setLastSoapReturnCode(transport.payload().codigoRetorno());
        ad.setLastSoapRequestId(transport.payload().requestId());
        ad.setLastSyncAt(now);
        ad.setUpdatedAt(now);
        adRepository.save(ad);
        upsertPublication(companyId, vehicleId, "REMOVED", ad.getRemoteAdCode(), null, now);
        credentialService.markSoapSync(companyId, storeKey, now, null);
    }

    private String resolveSoapHash(WebmotorsCredentialSnapshot credentials) {
        String cached = sessionCache.get(credentials.companyId(), credentials.storeKey());
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        WebmotorsTransportResult<WebmotorsSoapAuthResult> transport = soapAuthClient.authenticate(credentials);
        logSoap(credentials.companyId(), null, "Autenticar", transport.statusCode(), transport.payload().codigoRetorno(), transport.payload().requestId(), transport.sanitizedRequest(), transport.sanitizedResponse());
        ensureReturnCodeOk(transport.payload().codigoRetorno(), "autenticação SOAP");
        if (safe(transport.payload().hashAutenticacao()).isBlank()) {
            throw new BusinessException("WEBMOTORS_SOAP_HASH_MISSING", "A Webmotors não retornou o HashAutenticacao.");
        }
        sessionCache.put(credentials.companyId(), credentials.storeKey(), transport.payload().hashAutenticacao(), Instant.now().plusSeconds(20 * 60));
        return transport.payload().hashAutenticacao();
    }

    private Map<String, String> buildAdPayload(UUID companyId, String storeKey, JpaIoAutoVehicleEntity vehicle, boolean update, JpaWebmotorsAdEntity existingAd) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("pTitulo", safe(vehicle.getTitle()));
        payload.put("pDescricao", safe(vehicle.getDescription()));
        payload.put("pCodigoMarca", catalogService.resolveCode(companyId, storeKey, "brand", vehicle.getBrand()));
        payload.put("pCodigoModelo", catalogService.resolveCode(companyId, storeKey, "model", vehicle.getModel()));
        if (safe(vehicle.getVersion()).isBlank() == false) {
            payload.put("pCodigoVersao", catalogService.resolveCode(companyId, storeKey, "version", vehicle.getVersion()));
        }
        if (safe(vehicle.getFuelType()).isBlank() == false) {
            payload.put("pCodigoCombustivel", catalogService.resolveCode(companyId, storeKey, "fuel", vehicle.getFuelType()));
        }
        if (safe(vehicle.getTransmission()).isBlank() == false) {
            payload.put("pCodigoCambio", catalogService.resolveCode(companyId, storeKey, "transmission", vehicle.getTransmission()));
        }
        if (safe(vehicle.getColor()).isBlank() == false) {
            payload.put("pCodigoCor", catalogService.resolveCode(companyId, storeKey, "color", vehicle.getColor()));
        }
        if (vehicle.getPriceCents() != null) payload.put("pPrecoVenda", String.valueOf(vehicle.getPriceCents()));
        if (vehicle.getMileage() != null) payload.put("pQuilometragem", String.valueOf(vehicle.getMileage()));
        if (vehicle.getModelYear() != null) payload.put("pAnoModelo", String.valueOf(vehicle.getModelYear()));
        if (vehicle.getManufactureYear() != null) payload.put("pAnoFabricacao", String.valueOf(vehicle.getManufactureYear()));
        if (safe(vehicle.getStockNumber()).isBlank() == false) payload.put("pNumeroEstoque", vehicle.getStockNumber());
        if (update && existingAd != null && safe(existingAd.getRemoteAdCode()).isBlank() == false) {
            payload.put("pCodigoAnuncio", existingAd.getRemoteAdCode());
        }
        return payload;
    }

    private Map<String, String> buildCatalogSnapshot(UUID companyId, String storeKey, JpaIoAutoVehicleEntity vehicle) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("brand", catalogService.resolveCode(companyId, storeKey, "brand", vehicle.getBrand()));
        snapshot.put("model", catalogService.resolveCode(companyId, storeKey, "model", vehicle.getModel()));
        if (safe(vehicle.getVersion()).isBlank() == false) {
            snapshot.put("version", catalogService.resolveCode(companyId, storeKey, "version", vehicle.getVersion()));
        }
        return snapshot;
    }

    private void upsertReplicaFromRemote(UUID companyId, String storeKey, WebmotorsInventoryItem item) {
        JpaWebmotorsAdEntity entity = adRepository.findByCompanyIdAndRemoteAdCode(companyId, item.codigoAnuncio())
                .orElseGet(JpaWebmotorsAdEntity::new);
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCompanyId(companyId);
            entity.setStoreKey(storeKey);
            entity.setCreatedAt(now);
        }
        if (entity.getPublicationId() == null) {
            publicationRepository.findByCompanyIdAndProviderKeyAndProviderListingId(companyId, PROVIDER_KEY, item.codigoAnuncio())
                    .ifPresent(publication -> {
                        entity.setPublicationId(publication.getId());
                        entity.setVehicleId(publication.getVehicleId());
                    });
        }
        entity.setRemoteAdCode(item.codigoAnuncio());
        entity.setRemoteStatus(item.status());
        entity.setTitle(item.titulo());
        entity.setPriceCents(item.precoVenda());
        entity.setMileage(item.quilometragem());
        entity.setRemotePayloadJson(item.rawPayloadJson());
        entity.setLastSyncAt(now);
        entity.setRemoteUpdatedAt(now);
        entity.setUpdatedAt(now);
        adRepository.save(entity);
    }

    private JpaIoAutoVehiclePublicationEntity upsertPublication(UUID companyId, UUID vehicleId, String status, String remoteAdCode, String lastError, Instant publishedAt) {
        Instant now = Instant.now();
        JpaIoAutoVehiclePublicationEntity publication = publicationRepository.findByCompanyIdAndVehicleIdAndProviderKey(companyId, vehicleId, PROVIDER_KEY)
                .orElseGet(JpaIoAutoVehiclePublicationEntity::new);
        if (publication.getId() == null) {
            publication.setId(UUID.randomUUID());
            publication.setCompanyId(companyId);
            publication.setVehicleId(vehicleId);
            publication.setProviderKey(PROVIDER_KEY);
            publication.setCreatedAt(now);
        }
        publication.setStatus(status);
        if (safe(remoteAdCode).isBlank() == false) {
            publication.setProviderListingId(remoteAdCode);
        }
        publication.setLastError(nullable(lastError));
        if (publishedAt != null) {
            publication.setPublishedAt(publishedAt);
        }
        publication.setSyncedAt(now);
        publication.setUpdatedAt(now);
        return publicationRepository.save(publication);
    }

    private void failJob(JpaWebmotorsSyncJobEntity job, String code, String message) {
        boolean retry = shouldRetry(code, job.getAttempts());
        job.setStatus(retry ? WebmotorsJobStatus.PENDING.name() : WebmotorsJobStatus.FAILED.name());
        job.setLastError(message);
        job.setNextRetryAt(retry ? Instant.now().plusSeconds((long) Math.min(300, Math.pow(2, job.getAttempts()) * 30)) : Instant.now());
        job.setUpdatedAt(Instant.now());
        if (job.getAggregateId() != null) {
            upsertPublication(job.getCompanyId(), job.getAggregateId(), "ERROR", null, message, null);
        }
        jobRepository.save(job);
        credentialService.markSoapSync(job.getCompanyId(), job.getStoreKey(), Instant.now(), message);
    }

    private void logSoap(UUID companyId, UUID jobId, String operation, int statusCode, String returnCode, String requestId, String requestPayload, String responsePayload) {
        saveLog(companyId, jobId, "SOAP", "REQUEST", operation, null, null, requestId, requestPayload);
        saveLog(companyId, jobId, "SOAP", "RESPONSE", operation, statusCode, returnCode, requestId, responsePayload);
    }

    private void saveLog(UUID companyId, UUID jobId, String channel, String direction, String operation, Integer statusCode, String returnCode, String requestId, String payload) {
        JpaWebmotorsSyncLogEntity log = new JpaWebmotorsSyncLogEntity();
        log.setId(UUID.randomUUID());
        log.setCompanyId(companyId);
        log.setJobId(jobId);
        log.setChannel(channel);
        log.setDirection(direction);
        log.setOperation(operation);
        log.setStatusCode(statusCode);
        log.setReturnCode(nullable(returnCode));
        log.setRequestId(nullable(requestId));
        log.setSanitizedPayload(nullable(payload));
        log.setCreatedAt(Instant.now());
        logRepository.save(log);
    }

    private void ensureReturnCodeOk(String codigoRetorno, String context) {
        String code = safe(codigoRetorno);
        if (code.isBlank() || "0".equals(code) || "00".equals(code)) {
            return;
        }
        throw new BusinessException("WEBMOTORS_SOAP_RETURN_CODE_" + code, "A Webmotors retornou CódigoRetorno " + code + " durante " + context + ".");
    }

    private void assertSoapAdsEnabled(WebmotorsCredentialSnapshot credentials) {
        if (!credentials.featureFlags().soapAdsEnabled()) {
            throw new BusinessException("WEBMOTORS_SOAP_ADS_DISABLED", "A publicação SOAP da Webmotors está desativada para esta loja.");
        }
    }

    private JpaIoAutoVehicleEntity requireVehicle(UUID companyId, UUID vehicleId) {
        return vehicleRepository.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "Veículo não encontrado."));
    }

    private boolean shouldRetry(String code, int attempts) {
        if (attempts >= 5) return false;
        String normalized = safe(code).toUpperCase(Locale.ROOT);
        return !(normalized.contains("MISSING") || normalized.contains("NOT_FOUND") || normalized.contains("DISABLED") || normalized.contains("INVALID"));
    }

    private String buildIdempotencyKey(WebmotorsJobType jobType, JpaIoAutoVehicleEntity vehicle, JpaWebmotorsAdEntity existingAd) {
        return sha256(jobType.name() + ":" + vehicle.getId() + ":" + safe(existingAd == null ? null : existingAd.getRemoteAdCode()) + ":" + safe(vehicle.getUpdatedAt() == null ? null : vehicle.getUpdatedAt().toString()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder buffer = new StringBuilder();
            for (byte item : digest) buffer.append(String.format("%02x", item));
            return buffer.toString();
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_HASH_FAILED", "Não foi possível calcular a assinatura do job da Webmotors.");
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_JSON_SERIALIZATION_FAILED", "Não foi possível serializar os dados da Webmotors.");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (safe(value).isBlank() == false) return value.trim();
        }
        return "";
    }

    private String normalizeStoreKey(String storeKey) {
        String normalized = safe(storeKey).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "default" : normalized;
    }

    private String nullable(String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
