package com.docsportal.docs.service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(클라이언트 IP)별 고정 윈도 횟수 제한.
 */
final class RateLimiter {

    private record Window(long startMillis, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Clock clock;

    RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    void check(String key) {
        long now = clock.millis();
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now - w.startMillis() >= windowMillis);
        }
        Window window = windows.compute(key, (k, w) ->
            w == null || now - w.startMillis() >= windowMillis ? new Window(now, 1) : new Window(w.startMillis(), w.count() + 1));
        if (window.count() > limit) {
            throw OAuthException.rateLimited();
        }
    }
}
