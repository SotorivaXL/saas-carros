package com.io.appioweb.adapters.web.ioauto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AsaasWebhookController {

    private final IoAutoBillingService billingService;

    public AsaasWebhookController(IoAutoBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/webhooks/asaas/billing")
    public ResponseEntity<Void> handleAsaasWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "asaas-access-token", required = false) String authToken
    ) {
        billingService.handleAsaasWebhook(payload, authToken);
        return ResponseEntity.ok().build();
    }
}
