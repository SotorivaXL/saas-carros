package com.io.appioweb.adapters.integrations.webmotors;

public final class WebmotorsPayloadSanitizer {

    private WebmotorsPayloadSanitizer() {
    }

    public static String sanitize(String raw) {
        String value = raw == null ? "" : raw;
        value = value.replaceAll("(?is)<(?:\\w+:)?Senha>.*?</(?:\\w+:)?Senha>", "<Senha>***</Senha>");
        value = value.replaceAll("(?is)<(?:\\w+:)?p?HashAutenticacao>.*?</(?:\\w+:)?p?HashAutenticacao>", "<HashAutenticacao>***</HashAutenticacao>");
        value = value.replaceAll("(?is)<(?:\\w+:)?ClientSecret>.*?</(?:\\w+:)?ClientSecret>", "<ClientSecret>***</ClientSecret>");
        value = value.replaceAll("(?is)<(?:\\w+:)?Password>.*?</(?:\\w+:)?Password>", "<Password>***</Password>");
        value = value.replaceAll("(?i)\"client_secret\"\\s*:\\s*\"[^\"]*\"", "\"client_secret\":\"***\"");
        value = value.replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
        value = value.replaceAll("(?i)\"access_token\"\\s*:\\s*\"[^\"]*\"", "\"access_token\":\"***\"");
        value = value.replaceAll("(?i)(^|[?&])client_secret=[^&]*", "$1client_secret=***");
        value = value.replaceAll("(?i)(^|[?&])password=[^&]*", "$1password=***");
        value = value.replaceAll("(?i)(^|[?&])access_token=[^&]*", "$1access_token=***");
        value = value.replaceAll("(?i)Authorization:\\s*Basic\\s+[A-Za-z0-9+/=._-]+", "Authorization: Basic ***");
        value = value.replaceAll("(?i)Authorization:\\s*Bearer\\s+[A-Za-z0-9+/=._-]+", "Authorization: Bearer ***");
        return value.length() > 20000 ? value.substring(0, 20000) : value;
    }
}
