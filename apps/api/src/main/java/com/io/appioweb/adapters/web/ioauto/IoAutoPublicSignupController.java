package com.io.appioweb.adapters.web.ioauto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class IoAutoPublicSignupController {

    private final IoAutoBillingService billingService;

    public IoAutoPublicSignupController(IoAutoBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/public/signup/checkout")
    public ResponseEntity<CheckoutLaunch> createCheckout(@Valid @RequestBody SignupCheckoutHttpRequest request) {
        return ResponseEntity.ok(billingService.createSignupCheckout(new PublicSignupPayload(
                request.ownerFullName(),
                request.companyName(),
                request.email(),
                request.phone()
        )));
    }

    @GetMapping("/public/signup/status")
    public ResponseEntity<SignupStatusSnapshot> getSignupStatus(
            @RequestParam("intentId") UUID intentId,
            @RequestParam(name = "sessionId", required = false) String sessionId
    ) {
        return ResponseEntity.ok(billingService.getSignupStatus(intentId, sessionId));
    }

    public record SignupCheckoutHttpRequest(
            @NotBlank(message = "Informe o nome completo.") String ownerFullName,
            @NotBlank(message = "Informe o nome da loja.") String companyName,
            @Email(message = "Informe um e-mail valido.") @NotBlank(message = "Informe o e-mail.") String email,
            @NotBlank(message = "Informe o telefone.") String phone
    ) {
    }
}
