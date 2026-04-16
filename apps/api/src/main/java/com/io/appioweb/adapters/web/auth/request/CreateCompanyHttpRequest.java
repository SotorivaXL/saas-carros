package com.io.appioweb.adapters.web.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

public record CreateCompanyHttpRequest(
        @NotBlank String companyName,
        String profileImageUrl,
        @Email @NotBlank String companyEmail,
        @NotNull LocalDate contractEndDate,
        @NotBlank String cnpj,
        @NotNull LocalDate openedAt,
        @NotBlank String password,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Horario inicial invalido (HH:mm)")
        @NotBlank String businessHoursStart,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Horario final invalido (HH:mm)")
        @NotBlank String businessHoursEnd,
        @NotNull JsonNode businessHoursWeekly
) {}
