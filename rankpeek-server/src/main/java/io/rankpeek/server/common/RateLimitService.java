package io.rankpeek.server.common;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class RateLimitService {

    private final Map<String, Window> windows = new HashMap<>();

    public synchronized RateLimitDecision consume(String bucket, String subject, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1_000L, window.toMillis());
        String key = bucket + ":" + subject;
        Window current = windows.get(key);
        if (current == null || now >= current.resetAtMillis()) {
            windows.put(key, new Window(1, now + windowMillis));
            cleanupExpired(now);
            return new RateLimitDecision(true, window.getSeconds());
        }
        if (current.count() >= maxRequests) {
            return new RateLimitDecision(false, retryAfterSeconds(current.resetAtMillis(), now));
        }
        windows.put(key, new Window(current.count() + 1, current.resetAtMillis()));
        return new RateLimitDecision(true, retryAfterSeconds(current.resetAtMillis(), now));
    }

    private void cleanupExpired(long now) {
        if (windows.size() < 1_000) {
            return;
        }
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().getValue().resetAtMillis()) {
                iterator.remove();
            }
        }
    }

    private static long retryAfterSeconds(long resetAtMillis, long nowMillis) {
        long remainingMillis = Math.max(1L, resetAtMillis - nowMillis);
        return Math.max(1L, (remainingMillis + 999L) / 1_000L);
    }

    private record Window(int count, long resetAtMillis) {
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}
