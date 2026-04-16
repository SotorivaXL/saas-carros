package com.io.appioweb.adapters.web.auth.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTeamHttpRequest(
        @NotBlank String name
) {}
