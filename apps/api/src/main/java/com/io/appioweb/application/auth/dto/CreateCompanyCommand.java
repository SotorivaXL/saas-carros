package com.io.appioweb.application.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCompanyCommand(
        @NotBlank String companyName,
        String profileImageUrl,
        @Email @NotBlank String companyEmail,
        @NotNull LocalDate contractEndDate,
        @NotBlank String cnpj,
        @NotNull LocalDate openedAt,
        @NotBlank String password,
        @NotBlank String businessHoursStart,
        @NotBlank String businessHoursEnd,
        @NotBlank String businessHoursWeeklyJson
) {}
