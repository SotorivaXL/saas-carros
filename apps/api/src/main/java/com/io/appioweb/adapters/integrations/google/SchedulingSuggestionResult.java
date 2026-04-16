package com.io.appioweb.adapters.integrations.google;

import java.time.Instant;
import java.util.List;

public record SchedulingSuggestionResult(
        String timeZone,
        List<SchedulingSlot> slots,
        Instant generatedAt,
        Instant expiresAt
) {
}
