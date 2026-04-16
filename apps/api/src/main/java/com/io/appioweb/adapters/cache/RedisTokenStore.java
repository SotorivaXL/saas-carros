package com.io.appioweb.adapters.cache;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisTokenStore {

    private static final String PREFIX_REFRESH = "auth:refresh:";
    private static final String PREFIX_BLACKLIST = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void storeRefresh(String jti, String payload, Duration ttl) {
        redis.opsForValue().set(PREFIX_REFRESH + jti, payload, ttl);
    }

    public String getRefresh(String jti) {
        return redis.opsForValue().get(PREFIX_REFRESH + jti);
    }

    public void deleteRefresh(String jti) {
        redis.delete(PREFIX_REFRESH + jti);
    }

    public void blacklistAccess(String jti, Duration ttl) {
        redis.opsForValue().set(PREFIX_BLACKLIST + jti, "1", ttl);
    }

    public boolean isAccessBlacklisted(String jti) {
        Boolean exists = redis.hasKey(PREFIX_BLACKLIST + jti);
        return Boolean.TRUE.equals(exists);
    }
}
