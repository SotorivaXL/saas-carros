package com.io.appioweb.adapters.web.aisupervisors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AiSupervisorSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{8,15}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private AiSupervisorSupport() {
    }

    static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    static String trimToNull(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isBlank() ? null : trimmed;
    }

    static String truncate(String value, int max) {
        String text = safeTrim(value);
        if (max <= 0 || text.length() <= max) return text;
        return text.substring(0, Math.max(0, max));
    }

    static String normalize(String value) {
        String base = safeTrim(value).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return List.of();
        String[] pieces = NON_WORD.split(normalized);
        Set<String> tokens = new LinkedHashSet<>();
        for (String piece : pieces) {
            String token = safeTrim(piece);
            if (token.length() >= 2) tokens.add(token);
        }
        return List.copyOf(tokens);
    }

    static String maskPii(String value) {
        String masked = value == null ? "" : value;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("***@***");
        masked = URL_PATTERN.matcher(masked).replaceAll("[url]");
        Matcher matcher = PHONE_PATTERN.matcher(masked);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = match.length() <= 4
                    ? "****"
                    : match.substring(0, 2) + "****" + match.substring(match.length() - 2);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static JsonNode parseJson(String raw, String fallbackJson) {
        try {
            return OBJECT_MAPPER.readTree(raw == null || raw.isBlank() ? fallbackJson : raw);
        } catch (Exception ignored) {
            try {
                return OBJECT_MAPPER.readTree(fallbackJson);
            } catch (Exception impossible) {
                return OBJECT_MAPPER.createObjectNode();
            }
        }
    }

    static String toJson(JsonNode node, String fallback) {
        try {
            return node == null || node.isNull() ? fallback : OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static String toJsonArray(List<String> values) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        if (values != null) {
            for (String value : values) {
                String normalized = safeTrim(value);
                if (!normalized.isBlank()) array.add(normalized);
            }
        }
        return toJson(array, "[]");
    }

    static List<String> parseStringArray(String raw) {
        JsonNode node = parseJson(raw, "[]");
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = safeTrim(item.asText(""));
            if (!value.isBlank()) values.add(value);
        }
        return List.copyOf(values);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }
}
