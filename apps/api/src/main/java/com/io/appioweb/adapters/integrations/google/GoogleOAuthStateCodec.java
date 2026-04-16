package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class GoogleOAuthStateCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final SensitiveDataCrypto crypto;
    private final Clock clock;

    @Autowired
    public GoogleOAuthStateCodec(SensitiveDataCrypto crypto) {
        this(crypto, Clock.systemUTC());
    }

    GoogleOAuthStateCodec(SensitiveDataCrypto crypto, Clock clock) {
        this.crypto = crypto;
        this.clock = clock;
    }

    public String encode(UUID companyId) {
        if (companyId == null) {
            throw new BusinessException("GOOGLE_OAUTH_STATE_INVALID", "Nao foi possivel iniciar a conexao Google");
        }
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("companyId", companyId.toString());
        root.put("nonce", UUID.randomUUID().toString());
        root.put("issuedAt", clock.instant().toString());
        String encrypted = crypto.encrypt(toJson(root));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.getBytes(StandardCharsets.UTF_8));
    }

    public StatePayload decode(String state) {
        String raw = state == null ? "" : state.trim();
        if (raw.isBlank()) {
            throw new BusinessException("GOOGLE_OAUTH_STATE_INVALID", "State do OAuth Google ausente");
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(raw);
            String decrypted = crypto.decrypt(new String(packed, StandardCharsets.UTF_8));
            JsonNode root = OBJECT_MAPPER.readTree(decrypted);
            UUID companyId = UUID.fromString(root.path("companyId").asText(""));
            String nonce = root.path("nonce").asText("").trim();
            Instant issuedAt = Instant.parse(root.path("issuedAt").asText(""));
            if (nonce.isBlank()) {
                throw new BusinessException("GOOGLE_OAUTH_STATE_INVALID", "State do OAuth Google invalido");
            }
            Instant now = clock.instant();
            if (issuedAt.isBefore(now.minus(STATE_TTL)) || issuedAt.isAfter(now.plusSeconds(30))) {
                throw new BusinessException("GOOGLE_OAUTH_STATE_EXPIRED", "State do OAuth Google expirado. Reinicie a conexao");
            }
            return new StatePayload(companyId, nonce, issuedAt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("GOOGLE_OAUTH_STATE_INVALID", "State do OAuth Google invalido");
        }
    }

    private String toJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BusinessException("GOOGLE_OAUTH_STATE_INVALID", "Nao foi possivel gerar state do OAuth Google");
        }
    }

    public record StatePayload(UUID companyId, String nonce, Instant issuedAt) { }
}
