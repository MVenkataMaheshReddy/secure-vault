package com.securevault.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service to protect against login brute-force attacks.
 * Tracks failed login attempts per email + IP address.
 * Throttles/locks out after MAX_ATTEMPTS failed attempts within the window.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final int maxAttempts;
    private final long lockoutDurationMs;

    private static class AttemptRecord {
        int failedAttempts;
        long lastAttemptTimestamp;
        long lockedUntilTimestamp;

        AttemptRecord(long now) {
            this.failedAttempts = 1;
            this.lastAttemptTimestamp = now;
            this.lockedUntilTimestamp = 0;
        }
    }

    private final Map<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${securevault.security.max-login-attempts:5}") int maxAttempts,
            @Value("${securevault.security.lockout-duration-minutes:15}") int lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationMs = TimeUnit.MINUTES.toMillis(lockoutMinutes);
    }

    private String buildKey(String email, String ip) {
        String safeEmail = (email != null) ? email.trim().toLowerCase() : "unknown";
        String safeIp = (ip != null) ? ip.trim() : "unknown";
        return safeEmail + ":" + safeIp;
    }

    public boolean isBlocked(String email, String ip) {
        String key = buildKey(email, ip);
        AttemptRecord record = attemptsCache.get(key);
        if (record == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Check if currently locked out
        if (record.lockedUntilTimestamp > now) {
            return true;
        }

        // If lockout duration has passed, reset the counter
        if (record.lockedUntilTimestamp > 0 && record.lockedUntilTimestamp <= now) {
            attemptsCache.remove(key);
            return false;
        }

        // If last failed attempt was outside the lockout window, reset
        if (now - record.lastAttemptTimestamp > lockoutDurationMs) {
            attemptsCache.remove(key);
            return false;
        }

        return record.failedAttempts >= maxAttempts;
    }

    public void loginFailed(String email, String ip) {
        String key = buildKey(email, ip);
        long now = System.currentTimeMillis();

        attemptsCache.compute(key, (k, record) -> {
            if (record == null || (now - record.lastAttemptTimestamp > lockoutDurationMs && record.lockedUntilTimestamp <= now)) {
                return new AttemptRecord(now);
            }

            record.failedAttempts++;
            record.lastAttemptTimestamp = now;

            if (record.failedAttempts >= maxAttempts && record.lockedUntilTimestamp <= now) {
                record.lockedUntilTimestamp = now + lockoutDurationMs;
                log.warn("Brute-force lockout triggered for key [{}]. Locked until: {}", key, record.lockedUntilTimestamp);
            }

            return record;
        });
    }

    public void loginSucceeded(String email, String ip) {
        String key = buildKey(email, ip);
        attemptsCache.remove(key);
    }

    public int getRemainingAttempts(String email, String ip) {
        String key = buildKey(email, ip);
        AttemptRecord record = attemptsCache.get(key);
        if (record == null) {
            return maxAttempts;
        }
        int remaining = maxAttempts - record.failedAttempts;
        return Math.max(0, remaining);
    }

    public void reset(String email, String ip) {
        attemptsCache.remove(buildKey(email, ip));
    }
}
