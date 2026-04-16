package com.io.appioweb.application.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginCommand(
        @Email @NotBlank String email,
        @NotBlank String password
) {}