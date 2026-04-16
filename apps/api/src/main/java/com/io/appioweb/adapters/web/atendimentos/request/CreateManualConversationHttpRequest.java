package com.io.appioweb.adapters.web.atendimentos.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateManualConversationHttpRequest(
        @NotBlank String phone,
        UUID teamId,
        UUID assignedUserId
) {}
