package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorDistributionHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorRouteHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorSimulateHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorUpsertHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.response.AiSupervisorHttpResponse;
import com.io.appioweb.adapters.web.aisupervisors.response.AiSupervisorRouteHttpResponse;
import com.io.appioweb.adapters.web.aisupervisors.response.AiSupervisorSimulateHttpResponse;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.shared.errors.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AiSupervisorController {

    private final CurrentUserPort currentUser;
    private final AiSupervisorAdminService adminService;
    private final SupervisorRoutingService routingService;

    public AiSupervisorController(
            CurrentUserPort currentUser,
            AiSupervisorAdminService adminService,
            SupervisorRoutingService routingService
    ) {
        this.currentUser = currentUser;
        this.adminService = adminService;
        this.routingService = routingService;
    }

    @PostMapping({"/ai/supervisors", "/api/ai/supervisors"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorHttpResponse> create(@Valid @RequestBody AiSupervisorUpsertHttpRequest request) {
        return ResponseEntity.ok(adminService.create(currentUser.companyId(), request));
    }

    @PutMapping({"/ai/supervisors/{id}", "/api/ai/supervisors/{id}"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorHttpResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AiSupervisorUpsertHttpRequest request
    ) {
        return ResponseEntity.ok(adminService.update(currentUser.companyId(), id, request));
    }

    @GetMapping({"/ai/supervisors/{id}", "/api/ai/supervisors/{id}"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorHttpResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.get(currentUser.companyId(), id));
    }

    @GetMapping({"/ai/supervisors", "/api/ai/supervisors"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<List<AiSupervisorHttpResponse>> list() {
        return ResponseEntity.ok(adminService.list(currentUser.companyId()));
    }

    @PutMapping({"/ai/supervisors/{id}/distribution", "/api/ai/supervisors/{id}/distribution"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorHttpResponse> saveDistribution(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AiSupervisorDistributionHttpRequest request
    ) {
        return ResponseEntity.ok(adminService.saveDistribution(currentUser.companyId(), id, request));
    }

    @PostMapping({"/ai/supervisors/{id}/simulate", "/api/ai/supervisors/{id}/simulate"})
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorSimulateHttpResponse> simulate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AiSupervisorSimulateHttpRequest request
    ) {
        return ResponseEntity.ok(adminService.simulate(currentUser.companyId(), id, request));
    }

    @PostMapping("/internal/ai/supervisors/{id}/route")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<AiSupervisorRouteHttpResponse> route(
            @PathVariable UUID id,
            @Valid @RequestBody AiSupervisorRouteHttpRequest request
    ) {
        if (request == null || request.conversationId() == null || request.inboundMessageId() == null) {
            throw new BusinessException("SUPERVISOR_ROUTE_REQUEST_INVALID", "conversationId e inboundMessageId sao obrigatorios");
        }
        SupervisorRoutingService.RoutingResult result = routingService.routeOrTriageLead(
                currentUser.companyId(),
                id,
                request.conversationId(),
                request.inboundMessageId()
        );
        return ResponseEntity.ok(new AiSupervisorRouteHttpResponse(
                result.action(),
                result.targetAgentId(),
                result.outboundMessageId(),
                result.evaluationKey(),
                result.reason(),
                result.errorCode(),
                result.duplicate()
        ));
    }
}
