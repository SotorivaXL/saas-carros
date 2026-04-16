package com.io.appioweb.domain.ioauto.webmotors;

public record WebmotorsFeatureFlags(
        boolean soapAdsEnabled,
        boolean restLeadsEnabled,
        boolean catalogSyncEnabled,
        boolean leadPullEnabled,
        boolean callbackEnabled
) {
}
