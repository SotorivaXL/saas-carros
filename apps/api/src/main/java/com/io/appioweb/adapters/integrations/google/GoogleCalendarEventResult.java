package com.io.appioweb.adapters.integrations.google;

public record GoogleCalendarEventResult(
        String eventId,
        String htmlLink,
        String meetLink
) {
}
