package com.io.appioweb.adapters.security;

import com.io.appioweb.adapters.cache.RedisTokenStore;
import com.io.appioweb.application.auth.dto.AuthTokens;
import com.io.appioweb.application.auth.port.out.TokenServicePort;
import com.io.appioweb.domain.auth.entity.User;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class JwtTokenService implements TokenServicePort {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final RedisTokenStore store;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenService(JwtEncoder encoder, JwtDecoder decoder, RedisTokenStore store,
                           String issuer, Duration accessTtl, Duration refreshTtl) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.store = store;
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Override
    public AuthTokens issueTokens(User user) {
        Instant now = Instant.now();

        String accessJti = UUID.randomUUID().toString();
        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .subject(user.id().toString())
                .id(accessJti)
                .claim("cid", user.companyId().toString())
                .claim("roles", user.roles().stream().toList())
                .build();

        String refreshJti = UUID.randomUUID().toString();
        JwtClaimsSet refreshClaims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(refreshTtl))
                .subject(user.id().toString())
                .id(refreshJti)
                .claim("cid", user.companyId().toString())
                .claim("type", "refresh")
                .claim("roles", user.roles().stream().toList())
                .build();

        var header = JwsHeader.with(MacAlgorithm.HS256)
                .keyId("io-hs256")
                .build();

        String access = encoder.encode(JwtEncoderParameters.from(header, accessClaims)).getTokenValue();
        String refresh = encoder.encode(JwtEncoderParameters.from(header, refreshClaims)).getTokenValue();

        // guarda refresh no redis (payload mínimo)
        store.storeRefresh(refreshJti,
                user.id() + "|" + user.companyId(),
                refreshTtl
        );

        return new AuthTokens(access, refresh, accessTtl.toSeconds());
    }

    @Override
    public AuthTokens rotateRefresh(String refreshToken) {
        Jwt jwt = decodeSafe(refreshToken);

        if (!"refresh".equals(jwt.getClaimAsString("type")))
            throw new BusinessException("AUTH_INVALID", "Refresh inválido");

        String jti = jwt.getId();
        String saved = store.getRefresh(jti);
        if (saved == null) throw new BusinessException("AUTH_INVALID", "Refresh expirado ou revogado");

        store.deleteRefresh(jti);

        UUID userId = UUID.fromString(jwt.getSubject());
        UUID companyId = UUID.fromString(jwt.getClaimAsString("cid"));

        var roles = jwt.getClaimAsStringList("roles");
        User user = new User(
                userId,
                companyId,
                "n/a",
                "n/a",
                "n/a",
                null,
                null,
                null,
                null,
                java.util.Collections.emptySet(),
                null,
                true,
                Instant.now(),
                new java.util.HashSet<>(roles)
        );

        return issueTokens(user);
    }

    @Override
    public void revokeRefresh(String refreshToken) {
        Jwt jwt = decodeSafe(refreshToken);
        store.deleteRefresh(jwt.getId());
    }

    @Override
    public void blacklistAccess(String accessToken) {
        Jwt jwt = decodeSafe(accessToken);

        String jti = jwt.getId();
        Instant exp = jwt.getExpiresAt();

        if (jti == null || jti.isBlank() || exp == null) return; // não explode

        Duration ttl = Duration.between(Instant.now(), exp);
        if (!ttl.isNegative() && !ttl.isZero()) {
            store.blacklistAccess(jti, ttl);
        }
    }

    @Override
    public boolean isAccessBlacklisted(String jti) {
        return store.isAccessBlacklisted(jti);
    }

    private Jwt decodeSafe(String token) {
        try {
            return decoder.decode(token);
        } catch (Exception e) {
            throw new BusinessException("AUTH_INVALID", "Token inválido");
        }
    }
}
