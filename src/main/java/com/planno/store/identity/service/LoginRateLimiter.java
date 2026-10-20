package com.planno.store.identity.service;

import com.planno.store.core.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class LoginRateLimiter {
    private final Map<String, Window> windows = new HashMap<>();
    private static final int MAX_KEYS = 10_000;
    private static final long WINDOW_SECONDS = 900;

    public synchronized void check(String ip, String email) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !entry.getValue().until.isAfter(now));
        String ipKey = key("ip:" + ip);
        String accountKey = key("account:" + email.trim().toLowerCase(Locale.ROOT));
        int newKeys = (windows.containsKey(ipKey) ? 0 : 1) + (windows.containsKey(accountKey) ? 0 : 1);
        if (windows.size() + newKeys > MAX_KEYS || exhausted(ipKey, 30) || exhausted(accountKey, 10)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts; try again later");
        }
        consume(ipKey, now);
        consume(accountKey, now);
    }
    private boolean exhausted(String key, int limit) {
        Window window = windows.get(key);
        return window != null && window.attempts >= limit;
    }
    private void consume(String key, Instant now) {
        Window previous = windows.get(key);
        windows.put(key, previous == null ? new Window(now.plusSeconds(WINDOW_SECONDS), 1)
                : new Window(previous.until, previous.attempts + 1));
    }
    private static String key(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Window(Instant until, int attempts) {}
}
