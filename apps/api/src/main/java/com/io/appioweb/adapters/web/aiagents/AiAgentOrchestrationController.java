package com.io.appioweb.adapters.web.aiagents;

import com.io.appioweb.adapters.web.aiagents.request.AiAgentOrchestrateHttpRequest;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentToolHandoffHttpRequest;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentToolKbSearchHttpRequest;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentOrchestrateHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentToolHandoffHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentToolKbSearchHttpResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiAgentOrchestrationController {

    private final AiAgentOrchestrationService service;

    public AiAgentOrchestrationController(AiAgentOrchestrationService service) {
        this.service = service;
    }

    @PostMapping("/ai-agent/orchestrate")
    public ResponseEntity<AiAgentOrchestrateHttpResponse> orchestrate(@Valid @RequestBody AiAgentOrchestrateHttpRequest req) {
        return ResponseEntity.ok(service.orchestrate(req));
    }

    @PostMapping("/ai-agent/tools/kb-search")
    public ResponseEntity<AiAgentToolKbSearchHttpResponse> kbSearch(@Valid @RequestBody AiAgentToolKbSearchHttpRequest req) {
        return ResponseEntity.ok(service.executeKbSearch(req));
    }

    @PostMapping("/ai-agent/tools/handoff")
    public ResponseEntity<AiAgentToolHandoffHttpResponse> handoff(@Valid @RequestBody AiAgentToolHandoffHttpRequest req) {
        return ResponseEntity.ok(service.executeHandoff(req));
    }
}
