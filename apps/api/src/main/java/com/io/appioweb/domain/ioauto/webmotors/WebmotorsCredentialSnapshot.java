package com.io.appioweb.domain.ioauto.webmotors;

import java.util.UUID;

public record WebmotorsCredentialSnapshot(
        UUID id,
        UUID companyId,
        String storeKey,
        String storeName,
        WebmotorsFeatureFlags featureFlags,
        String soapBaseUrl,
        String soapAuthPath,
        String soapInventoryPath,
        String soapCatalogPath,
        String soapCnpj,
        String soapEmail,
        String soapPassword,
        String restTokenUrl,
        String restApiBaseUrl,
        String restUsername,
        String restPassword,
        String restClientId,
        String restClientSecret,
        String callbackSecret
) {
}
