package com.io.appioweb.adapters.web.atendimentos.response;

import java.util.UUID;

public record AtendimentoUserHttpResponse(
        UUID id,
        String fullName,
        String email,
        UUID teamId,
        String teamName
) {}
