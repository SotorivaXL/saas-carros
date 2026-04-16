package com.io.appioweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.*;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private final List<String> appAllowedOrigins;

    public CorsConfig(@Value("${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}") String configuredOrigins) {
        List<String> parsed = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        this.appAllowedOrigins = parsed.isEmpty() ? List.of("http://localhost:3000") : parsed;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Webhook pode chegar de origens variaveis (provedores/tuneis), sem cookies.
        CorsConfiguration webhookConfig = new CorsConfiguration();
        webhookConfig.setAllowedOriginPatterns(List.of("*"));
        webhookConfig.setAllowedMethods(List.of("POST", "OPTIONS"));
        webhookConfig.setAllowedHeaders(List.of("*"));
        webhookConfig.setAllowCredentials(false);
        source.registerCorsConfiguration("/webhooks/**", webhookConfig);

        CorsConfiguration appConfig = new CorsConfiguration();
        appConfig.setAllowedOrigins(appAllowedOrigins);
        appConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        appConfig.setAllowedHeaders(List.of("*"));
        appConfig.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", appConfig);

        return source;
    }
}
