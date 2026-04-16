package com.io.appioweb.adapters.web.ioauto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final IoAutoBillingService billingService;

    public StripeWebhookController(IoAutoBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/webhooks/stripe/billing")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature
    ) {
        billingService.handleStripeWebhook(payload, signature);
        return ResponseEntity.noContent().build();
    }
}
