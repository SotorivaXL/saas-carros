package com.io.appioweb.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(
        String type,
        UUID companyId,
        UUID conversationId,
        Instant at
) {
}
