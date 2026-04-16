package com.io.appioweb.adapters.security;

import com.io.appioweb.application.auth.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptPasswordHasher implements PasswordHasherPort {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Override public String hash(String raw) { return encoder.encode(raw); }
    @Override public boolean matches(String raw, String hashed) { return encoder.matches(raw, hashed); }
}
