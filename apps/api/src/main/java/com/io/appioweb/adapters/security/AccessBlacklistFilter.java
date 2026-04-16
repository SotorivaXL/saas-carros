package com.io.appioweb.adapters.security;

import com.io.appioweb.application.auth.port.out.TokenServicePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AccessBlacklistFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final TokenServicePort tokens;

    public AccessBlacklistFilter(JwtDecoder decoder, TokenServicePort tokens) {
        this.decoder = decoder;
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Jwt jwt = decoder.decode(token);
                String jti = jwt.getId();
                if (jti != null && tokens.isAccessBlacklisted(jti)) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"AUTH_REVOKED\",\"message\":\"Token revogado\"}");
                    return;
                }
            } catch (Exception ignored) { /* deixa o resource server tratar */ }
        }

        filterChain.doFilter(request, response);
    }
}
