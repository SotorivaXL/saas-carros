package com.io.appioweb.adapters.web.atendimentos.response;

import java.time.Instant;
import java.util.UUID;

public record MessageHttpResponse(
        UUID id,
        UUID conversationId,
        String phone,
        String text,
        String type,
        String imageUrl,
        String stickerUrl,
        String videoUrl,
        String audioUrl,
        String documentUrl,
        String documentName,
        boolean fromMe,
        String status,
        String messageId,
        Long moment,
        Instant createdAt
) {}
