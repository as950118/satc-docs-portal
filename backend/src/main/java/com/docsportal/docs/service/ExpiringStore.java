package com.docsportal.docs.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추측할 수 없는 키로 값을 잠시 맡아 두는 메모리 저장소. take 는 한 번만 성공한다.
 * 재기동하면 비워지므로 진행 중이던 OAuth 흐름은 처음부터 다시 하면 된다.
 */
final class ExpiringStore<T> {

    private record Entry<T>(T value, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Entry<T>> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int capacity;
    private final Clock clock;

    ExpiringStore(Duration ttl, int capacity, Clock clock) {
        this.ttl = ttl;
        this.capacity = capacity;
        this.clock = clock;
    }

    String put(T value) {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
        if (entries.size() >= capacity) {
            throw OAuthException.rateLimited();
        }
        String key = randomKey();
        entries.put(key, new Entry<>(value, now.plus(ttl)));
        return key;
    }

    Optional<T> peek(String key) {
        return Optional.ofNullable(key).map(entries::get).filter(this::live).map(Entry::value);
    }

    Optional<T> take(String key) {
        return Optional.ofNullable(key).map(entries::remove).filter(this::live).map(Entry::value);
    }

    private boolean live(Entry<T> entry) {
        return clock.instant().isBefore(entry.expiresAt());
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
