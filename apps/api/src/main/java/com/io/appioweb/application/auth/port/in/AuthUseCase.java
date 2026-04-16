package com.io.appioweb.application.auth.port.in;

import com.io.appioweb.application.auth.dto.AuthTokens;
import com.io.appioweb.application.auth.dto.LoginCommand;

public interface AuthUseCase {
    AuthTokens login(LoginCommand command);
    void logout(String accessToken, String refreshToken);
    AuthTokens refresh(String refreshToken);
}
