package com.io.appioweb.application.auth.port.in;

import com.io.appioweb.application.auth.dto.CreateUserCommand;
import com.io.appioweb.application.auth.dto.MeResponse;

public interface UserAdminUseCase {
    void createUser(CreateUserCommand command);
    MeResponse me();
}
