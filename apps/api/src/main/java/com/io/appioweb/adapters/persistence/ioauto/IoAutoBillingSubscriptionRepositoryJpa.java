package com.io.appioweb.adapters.persistence.ioauto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IoAutoBillingSubscriptionRepositoryJpa extends JpaRepository<JpaIoAutoBillingSubscriptionEntity, UUID> {
    Optional<JpaIoAutoBillingSubscriptionEntity> findTopByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
    Optional<JpaIoAutoBillingSubscriptionEntity> findByProviderAndProviderSubscriptionId(String provider, String providerSubscriptionId);
}
