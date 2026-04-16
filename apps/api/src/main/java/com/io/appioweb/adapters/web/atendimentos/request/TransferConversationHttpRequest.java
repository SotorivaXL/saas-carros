package com.io.appioweb.adapters.web.atendimentos.request;

import java.util.UUID;

public record TransferConversationHttpRequest(
        UUID teamId,
        UUID targetUserId
) {}
