package com.io.appioweb.application.auth.usecase;

import com.io.appioweb.application.auth.dto.CreateCompanyCommand;
import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import com.io.appioweb.application.auth.port.out.PasswordHasherPort;
import com.io.appioweb.application.auth.port.out.RoleRepositoryPort;
import com.io.appioweb.application.auth.port.out.TeamRepositoryPort;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import com.io.appioweb.domain.auth.entity.Company;
import com.io.appioweb.domain.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCompanyUseCaseTest {

    @Mock
    private CompanyRepositoryPort companies;

    @Mock
    private UserRepositoryPort users;

    @Mock
    private RoleRepositoryPort roles;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private TeamRepositoryPort teams;

    @InjectMocks
    private CreateCompanyUseCase useCase;

    @Test
    void createCompanyCopiesCompanyProfileImageToInitialOwner() {
        CreateCompanyCommand command = new CreateCompanyCommand(
                "Empresa Teste",
                "data:image/png;base64,abc123",
                "contato@empresa.com",
                LocalDate.parse("2027-12-31"),
                "12.345.678/0001-90",
                LocalDate.parse("2024-01-15"),
                "senha-forte",
                "08:00",
                "18:00",
                "{\"monday\":{\"active\":true,\"start\":\"08:00\",\"lunchStart\":\"12:00\",\"lunchEnd\":\"13:00\",\"end\":\"18:00\"}}"
        );

        when(roles.existsByName("ADMIN")).thenReturn(true);
        when(companies.findByEmail("contato@empresa.com")).thenReturn(Optional.empty());
        when(users.findByEmailGlobal("contato@empresa.com")).thenReturn(Optional.empty());
        when(hasher.hash("senha-forte")).thenReturn("hash-gerado");

        useCase.createCompany(command);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(companies).save(companyCaptor.capture());
        verify(users).save(userCaptor.capture());

        assertThat(companyCaptor.getValue().profileImageUrl()).isEqualTo(command.profileImageUrl());
        assertThat(userCaptor.getValue().profileImageUrl()).isEqualTo(command.profileImageUrl());
        assertThat(userCaptor.getValue().jobTitle()).isEqualTo("Administrador");
    }
}
