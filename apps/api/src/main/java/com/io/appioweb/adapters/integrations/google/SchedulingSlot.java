package com.io.appioweb.adapters.integrations.google;

import java.time.Instant;

public record SchedulingSlot(
        Instant start,
        Instant end,
        String label,
        int index
) {
}
