package com.io.appioweb.application.ioauto.webmotors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebmotorsOutboxProcessor {

    private final WebmotorsAdsService adsService;

    public WebmotorsOutboxProcessor(WebmotorsAdsService adsService) {
        this.adsService = adsService;
    }

    @Scheduled(fixedDelayString = "${WEBMOTORS_OUTBOX_POLL_MS:15000}")
    public void processPendingJobs() {
        adsService.processPendingJobs();
    }
}
