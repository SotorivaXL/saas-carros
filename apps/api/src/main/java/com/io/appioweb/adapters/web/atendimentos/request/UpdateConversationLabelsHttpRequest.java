package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.Valid;

import java.util.List;

public record UpdateConversationLabelsHttpRequest(
        @Valid List<ConversationLabelHttpRequest> labels
) {
}
