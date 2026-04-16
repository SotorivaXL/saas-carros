package com.io.appioweb.adapters.web.atendimentos.response;

public record SendTextHttpResponse(
        java.util.UUID conversationId,
        String zaapId,
        String messageId,
        String id
) {}
