package com.io.appioweb.application.auth.port.in;

import com.io.appioweb.application.auth.dto.CreateCompanyCommand;
import com.io.appioweb.application.auth.dto.CreateCompanyResult;

public interface CompanyAdminUseCase {
    CreateCompanyResult createCompany(CreateCompanyCommand command);
}
