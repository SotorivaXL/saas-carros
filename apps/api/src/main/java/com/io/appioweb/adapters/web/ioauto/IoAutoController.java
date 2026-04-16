package com.io.appioweb.adapters.web.ioauto;

import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoSessionRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoConversationEntity;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoSessionEntity;
import com.io.appioweb.adapters.persistence.ioauto.IoAutoIntegrationRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.IoAutoVehiclePublicationRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.IoAutoVehicleRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoIntegrationEntity;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoVehicleEntity;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoVehiclePublicationEntity;
import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.shared.errors.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class IoAutoController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CurrentUserPort currentUser;
    private final CompanyRepositoryPort companies;
    private final AtendimentoConversationRepositoryJpa conversations;
    private final AtendimentoSessionRepositoryJpa sessions;
    private final IoAutoVehicleRepositoryJpa vehicles;
    private final IoAutoVehiclePublicationRepositoryJpa publications;
    private final IoAutoIntegrationRepositoryJpa integrations;
    private final IoAutoBillingService billingService;

    public IoAutoController(
            CurrentUserPort currentUser,
            CompanyRepositoryPort companies,
            AtendimentoConversationRepositoryJpa conversations,
            AtendimentoSessionRepositoryJpa sessions,
            IoAutoVehicleRepositoryJpa vehicles,
            IoAutoVehiclePublicationRepositoryJpa publications,
            IoAutoIntegrationRepositoryJpa integrations,
            IoAutoBillingService billingService
    ) {
        this.currentUser = currentUser;
        this.companies = companies;
        this.conversations = conversations;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.publications = publications;
        this.integrations = integrations;
        this.billingService = billingService;
    }

    @GetMapping("/ioauto/dashboard")
    public ResponseEntity<IoAutoDashboardHttpResponse> getDashboard(
            @RequestParam(name = "preset", required = false) String preset,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        UUID companyId = currentUser.companyId();
        String companyName = companies.findById(companyId).map(company -> company.name()).orElse("IOAuto");
        DashboardPeriodSelection periodSelection = resolveDashboardPeriod(preset, from, to);

        List<JpaIoAutoVehicleEntity> companyVehicles = vehicles.findAllByCompanyIdOrderByUpdatedAtDesc(companyId);
        List<JpaIoAutoVehiclePublicationEntity> companyPublications = publications.findAllByCompanyIdOrderByUpdatedAtDesc(companyId);
        List<JpaIoAutoIntegrationEntity> companyIntegrations = integrations.findAllByCompanyIdOrderByDisplayNameAsc(companyId).stream()
                .filter(integration -> isSupportedProvider(integration.getProviderKey()))
                .toList();
        List<JpaAtendimentoConversationEntity> companyConversations = conversations.findAllByCompanyIdOrderByLastMessageAtDescUpdatedAtDesc(companyId).stream()
                .filter(conversation -> isSupportedLeadSource(conversation.getSourcePlatform()))
                .toList();
        BillingSnapshot billing = billingService.getBillingSnapshot(companyId);
        List<JpaAtendimentoSessionEntity> periodLeadSessions = sessions.findAllByCompanyIdAndArrivedAtGreaterThanEqualAndArrivedAtLessThanOrderByArrivedAtAsc(
                companyId,
                periodSelection.fromAt(),
                periodSelection.toExclusiveAt()
        );
        List<JpaAtendimentoSessionEntity> periodSalesSessions = sessions.findAllByCompanyIdAndSaleCompletedIsTrueAndSaleCompletedAtGreaterThanEqualAndSaleCompletedAtLessThanOrderBySaleCompletedAtAsc(
                companyId,
                periodSelection.fromAt(),
                periodSelection.toExclusiveAt()
        );

        long featuredCount = companyVehicles.stream().filter(JpaIoAutoVehicleEntity::isFeatured).count();
        long connectedIntegrations = companyIntegrations.stream()
                .filter(integration -> "CONNECTED".equalsIgnoreCase(integration.getStatus()) || "ACTIVE".equalsIgnoreCase(integration.getStatus()))
                .count();
        long activePublicationCount = companyPublications.stream()
                .filter(publication -> isActivePublicationStatus(publication.getStatus()))
                .count();

        Map<String, Long> sources = new LinkedHashMap<>();
        for (JpaAtendimentoConversationEntity conversation : companyConversations) {
            String key = normalizeSourcePlatform(conversation.getSourcePlatform());
            sources.put(key, sources.getOrDefault(key, 0L) + 1L);
        }

        List<IoAutoDashboardHttpResponse.SourceSummary> sourceSummaries = sources.entrySet().stream()
                .map(entry -> new IoAutoDashboardHttpResponse.SourceSummary(entry.getKey(), sourceLabel(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(IoAutoDashboardHttpResponse.SourceSummary::total).reversed())
                .toList();
        List<IoAutoDashboardHttpResponse.PeriodPoint> leadVsSales = buildLeadVsSalesSeries(periodSelection, periodLeadSessions, periodSalesSessions);
        List<IoAutoDashboardHttpResponse.SellerSalesSummary> salesBySeller = buildSalesBySeller(periodSalesSessions);

        Map<UUID, List<JpaIoAutoVehiclePublicationEntity>> publicationsByVehicle = groupPublicationsByVehicle(companyId, companyVehicles);

        List<IoAutoDashboardHttpResponse.RecentVehicle> recentVehicles = companyVehicles.stream()
                .limit(5)
                .map(vehicle -> new IoAutoDashboardHttpResponse.RecentVehicle(
                        vehicle.getId(),
                        vehicle.getTitle(),
                        vehicle.getPriceCents(),
                        normalizeText(vehicle.getStatus(), "DRAFT"),
                        vehicle.getUpdatedAt(),
                        publicationsByVehicle.getOrDefault(vehicle.getId(), List.of()).size()
                ))
                .toList();

        List<IoAutoDashboardHttpResponse.RecentConversation> recentConversations = companyConversations.stream()
                .limit(6)
                .map(conversation -> new IoAutoDashboardHttpResponse.RecentConversation(
                        conversation.getId(),
                        normalizeText(conversation.getDisplayName(), conversation.getPhone()),
                        normalizeText(conversation.getLastMessageText(), "Sem mensagens recentes."),
                        conversation.getLastMessageAt(),
                        normalizeSourcePlatform(conversation.getSourcePlatform())
                ))
                .toList();

        return ResponseEntity.ok(new IoAutoDashboardHttpResponse(
                companyName,
                companyVehicles.size(),
                featuredCount,
                activePublicationCount,
                companyConversations.size(),
                connectedIntegrations,
                billing,
                new IoAutoDashboardHttpResponse.PeriodFilter(
                        periodSelection.preset(),
                        DATE_FORMATTER.format(periodSelection.fromDate()),
                        DATE_FORMATTER.format(periodSelection.toDate())
                ),
                leadVsSales,
                salesBySeller,
                sourceSummaries,
                recentVehicles,
                recentConversations
        ));
    }

    @GetMapping("/ioauto/vehicles")
    public ResponseEntity<List<IoAutoVehicleHttpResponse>> listVehicles() {
        UUID companyId = currentUser.companyId();
        List<JpaIoAutoVehicleEntity> companyVehicles = vehicles.findAllByCompanyIdOrderByUpdatedAtDesc(companyId);
        Map<UUID, List<JpaIoAutoVehiclePublicationEntity>> publicationsByVehicle = groupPublicationsByVehicle(companyId, companyVehicles);
        Map<String, JpaIoAutoIntegrationEntity> integrationsByKey = integrations.findAllByCompanyIdOrderByDisplayNameAsc(companyId).stream()
                .filter(integration -> isSupportedProvider(integration.getProviderKey()))
                .collect(java.util.stream.Collectors.toMap(JpaIoAutoIntegrationEntity::getProviderKey, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<IoAutoVehicleHttpResponse> response = companyVehicles.stream()
                .map(vehicle -> toVehicleResponse(vehicle, publicationsByVehicle.getOrDefault(vehicle.getId(), List.of()), integrationsByKey))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ioauto/vehicles")
    @Transactional
    public ResponseEntity<IoAutoVehicleHttpResponse> createVehicle(@Valid @RequestBody SaveVehicleHttpRequest request) {
        return ResponseEntity.ok(saveVehicle(null, request));
    }

    @PutMapping("/ioauto/vehicles/{vehicleId}")
    @Transactional
    public ResponseEntity<IoAutoVehicleHttpResponse> updateVehicle(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody SaveVehicleHttpRequest request
    ) {
        return ResponseEntity.ok(saveVehicle(vehicleId, request));
    }

    @GetMapping("/ioauto/integrations")
    public ResponseEntity<List<IoAutoIntegrationHttpResponse>> listIntegrations() {
        UUID companyId = currentUser.companyId();
        List<IoAutoIntegrationHttpResponse> response = integrations.findAllByCompanyIdOrderByDisplayNameAsc(companyId).stream()
                .filter(entity -> isSupportedProvider(entity.getProviderKey()))
                .map(this::toIntegrationResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/ioauto/integrations/{providerKey}")
    @Transactional
    public ResponseEntity<IoAutoIntegrationHttpResponse> updateIntegration(
            @PathVariable String providerKey,
            @Valid @RequestBody UpdateIntegrationHttpRequest request
    ) {
        UUID companyId = currentUser.companyId();
        Instant now = Instant.now();
        String normalizedProviderKey = normalizeProviderKey(providerKey);
        if (!isSupportedProvider(normalizedProviderKey)) {
            throw new BusinessException("IOAUTO_INTEGRATION_UNSUPPORTED", "Esta integração não está mais disponível.");
        }

        JpaIoAutoIntegrationEntity entity = integrations.findByCompanyIdAndProviderKey(companyId, normalizedProviderKey)
                .orElseGet(() -> {
                    JpaIoAutoIntegrationEntity created = new JpaIoAutoIntegrationEntity();
                    created.setId(UUID.randomUUID());
                    created.setCompanyId(companyId);
                    created.setProviderKey(normalizedProviderKey);
                    created.setCreatedAt(now);
                    return created;
                });

        entity.setDisplayName(normalizeText(request.displayName(), defaultIntegrationLabel(normalizedProviderKey)));
        entity.setStatus(normalizeText(request.status(), "CONFIGURATION_REQUIRED"));
        entity.setEndpointUrl(normalizeNullableText(request.endpointUrl()));
        entity.setAccountName(normalizeNullableText(request.accountName()));
        entity.setUsername(normalizeNullableText(request.username()));
        if (normalizeText(request.apiToken()).isBlank() == false) {
            entity.setApiToken(request.apiToken().trim());
        }
        if (normalizeText(request.webhookSecret()).isBlank() == false) {
            entity.setWebhookSecret(request.webhookSecret().trim());
        }
        entity.setLastError(normalizeNullableText(request.lastError()));
        entity.setSettingsJson(writeJsonObject(request.settings()));
        entity.setLastSyncAt(request.markSyncedNow() ? now : entity.getLastSyncAt());
        entity.setUpdatedAt(now);
        integrations.save(entity);

        return ResponseEntity.ok(toIntegrationResponse(entity));
    }

    @GetMapping("/ioauto/publications")
    public ResponseEntity<List<IoAutoPublicationHttpResponse>> listPublications() {
        UUID companyId = currentUser.companyId();
        Map<UUID, JpaIoAutoVehicleEntity> vehiclesById = vehicles.findAllByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
                .collect(java.util.stream.Collectors.toMap(JpaIoAutoVehicleEntity::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, JpaIoAutoIntegrationEntity> integrationsByKey = integrations.findAllByCompanyIdOrderByDisplayNameAsc(companyId).stream()
                .filter(integration -> isSupportedProvider(integration.getProviderKey()))
                .collect(java.util.stream.Collectors.toMap(JpaIoAutoIntegrationEntity::getProviderKey, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<IoAutoPublicationHttpResponse> response = publications.findAllByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
                .filter(publication -> isSupportedProvider(publication.getProviderKey()))
                .map(publication -> {
                    JpaIoAutoVehicleEntity vehicle = vehiclesById.get(publication.getVehicleId());
                    JpaIoAutoIntegrationEntity integration = integrationsByKey.get(publication.getProviderKey());
                    return new IoAutoPublicationHttpResponse(
                            publication.getId(),
                            publication.getVehicleId(),
                            vehicle == null ? "Veículo removido" : vehicle.getTitle(),
                            normalizeText(publication.getProviderKey()),
                            integration == null ? defaultIntegrationLabel(publication.getProviderKey()) : integration.getDisplayName(),
                            normalizeText(publication.getStatus(), "READY_TO_SYNC"),
                            normalizeNullableText(publication.getExternalUrl()),
                            normalizeNullableText(publication.getLastError()),
                            publication.getPublishedAt(),
                            publication.getUpdatedAt()
                    );
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ioauto/billing")
    public ResponseEntity<BillingSnapshot> getBilling() {
        return ResponseEntity.ok(billingService.getBillingSnapshot(currentUser.companyId()));
    }

    @PostMapping("/ioauto/billing/portal")
    public ResponseEntity<PortalLaunch> createBillingPortal() {
        return ResponseEntity.ok(billingService.createPortalSession(currentUser.companyId()));
    }

    private IoAutoVehicleHttpResponse saveVehicle(UUID vehicleId, SaveVehicleHttpRequest request) {
        UUID companyId = currentUser.companyId();
        Instant now = Instant.now();
        JpaIoAutoVehicleEntity entity = vehicleId == null
                ? new JpaIoAutoVehicleEntity()
                : vehicles.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "Veículo não encontrado."));

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCompanyId(companyId);
            entity.setCreatedAt(now);
        }

        entity.setStockNumber(normalizeNullableText(request.stockNumber()));
        entity.setTitle(requireText(request.title(), "Informe um título para o anúncio."));
        entity.setBrand(requireText(request.brand(), "Informe a marca do veículo."));
        entity.setModel(requireText(request.model(), "Informe o modelo do veículo."));
        entity.setVersion(normalizeNullableText(request.version()));
        entity.setModelYear(request.modelYear());
        entity.setManufactureYear(request.manufactureYear());
        entity.setPriceCents(request.priceCents());
        entity.setMileage(request.mileage());
        entity.setTransmission(normalizeNullableText(request.transmission()));
        entity.setFuelType(normalizeNullableText(request.fuelType()));
        entity.setBodyType(normalizeNullableText(request.bodyType()));
        entity.setColor(normalizeNullableText(request.color()));
        entity.setPlateFinal(normalizeNullableText(request.plateFinal()));
        entity.setCity(normalizeNullableText(request.city()));
        entity.setState(normalizeNullableText(request.state()).toUpperCase(Locale.ROOT));
        entity.setFeatured(Boolean.TRUE.equals(request.featured()));
        entity.setStatus(normalizeText(request.status(), "DRAFT"));
        entity.setDescription(normalizeNullableText(request.description()));
        entity.setCoverImageUrl(normalizeNullableText(request.coverImageUrl()));
        entity.setGalleryJson(writeStringArray(request.gallery()));
        entity.setOptionalsJson(writeStringArray(request.optionals()));
        entity.setUpdatedAt(now);
        vehicles.save(entity);

        List<String> selectedIntegrations = sanitizeIntegrationKeys(request.targetIntegrations()).stream()
                .filter(this::supportsVehiclePublication)
                .toList();
        List<JpaIoAutoVehiclePublicationEntity> existingPublications = publications.findAllByCompanyIdAndVehicleId(companyId, entity.getId());
        Map<String, JpaIoAutoVehiclePublicationEntity> existingByProvider = existingPublications.stream()
                .collect(java.util.stream.Collectors.toMap(JpaIoAutoVehiclePublicationEntity::getProviderKey, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<JpaIoAutoVehiclePublicationEntity> nextPublications = new ArrayList<>();
        for (String providerKey : selectedIntegrations) {
            JpaIoAutoIntegrationEntity integration = resolveOrCreateIntegration(companyId, providerKey, now);
            JpaIoAutoVehiclePublicationEntity publication = existingByProvider.getOrDefault(providerKey, new JpaIoAutoVehiclePublicationEntity());

            if (publication.getId() == null) {
                publication.setId(UUID.randomUUID());
                publication.setCompanyId(companyId);
                publication.setVehicleId(entity.getId());
                publication.setProviderKey(providerKey);
                publication.setCreatedAt(now);
            }

            publication.setStatus(determinePublicationStatus(integration));
            publication.setLastError("CONNECTED".equalsIgnoreCase(integration.getStatus()) ? null : "Conclua a configuração desta integração para publicar.");
            publication.setUpdatedAt(now);
            nextPublications.add(publication);
        }

        List<JpaIoAutoVehiclePublicationEntity> toRemove = existingPublications.stream()
                .filter(publication -> selectedIntegrations.contains(publication.getProviderKey()) == false)
                .toList();

        if (!toRemove.isEmpty()) {
            publications.deleteAll(toRemove);
        }
        if (!nextPublications.isEmpty()) {
            publications.saveAll(nextPublications);
        }

        Map<String, JpaIoAutoIntegrationEntity> integrationsByKey = integrations.findAllByCompanyIdOrderByDisplayNameAsc(companyId).stream()
                .filter(integration -> isSupportedProvider(integration.getProviderKey()))
                .collect(java.util.stream.Collectors.toMap(JpaIoAutoIntegrationEntity::getProviderKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        return toVehicleResponse(entity, publications.findAllByCompanyIdAndVehicleId(companyId, entity.getId()), integrationsByKey);
    }

    private Map<UUID, List<JpaIoAutoVehiclePublicationEntity>> groupPublicationsByVehicle(UUID companyId, List<JpaIoAutoVehicleEntity> companyVehicles) {
        List<UUID> vehicleIds = companyVehicles.stream().map(JpaIoAutoVehicleEntity::getId).toList();
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<JpaIoAutoVehiclePublicationEntity>> grouped = new LinkedHashMap<>();
        for (JpaIoAutoVehiclePublicationEntity publication : publications.findAllByCompanyIdAndVehicleIdIn(companyId, vehicleIds)) {
            grouped.computeIfAbsent(publication.getVehicleId(), ignored -> new ArrayList<>()).add(publication);
        }
        return grouped;
    }

    private IoAutoVehicleHttpResponse toVehicleResponse(
            JpaIoAutoVehicleEntity vehicle,
            List<JpaIoAutoVehiclePublicationEntity> vehiclePublications,
            Map<String, JpaIoAutoIntegrationEntity> integrationsByKey
    ) {
        List<IoAutoVehicleHttpResponse.PublicationSummary> publicationSummaries = vehiclePublications.stream()
                .filter(publication -> isSupportedProvider(publication.getProviderKey()))
                .sorted(Comparator.comparing(JpaIoAutoVehiclePublicationEntity::getProviderKey))
                .map(publication -> {
                    JpaIoAutoIntegrationEntity integration = integrationsByKey.get(publication.getProviderKey());
                    return new IoAutoVehicleHttpResponse.PublicationSummary(
                            publication.getId(),
                            publication.getProviderKey(),
                            integration == null ? defaultIntegrationLabel(publication.getProviderKey()) : integration.getDisplayName(),
                            normalizeText(publication.getStatus(), "READY_TO_SYNC"),
                            normalizeNullableText(publication.getExternalUrl())
                    );
                })
                .toList();

        return new IoAutoVehicleHttpResponse(
                vehicle.getId(),
                normalizeNullableText(vehicle.getStockNumber()),
                vehicle.getTitle(),
                vehicle.getBrand(),
                vehicle.getModel(),
                normalizeNullableText(vehicle.getVersion()),
                vehicle.getModelYear(),
                vehicle.getManufactureYear(),
                vehicle.getPriceCents(),
                vehicle.getMileage(),
                normalizeNullableText(vehicle.getTransmission()),
                normalizeNullableText(vehicle.getFuelType()),
                normalizeNullableText(vehicle.getBodyType()),
                normalizeNullableText(vehicle.getColor()),
                normalizeNullableText(vehicle.getPlateFinal()),
                normalizeNullableText(vehicle.getCity()),
                normalizeNullableText(vehicle.getState()),
                vehicle.isFeatured(),
                normalizeText(vehicle.getStatus(), "DRAFT"),
                normalizeNullableText(vehicle.getDescription()),
                normalizeNullableText(vehicle.getCoverImageUrl()),
                readStringArray(vehicle.getGalleryJson()),
                readStringArray(vehicle.getOptionalsJson()),
                publicationSummaries,
                vehicle.getUpdatedAt()
        );
    }

    private IoAutoIntegrationHttpResponse toIntegrationResponse(JpaIoAutoIntegrationEntity entity) {
        return new IoAutoIntegrationHttpResponse(
                normalizeText(entity.getProviderKey()),
                normalizeText(entity.getDisplayName(), defaultIntegrationLabel(entity.getProviderKey())),
                normalizeText(entity.getStatus(), "CONFIGURATION_REQUIRED"),
                normalizeNullableText(entity.getEndpointUrl()),
                normalizeNullableText(entity.getAccountName()),
                normalizeNullableText(entity.getUsername()),
                normalizeText(entity.getApiToken()).isBlank() == false,
                normalizeText(entity.getWebhookSecret()).isBlank() == false,
                supportsVehiclePublication(entity.getProviderKey()),
                entity.getLastSyncAt(),
                normalizeNullableText(entity.getLastError()),
                readObjectMap(entity.getSettingsJson())
        );
    }

    private JpaIoAutoIntegrationEntity resolveOrCreateIntegration(UUID companyId, String providerKey, Instant now) {
        return integrations.findByCompanyIdAndProviderKey(companyId, providerKey)
                .orElseGet(() -> {
                    JpaIoAutoIntegrationEntity entity = new JpaIoAutoIntegrationEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setCompanyId(companyId);
                    entity.setProviderKey(providerKey);
                    entity.setDisplayName(defaultIntegrationLabel(providerKey));
                    entity.setStatus("CONFIGURATION_REQUIRED");
                    entity.setSettingsJson("{}");
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    return integrations.save(entity);
                });
    }

    private String determinePublicationStatus(JpaIoAutoIntegrationEntity integration) {
        return "CONNECTED".equalsIgnoreCase(integration.getStatus()) || "ACTIVE".equalsIgnoreCase(integration.getStatus())
                ? "READY_TO_SYNC"
                : "WAITING_CONFIGURATION";
    }

    private List<String> sanitizeIntegrationKeys(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String normalized = normalizeProviderKey(value);
            if (normalized.isBlank()) continue;
            unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    private String normalizeProviderKey(String value) {
        return normalizeText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    private String writeStringArray(List<String> values) {
        try {
            List<String> normalized = (values == null ? List.<String>of() : values).stream()
                    .map(this::normalizeText)
                    .filter(item -> item.isBlank() == false)
                    .distinct()
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new BusinessException("IOAUTO_JSON_SERIALIZATION_FAILED", "Não foi possível salvar os dados do cadastro.");
        }
    }

    private List<String> readStringArray(String raw) {
        try {
            String normalized = normalizeText(raw, "[]");
            return OBJECT_MAPPER.readValue(normalized, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJsonObject(Map<String, String> values) {
        try {
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : (values == null ? Map.<String, String>of() : values).entrySet()) {
                String key = normalizeText(entry.getKey());
                if (key.isBlank()) continue;
                normalized.put(key, normalizeText(entry.getValue()));
            }
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new BusinessException("IOAUTO_SETTINGS_SERIALIZATION_FAILED", "Não foi possível salvar as configurações da integração.");
        }
    }

    private Map<String, String> readObjectMap(String raw) {
        try {
            return OBJECT_MAPPER.readValue(normalizeText(raw, "{}"), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private DashboardPeriodSelection resolveDashboardPeriod(String preset, String from, String to) {
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        String normalizedPreset = normalizeText(preset, "30d").toLowerCase(Locale.ROOT);

        LocalDate resolvedFrom;
        LocalDate resolvedTo;

        switch (normalizedPreset) {
            case "7d" -> {
                resolvedTo = today;
                resolvedFrom = today.minusDays(6);
            }
            case "90d" -> {
                resolvedTo = today;
                resolvedFrom = today.minusDays(89);
            }
            case "month" -> {
                resolvedTo = today;
                resolvedFrom = today.withDayOfMonth(1);
            }
            case "custom" -> {
                resolvedFrom = parseDashboardDate(from, today.minusDays(29));
                resolvedTo = parseDashboardDate(to, today);
            }
            case "30d" -> {
                resolvedTo = today;
                resolvedFrom = today.minusDays(29);
            }
            default -> {
                resolvedTo = today;
                resolvedFrom = today.minusDays(29);
                normalizedPreset = "30d";
            }
        }

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException("IOAUTO_DASHBOARD_INVALID_PERIOD", "O período informado para o dashboard é inválido.");
        }

        if (resolvedFrom.isBefore(resolvedTo.minusDays(365))) {
            resolvedFrom = resolvedTo.minusDays(365);
        }

        return new DashboardPeriodSelection(
                normalizedPreset,
                resolvedFrom,
                resolvedTo,
                resolvedFrom.atStartOfDay(DASHBOARD_ZONE).toInstant(),
                resolvedTo.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant()
        );
    }

    private LocalDate parseDashboardDate(String raw, LocalDate fallback) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (Exception exception) {
            throw new BusinessException("IOAUTO_DASHBOARD_INVALID_DATE", "Não foi possível interpretar uma das datas do dashboard.");
        }
    }

    private List<IoAutoDashboardHttpResponse.PeriodPoint> buildLeadVsSalesSeries(
            DashboardPeriodSelection periodSelection,
            List<JpaAtendimentoSessionEntity> leadSessions,
            List<JpaAtendimentoSessionEntity> salesSessions
    ) {
        Map<LocalDate, Long> leadsByDate = new LinkedHashMap<>();
        Map<LocalDate, Long> salesByDate = new LinkedHashMap<>();

        for (JpaAtendimentoSessionEntity session : leadSessions) {
            LocalDate bucket = session.getArrivedAt().atZone(DASHBOARD_ZONE).toLocalDate();
            leadsByDate.put(bucket, leadsByDate.getOrDefault(bucket, 0L) + 1L);
        }

        for (JpaAtendimentoSessionEntity session : salesSessions) {
            if (session.getSaleCompletedAt() == null) continue;
            LocalDate bucket = session.getSaleCompletedAt().atZone(DASHBOARD_ZONE).toLocalDate();
            salesByDate.put(bucket, salesByDate.getOrDefault(bucket, 0L) + 1L);
        }

        List<IoAutoDashboardHttpResponse.PeriodPoint> points = new ArrayList<>();
        LocalDate cursor = periodSelection.fromDate();
        while (cursor.isAfter(periodSelection.toDate()) == false) {
            points.add(new IoAutoDashboardHttpResponse.PeriodPoint(
                    DATE_FORMATTER.format(cursor),
                    cursor.format(DateTimeFormatter.ofPattern("dd/MM")),
                    leadsByDate.getOrDefault(cursor, 0L),
                    salesByDate.getOrDefault(cursor, 0L)
            ));
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    private List<IoAutoDashboardHttpResponse.SellerSalesSummary> buildSalesBySeller(List<JpaAtendimentoSessionEntity> salesSessions) {
        Map<String, Long> totalsBySeller = new LinkedHashMap<>();
        Map<String, String> idsBySeller = new LinkedHashMap<>();

        for (JpaAtendimentoSessionEntity session : salesSessions) {
            String sellerName = normalizeText(session.getResponsibleUserName(), "Sem vendedor");
            String sellerKey = session.getResponsibleUserId() == null ? "unassigned" : session.getResponsibleUserId().toString();
            totalsBySeller.put(sellerKey, totalsBySeller.getOrDefault(sellerKey, 0L) + 1L);
            idsBySeller.put(sellerKey, sellerName);
        }

        return totalsBySeller.entrySet().stream()
                .map(entry -> new IoAutoDashboardHttpResponse.SellerSalesSummary(
                        "unassigned".equals(entry.getKey()) ? null : UUID.fromString(entry.getKey()),
                        idsBySeller.getOrDefault(entry.getKey(), "Sem vendedor"),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(IoAutoDashboardHttpResponse.SellerSalesSummary::totalSales).reversed()
                        .thenComparing(IoAutoDashboardHttpResponse.SellerSalesSummary::sellerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String sourceLabel(String key) {
        return switch (key) {
            case "WEBMOTORS" -> "WebMotors";
            default -> "Outra origem";
        };
    }

    private boolean supportsVehiclePublication(String providerKey) {
        return isSupportedProvider(providerKey);
    }

    private boolean isActivePublicationStatus(String status) {
        String normalized = normalizeText(status).toUpperCase(Locale.ROOT);
        return !"REMOVED".equals(normalized) && !"SOLD".equals(normalized) && !"ARCHIVED".equals(normalized);
    }

    private String normalizeSourcePlatform(String value) {
        return normalizeText(value, "OTHER").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            throw new BusinessException("IOAUTO_INVALID_PAYLOAD", message);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeText(String value, String fallback) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String defaultIntegrationLabel(String providerKey) {
        String normalized = normalizeProviderKey(providerKey);
        return switch (normalized) {
            case "webmotors" -> "Webmotors / Estoque e Leads";
            case "olx-autos" -> "OLX Autos";
            case "icarros" -> "iCarros";
            default -> normalized.isBlank() ? "Integração" : normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
        };
    }

    private boolean isSupportedProvider(String providerKey) {
        return "zapi".equalsIgnoreCase(normalizeProviderKey(providerKey)) == false;
    }

    private boolean isSupportedLeadSource(String sourcePlatform) {
        String normalized = normalizeSourcePlatform(sourcePlatform);
        return !"ZAPI".equals(normalized) && !"WHATSAPP".equals(normalized);
    }

    private record DashboardPeriodSelection(
            String preset,
            LocalDate fromDate,
            LocalDate toDate,
            Instant fromAt,
            Instant toExclusiveAt
    ) {
    }

    public record IoAutoDashboardHttpResponse(
            String companyName,
            long vehicleCount,
            long featuredCount,
            long publicationCount,
            long leadCount,
            long connectedIntegrations,
            BillingSnapshot billing,
            PeriodFilter periodFilter,
            List<PeriodPoint> leadVsSales,
            List<SellerSalesSummary> salesBySeller,
            List<SourceSummary> leadSources,
            List<RecentVehicle> recentVehicles,
            List<RecentConversation> recentConversations
    ) {
        public record PeriodFilter(String preset, String from, String to) {
        }

        public record PeriodPoint(String date, String label, long leads, long sales) {
        }

        public record SellerSalesSummary(UUID sellerId, String sellerName, long totalSales) {
        }

        public record SourceSummary(String key, String label, long total) {
        }

        public record RecentVehicle(
                UUID id,
                String title,
                Long priceCents,
                String status,
                Instant updatedAt,
                int publicationCount
        ) {
        }

        public record RecentConversation(
                UUID id,
                String contactName,
                String lastMessage,
                Instant lastAt,
                String sourcePlatform
        ) {
        }
    }

    public record IoAutoVehicleHttpResponse(
            UUID id,
            String stockNumber,
            String title,
            String brand,
            String model,
            String version,
            Integer modelYear,
            Integer manufactureYear,
            Long priceCents,
            Integer mileage,
            String transmission,
            String fuelType,
            String bodyType,
            String color,
            String plateFinal,
            String city,
            String state,
            boolean featured,
            String status,
            String description,
            String coverImageUrl,
            List<String> gallery,
            List<String> optionals,
            List<PublicationSummary> publications,
            Instant updatedAt
    ) {
        public record PublicationSummary(
                UUID id,
                String providerKey,
                String providerName,
                String status,
                String externalUrl
        ) {
        }
    }

    public record IoAutoIntegrationHttpResponse(
            String providerKey,
            String displayName,
            String status,
            String endpointUrl,
            String accountName,
            String username,
            boolean hasApiToken,
            boolean hasWebhookSecret,
            boolean supportsPublication,
            Instant lastSyncAt,
            String lastError,
            Map<String, String> settings
    ) {
    }

    public record IoAutoPublicationHttpResponse(
            UUID id,
            UUID vehicleId,
            String vehicleTitle,
            String providerKey,
            String providerName,
            String status,
            String externalUrl,
            String lastError,
            Instant publishedAt,
            Instant updatedAt
    ) {
    }

    public record SaveVehicleHttpRequest(
            String stockNumber,
            @NotBlank(message = "Informe um título.") String title,
            @NotBlank(message = "Informe a marca.") String brand,
            @NotBlank(message = "Informe o modelo.") String model,
            String version,
            Integer modelYear,
            Integer manufactureYear,
            Long priceCents,
            Integer mileage,
            String transmission,
            String fuelType,
            String bodyType,
            String color,
            String plateFinal,
            String city,
            String state,
            Boolean featured,
            String status,
            String description,
            String coverImageUrl,
            List<String> gallery,
            List<String> optionals,
            List<String> targetIntegrations
    ) {
    }

    public record UpdateIntegrationHttpRequest(
            String displayName,
            String status,
            String endpointUrl,
            String accountName,
            String username,
            String apiToken,
            String webhookSecret,
            String lastError,
            Map<String, String> settings,
            boolean markSyncedNow
    ) {
    }
}
