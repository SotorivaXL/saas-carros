package com.io.appioweb.application.auth.usecase;

import com.io.appioweb.application.auth.dto.AuthTokens;
import com.io.appioweb.application.auth.dto.LoginCommand;
import com.io.appioweb.application.auth.port.in.AuthUseCase;
import com.io.appioweb.application.auth.port.out.PasswordHasherPort;
import com.io.appioweb.application.auth.port.out.TokenServicePort;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import com.io.appioweb.domain.auth.entity.User;
import com.io.appioweb.shared.errors.BusinessException;

import java.util.Optional;

public class LoginUseCase implements AuthUseCase {
    private final UserRepositoryPort users;
    private final PasswordHasherPort hasher;
    private final TokenServicePort tokens;

    public LoginUseCase(UserRepositoryPort users, PasswordHasherPort hasher, TokenServicePort tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public AuthTokens login(LoginCommand command) {
        // Multiempresa mínimo: se existir email em apenas um tenant, loga.
        Optional<User> opt = users.findByEmailGlobal(command.email());
        User user = opt.orElseThrow(() -> new BusinessException("AUTH_INVALID", "Credenciais inválidas"));

        if (!user.isActive()) throw new BusinessException("AUTH_INACTIVE", "Usuário inativo");
        if (!hasher.matches(command.password(), user.passwordHash()))
            throw new BusinessException("AUTH_INVALID", "Credenciais inválidas");

        return tokens.issueTokens(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) tokens.revokeRefresh(refreshToken);
        if (accessToken != null && !accessToken.isBlank()) tokens.blacklistAccess(accessToken);
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        return tokens.rotateRefresh(refreshToken);
    }
}
