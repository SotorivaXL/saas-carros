package com.io.appioweb.adapters.web.ioauto;

import com.io.appioweb.adapters.persistence.ioauto.JpaWebmotorsLeadEntity;
import com.io.appioweb.application.ioauto.webmotors.WebmotorsLeadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class WebmotorsLeadCallbackController {

    private final WebmotorsLeadService leadService;

    public WebmotorsLeadCallbackController(WebmotorsLeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/webhooks/webmotors/leads")
    public ResponseEntity<?> callback(
            @RequestParam UUID companyId,
            @RequestParam(defaultValue = "default") String storeKey,
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload
    ) {
        JpaWebmotorsLeadEntity lead = leadService.processCallback(companyId, storeKey, normalizeHeaders(headers), payload);
        if (lead == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((key, value) -> normalized.put(key, value));
        return normalized;
    }
}
