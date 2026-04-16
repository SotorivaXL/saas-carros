package com.io.appioweb.adapters.integrations.webmotors.soap;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebmotorsSoapSessionCache {

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public String get(UUID companyId, String storeKey) {
        Entry entry = sessions.get(key(companyId, storeKey));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            sessions.remove(key(companyId, storeKey));
            return null;
        }
        return entry.hash();
    }

    public void put(UUID companyId, String storeKey, String hash, Instant expiresAt) {
        sessions.put(key(companyId, storeKey), new Entry(hash, expiresAt));
    }

    public void invalidate(UUID companyId, String storeKey) {
        sessions.remove(key(companyId, storeKey));
    }

    private String key(UUID companyId, String storeKey) {
        return companyId + "::" + (storeKey == null ? "default" : storeKey.trim().toLowerCase());
    }

    private record Entry(String hash, Instant expiresAt) {
    }
}
