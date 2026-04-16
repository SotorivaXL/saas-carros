package com.io.appioweb.adapters.web.ioauto;

import com.io.appioweb.adapters.persistence.ioauto.IoAutoBillingSubscriptionRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.IoAutoIntegrationRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.IoAutoSignupIntentRepositoryJpa;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoBillingSubscriptionEntity;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoIntegrationEntity;
import com.io.appioweb.adapters.persistence.ioauto.JpaIoAutoSignupIntentEntity;
import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import com.io.appioweb.application.auth.port.out.PasswordHasherPort;
import com.io.appioweb.application.auth.port.out.TeamRepositoryPort;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import com.io.appioweb.domain.auth.entity.Company;
import com.io.appioweb.domain.auth.entity.Team;
import com.io.appioweb.domain.auth.entity.User;
import com.io.appioweb.shared.errors.BusinessException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.billingportal.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class IoAutoBillingService {

    private static final String BILLING_PROVIDER = "STRIPE";
    private static final String SIGNUP_PENDING = "PENDING_PAYMENT";
    private static final String SIGNUP_ACTIVE = "ACTIVE";
    private static final String SIGNUP_CANCELLED = "CANCELLED";
    private static final String DEFAULT_PLAN_KEY = "ioauto-growth";
    private static final String DEFAULT_PLAN_NAME = "IOAuto Growth";
    private static final String DEFAULT_TEAM_NAME = "Equipe Comercial";
    private static final String DEFAULT_BUSINESS_HOURS_WEEKLY_JSON = """
            {"sunday":{"active":false,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"monday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"tuesday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"wednesday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"thursday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"friday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},"saturday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"16:00"}}
            """;

    private final IoAutoSignupIntentRepositoryJpa signupIntents;
    private final IoAutoBillingSubscriptionRepositoryJpa subscriptions;
    private final IoAutoIntegrationRepositoryJpa integrations;
    private final CompanyRepositoryPort companies;
    private final UserRepositoryPort users;
    private final TeamRepositoryPort teams;
    private final PasswordHasherPort hasher;
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final String stripePriceId;
    private final String publicAppUrl;
    private final String planKey;
    private final String planName;

    public IoAutoBillingService(
            IoAutoSignupIntentRepositoryJpa signupIntents,
            IoAutoBillingSubscriptionRepositoryJpa subscriptions,
            IoAutoIntegrationRepositoryJpa integrations,
            CompanyRepositoryPort companies,
            UserRepositoryPort users,
            TeamRepositoryPort teams,
            PasswordHasherPort hasher,
            @Value("${STRIPE_SECRET_KEY:}") String stripeSecretKey,
            @Value("${STRIPE_WEBHOOK_SECRET:}") String stripeWebhookSecret,
            @Value("${STRIPE_PRICE_ID:}") String stripePriceId,
            @Value("${APP_PUBLIC_URL:http://localhost:3000}") String publicAppUrl,
            @Value("${IOAUTO_PLAN_KEY:" + DEFAULT_PLAN_KEY + "}") String planKey,
            @Value("${IOAUTO_PLAN_NAME:" + DEFAULT_PLAN_NAME + "}") String planName
    ) {
        this.signupIntents = signupIntents;
        this.subscriptions = subscriptions;
        this.integrations = integrations;
        this.companies = companies;
        this.users = users;
        this.teams = teams;
        this.hasher = hasher;
        this.stripeSecretKey = normalizeText(stripeSecretKey);
        this.stripeWebhookSecret = normalizeText(stripeWebhookSecret);
        this.stripePriceId = normalizeText(stripePriceId);
        this.publicAppUrl = trimTrailingSlash(normalizeText(publicAppUrl, "http://localhost:3000"));
        this.planKey = normalizeText(planKey, DEFAULT_PLAN_KEY);
        this.planName = normalizeText(planName, DEFAULT_PLAN_NAME);
    }

    @Transactional
    public CheckoutLaunch createSignupCheckout(PublicSignupPayload payload) {
        requireStripeCheckoutConfiguration();

        String ownerFullName = requireText(payload.ownerFullName(), "Informe o nome do responsavel.");
        String companyName = requireText(payload.companyName(), "Informe o nome da operacao.");
        String email = normalizeEmail(payload.email());
        String password = requireText(payload.password(), "Informe uma senha de acesso.");

        if (password.length() < 8) {
            throw new BusinessException("SIGNUP_INVALID_PASSWORD", "Use uma senha com pelo menos 8 caracteres.");
        }
        if (users.findByEmailGlobal(email).isPresent()) {
            throw new BusinessException("SIGNUP_EMAIL_ALREADY_EXISTS", "Já existe uma conta criada com este e-mail.");
        }

        Instant now = Instant.now();
        JpaIoAutoSignupIntentEntity intent = new JpaIoAutoSignupIntentEntity();
        intent.setId(UUID.randomUUID());
        intent.setCompanyName(companyName);
        intent.setOwnerFullName(ownerFullName);
        intent.setEmail(email);
        intent.setWhatsappNumber("");
        intent.setPasswordHash(hasher.hash(password));
        intent.setPlanKey(planKey);
        intent.setProvider(BILLING_PROVIDER);
        intent.setStatus(SIGNUP_PENDING);
        intent.setProviderPriceId(stripePriceId);
        intent.setCreatedAt(now);
        intent.setUpdatedAt(now);

        signupIntents.save(intent);

        try {
            Stripe.apiKey = stripeSecretKey;

            com.stripe.param.checkout.SessionCreateParams params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(publicAppUrl + "/assinar/sucesso?intent=" + intent.getId() + "&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(publicAppUrl + "/assinar/cancelado?intent=" + intent.getId())
                    .setCustomerEmail(email)
                    .setClientReferenceId(intent.getId().toString())
                    .setAllowPromotionCodes(true)
                    .putMetadata("intentId", intent.getId().toString())
                    .putMetadata("planKey", planKey)
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(stripePriceId)
                            .setQuantity(1L)
                            .build())
                    .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                            .putMetadata("intentId", intent.getId().toString())
                            .putMetadata("planKey", planKey)
                            .build())
                    .build();

            Session session = Session.create(params);
            intent.setCheckoutSessionId(session.getId());
            intent.setUpdatedAt(Instant.now());
            signupIntents.save(intent);

            String checkoutUrl = normalizeText(session.getUrl());
            if (checkoutUrl.isBlank()) {
                throw new BusinessException("BILLING_CHECKOUT_URL_MISSING", "O checkout não retornou uma URL válida.");
            }

            return new CheckoutLaunch(intent.getId(), checkoutUrl);
        } catch (StripeException exception) {
            throw new BusinessException("BILLING_CHECKOUT_FAILED", "Não foi possível iniciar o checkout da assinatura.");
        }
    }

    @Transactional
    public SignupStatusSnapshot getSignupStatus(UUID intentId, String sessionId) {
        JpaIoAutoSignupIntentEntity intent = signupIntents.findById(intentId)
                .orElseThrow(() -> new BusinessException("SIGNUP_INTENT_NOT_FOUND", "Cadastro não encontrado."));

        if (SIGNUP_ACTIVE.equalsIgnoreCase(intent.getStatus())) {
            return toSignupStatus(intent, "Conta liberada. Use seu e-mail e a senha definida no cadastro.");
        }

        String normalizedSessionId = normalizeText(sessionId);
        if (!normalizedSessionId.isBlank() && !stripeSecretKey.isBlank()) {
            try {
                Stripe.apiKey = stripeSecretKey;
                Session session = Session.retrieve(normalizedSessionId);
                if (isPaidCheckoutSession(session) && normalizeText(session.getSubscription()).isBlank() == false) {
                    activateIntent(intent, session);
                    intent = signupIntents.findById(intentId).orElse(intent);
                    return toSignupStatus(intent, "Pagamento confirmado e acesso liberado.");
                }
            } catch (StripeException ignored) {
                // Mantem o status pendente; o webhook pode concluir a ativacao.
            }
        }

        return toSignupStatus(intent, "Aguardando confirmação do pagamento.");
    }

    @Transactional
    public BillingSnapshot getBillingSnapshot(UUID companyId) {
        Optional<JpaIoAutoBillingSubscriptionEntity> subscription = subscriptions.findTopByCompanyIdOrderByUpdatedAtDesc(companyId);
        return subscription
                .map(item -> new BillingSnapshot(
                        true,
                        normalizeText(item.getPlanName(), planName),
                        normalizeText(item.getStatus(), "inactive"),
                        item.getAmountCents(),
                        normalizeText(item.getCurrency(), "brl"),
                        normalizeText(item.getBillingInterval(), "month"),
                        item.getCurrentPeriodEnd(),
                        item.isCancelAtPeriodEnd(),
                        normalizeText(item.getProvider(), BILLING_PROVIDER),
                        normalizeText(item.getProviderCustomerId()),
                        normalizeText(item.getProviderSubscriptionId())
                ))
                .orElseGet(() -> new BillingSnapshot(
                        false,
                        planName,
                        "pending_configuration",
                        null,
                        "brl",
                        "month",
                        null,
                        false,
                        BILLING_PROVIDER,
                        "",
                        ""
                ));
    }

    public PortalLaunch createPortalSession(UUID companyId) {
        requireStripeCheckoutConfiguration();

        JpaIoAutoBillingSubscriptionEntity subscription = subscriptions.findTopByCompanyIdOrderByUpdatedAtDesc(companyId)
                .orElseThrow(() -> new BusinessException("BILLING_NOT_FOUND", "Não existe uma assinatura vinculada a esta conta."));

        String customerId = normalizeText(subscription.getProviderCustomerId());
        if (customerId.isBlank()) {
            throw new BusinessException("BILLING_CUSTOMER_MISSING", "A assinatura atual ainda não possui cliente Stripe vinculado.");
        }

        try {
            Stripe.apiKey = stripeSecretKey;
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(publicAppUrl + "/protected/assinatura")
                    .build();

            com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
            String portalUrl = normalizeText(session.getUrl());
            if (portalUrl.isBlank()) {
                throw new BusinessException("BILLING_PORTAL_URL_MISSING", "Não foi possível montar o portal da assinatura.");
            }
            return new PortalLaunch(portalUrl);
        } catch (StripeException exception) {
            throw new BusinessException("BILLING_PORTAL_FAILED", "Não foi possível abrir o portal da assinatura.");
        }
    }

    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        requireStripeWebhookConfiguration();

        try {
            Stripe.apiKey = stripeSecretKey;
            Event event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
            StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);

            if ("checkout.session.completed".equals(event.getType()) && object instanceof Session session) {
                String intentId = normalizeText(session.getMetadata() == null ? null : session.getMetadata().get("intentId"));
                if (!intentId.isBlank()) {
                    signupIntents.findById(UUID.fromString(intentId)).ifPresent(intent -> activateIntent(intent, session));
                }
                return;
            }

            if (("customer.subscription.created".equals(event.getType())
                    || "customer.subscription.updated".equals(event.getType())
                    || "customer.subscription.deleted".equals(event.getType()))
                    && object instanceof Subscription subscription) {
                syncSubscription(subscription, null);
                return;
            }
        } catch (SignatureVerificationException exception) {
            throw new BusinessException("BILLING_WEBHOOK_INVALID_SIGNATURE", "Assinatura do webhook Stripe inválida.");
        } catch (StripeException exception) {
            throw new BusinessException("BILLING_WEBHOOK_FAILED", "Falha ao processar evento do Stripe.");
        }
    }

    private SignupStatusSnapshot toSignupStatus(JpaIoAutoSignupIntentEntity intent, String message) {
        return new SignupStatusSnapshot(
                intent.getId(),
                normalizeText(intent.getStatus(), SIGNUP_PENDING),
                message,
                SIGNUP_ACTIVE.equalsIgnoreCase(intent.getStatus()),
                normalizeText(intent.getEmail()),
                normalizeText(intent.getCompanyName())
        );
    }

    private void requireStripeCheckoutConfiguration() {
        if (stripeSecretKey.isBlank() || stripePriceId.isBlank()) {
            throw new BusinessException("BILLING_NOT_CONFIGURED", "Configure STRIPE_SECRET_KEY e STRIPE_PRICE_ID antes de usar o checkout.");
        }
    }

    private void requireStripeWebhookConfiguration() {
        requireStripeCheckoutConfiguration();
        if (stripeWebhookSecret.isBlank()) {
            throw new BusinessException("BILLING_WEBHOOK_NOT_CONFIGURED", "Configure STRIPE_WEBHOOK_SECRET para validar os eventos Stripe.");
        }
    }

    private boolean isPaidCheckoutSession(Session session) {
        String status = normalizeText(session.getStatus()).toLowerCase(Locale.ROOT);
        String paymentStatus = normalizeText(session.getPaymentStatus()).toLowerCase(Locale.ROOT);
        return "complete".equals(status) || "paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus);
    }

    private void activateIntent(JpaIoAutoSignupIntentEntity intent, Session session) {
        if (SIGNUP_ACTIVE.equalsIgnoreCase(intent.getStatus()) && intent.getCompanyId() != null) {
            if (!normalizeText(session.getSubscription()).isBlank()) {
                syncSubscriptionById(intent.getCompanyId(), session.getSubscription(), session.getId());
            }
            return;
        }

        if (!isPaidCheckoutSession(session)) {
            return;
        }

        String normalizedEmail = normalizeEmail(intent.getEmail());
        users.findByEmailGlobal(normalizedEmail).ifPresent(existing -> {
            if (intent.getUserId() == null || !existing.id().equals(intent.getUserId())) {
                throw new BusinessException("SIGNUP_EMAIL_ALREADY_EXISTS", "Já existe uma conta com este e-mail.");
            }
        });

        Subscription subscription = retrieveSubscription(normalizeText(session.getSubscription()));
        Instant now = Instant.now();
        UUID companyId = intent.getCompanyId() != null ? intent.getCompanyId() : UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = intent.getUserId() != null ? intent.getUserId() : UUID.randomUUID();
        LocalDate contractEndDate = toContractEndDate(subscription, now);

        Company company = new Company(
                companyId,
                normalizeText(intent.getCompanyName()),
                null,
                normalizedEmail,
                contractEndDate,
                "",
                LocalDate.now(ZoneOffset.UTC),
                "",
                "",
                "",
                "",
                "09:00",
                "18:00",
                DEFAULT_BUSINESS_HOURS_WEEKLY_JSON,
                now
        );
        companies.save(company);

        teams.save(new Team(
                teamId,
                companyId,
                DEFAULT_TEAM_NAME,
                now,
                now
        ));

        users.save(new User(
                userId,
                companyId,
                normalizedEmail,
                intent.getPasswordHash(),
                normalizeText(intent.getOwnerFullName()),
                null,
                "Administrador",
                null,
                "admin",
                null,
                teamId,
                true,
                now,
                Set.of("ADMIN")
        ));

        ensureDefaultIntegrations(companyId, now);
        syncSubscription(companyId, session, subscription);

        intent.setStatus(SIGNUP_ACTIVE);
        intent.setCheckoutSessionId(normalizeText(session.getId()));
        intent.setProviderCustomerId(normalizeText(session.getCustomer()));
        intent.setProviderSubscriptionId(normalizeText(session.getSubscription()));
        intent.setCompanyId(companyId);
        intent.setUserId(userId);
        intent.setActivatedAt(now);
        intent.setUpdatedAt(now);
        signupIntents.save(intent);
    }

    private void syncSubscriptionById(UUID companyId, String subscriptionId, String checkoutSessionId) {
        if (companyId == null || normalizeText(subscriptionId).isBlank()) {
            return;
        }
        Subscription subscription = retrieveSubscription(subscriptionId);
        syncSubscription(companyId, null, subscription, checkoutSessionId);
    }

    private void syncSubscription(Subscription subscription, String checkoutSessionId) {
        String intentId = normalizeText(subscription.getMetadata() == null ? null : subscription.getMetadata().get("intentId"));
        if (!intentId.isBlank()) {
            signupIntents.findById(UUID.fromString(intentId)).ifPresent(intent -> {
                Session syntheticSession = new Session();
                syntheticSession.setId(checkoutSessionId);
                syntheticSession.setSubscription(subscription.getId());
                syntheticSession.setCustomer(subscription.getCustomer());
                syntheticSession.setStatus("complete");
                syntheticSession.setPaymentStatus("paid");
                syntheticSession.setMetadata(subscription.getMetadata());
                activateIntent(intent, syntheticSession);
            });
            return;
        }

        String providerSubscriptionId = normalizeText(subscription.getId());
        if (providerSubscriptionId.isBlank()) return;

        subscriptions.findByProviderAndProviderSubscriptionId(BILLING_PROVIDER, providerSubscriptionId)
                .ifPresent(existing -> syncSubscription(existing.getCompanyId(), null, subscription, checkoutSessionId));
    }

    private void syncSubscription(UUID companyId, Session session, Subscription subscription) {
        syncSubscription(companyId, session, subscription, session == null ? null : session.getId());
    }

    private void syncSubscription(UUID companyId, Session session, Subscription subscription, String checkoutSessionId) {
        if (companyId == null || subscription == null) return;

        JpaIoAutoBillingSubscriptionEntity entity = subscriptions.findByProviderAndProviderSubscriptionId(BILLING_PROVIDER, subscription.getId())
                .orElseGet(JpaIoAutoBillingSubscriptionEntity::new);

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
        }

        entity.setCompanyId(companyId);
        entity.setProvider(BILLING_PROVIDER);
        entity.setProviderCustomerId(normalizeText(subscription.getCustomer()));
        entity.setProviderSubscriptionId(normalizeText(subscription.getId()));
        entity.setProviderPriceId(firstPriceId(subscription).orElse(stripePriceId));
        entity.setPlanKey(normalizeText(planKey));
        entity.setPlanName(normalizeText(planName));
        entity.setStatus(normalizeText(subscription.getStatus(), "inactive"));
        entity.setAmountCents(firstAmountCents(subscription).orElse(null));
        entity.setCurrency(normalizeText(subscription.getCurrency(), "brl"));
        entity.setBillingInterval(firstBillingInterval(subscription).orElse("month"));
        entity.setCurrentPeriodEnd(resolveCurrentPeriodEnd(subscription));
        entity.setCancelAtPeriodEnd(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        entity.setCheckoutSessionId(normalizeText(checkoutSessionId));
        entity.setUpdatedAt(Instant.now());
        subscriptions.save(entity);

        companies.findById(companyId).ifPresent(company -> companies.save(new Company(
                company.id(),
                company.name(),
                company.profileImageUrl(),
                company.email(),
                toContractEndDate(subscription, Instant.now()),
                company.cnpj(),
                company.openedAt(),
                "",
                "",
                "",
                "",
                company.businessHoursStart(),
                company.businessHoursEnd(),
                company.businessHoursWeeklyJson(),
                company.createdAt()
        )));

        if (session != null) {
            String sessionId = normalizeText(session.getId());
            if (!sessionId.isBlank()) {
                signupIntents.findByCheckoutSessionId(sessionId).ifPresent(intent -> {
                    intent.setProviderCustomerId(normalizeText(subscription.getCustomer()));
                    intent.setProviderSubscriptionId(normalizeText(subscription.getId()));
                    intent.setUpdatedAt(Instant.now());
                    signupIntents.save(intent);
                });
            }
        }
    }

    private Optional<String> firstPriceId(Subscription subscription) {
        return subscription.getItems() == null || subscription.getItems().getData() == null
                ? Optional.empty()
                : subscription.getItems().getData().stream()
                .map(item -> item.getPrice() == null ? null : item.getPrice().getId())
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private Optional<Long> firstAmountCents(Subscription subscription) {
        return subscription.getItems() == null || subscription.getItems().getData() == null
                ? Optional.empty()
                : subscription.getItems().getData().stream()
                .map(item -> item.getPrice() == null ? null : item.getPrice().getUnitAmount())
                .filter(value -> value != null)
                .map(Number::longValue)
                .findFirst();
    }

    private Optional<String> firstBillingInterval(Subscription subscription) {
        return subscription.getItems() == null || subscription.getItems().getData() == null
                ? Optional.empty()
                : subscription.getItems().getData().stream()
                .map(item -> item.getPrice() == null || item.getPrice().getRecurring() == null ? null : item.getPrice().getRecurring().getInterval())
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private Subscription retrieveSubscription(String subscriptionId) {
        if (normalizeText(subscriptionId).isBlank()) {
            return null;
        }
        try {
            Stripe.apiKey = stripeSecretKey;
            return Subscription.retrieve(subscriptionId);
        } catch (StripeException exception) {
            throw new BusinessException("BILLING_SUBSCRIPTION_FETCH_FAILED", "Não foi possível obter os dados da assinatura.");
        }
    }

    private LocalDate toContractEndDate(Subscription subscription, Instant fallbackNow) {
        Instant contractEnd = subscription == null ? fallbackNow.plusSeconds(30L * 24 * 60 * 60) : resolveCurrentPeriodEnd(subscription);
        if (contractEnd == null) {
            contractEnd = fallbackNow.plusSeconds(30L * 24 * 60 * 60);
        }
        return contractEnd.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private Instant resolveCurrentPeriodEnd(Subscription subscription) {
        if (subscription == null || subscription.getItems() == null || subscription.getItems().getData() == null) {
            return null;
        }
        return subscription.getItems().getData().stream()
                .map(item -> item == null ? null : toInstant(item.getCurrentPeriodEnd()))
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private Instant toInstant(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0) return null;
        return Instant.ofEpochSecond(epochSeconds);
    }

    private void ensureDefaultIntegrations(UUID companyId, Instant now) {
        upsertIntegration(companyId, "webmotors", "Webmotors / Estoque e Leads", now);
    }

    private void upsertIntegration(UUID companyId, String providerKey, String displayName, Instant now) {
        JpaIoAutoIntegrationEntity entity = integrations.findByCompanyIdAndProviderKey(companyId, providerKey)
                .orElseGet(JpaIoAutoIntegrationEntity::new);

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCompanyId(companyId);
            entity.setProviderKey(providerKey);
            entity.setCreatedAt(now);
        }
        entity.setDisplayName(displayName);
        entity.setStatus(normalizeText(entity.getStatus(), "CONFIGURATION_REQUIRED"));
        entity.setSettingsJson(normalizeText(entity.getSettingsJson(), "{}"));
        entity.setUpdatedAt(now);
        integrations.save(entity);
    }

    private String normalizePhone(String value) {
        return normalizeText(value).replaceAll("\\D", "");
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeText(value).toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new BusinessException("SIGNUP_INVALID_EMAIL", "Informe um e-mail válido.");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            throw new BusinessException("SIGNUP_INVALID_PAYLOAD", message);
        }
        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeText(String value, String fallback) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}

record PublicSignupPayload(
        String ownerFullName,
        String companyName,
        String email,
        String password
) {
}

record CheckoutLaunch(UUID intentId, String checkoutUrl) {
}

record SignupStatusSnapshot(
        UUID intentId,
        String status,
        String message,
        boolean accessReady,
        String loginEmail,
        String companyName
) {
}

record BillingSnapshot(
        boolean hasSubscription,
        String planName,
        String status,
        Long amountCents,
        String currency,
        String billingInterval,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String provider,
        String providerCustomerId,
        String providerSubscriptionId
) {
}

record PortalLaunch(String portalUrl) {
}
