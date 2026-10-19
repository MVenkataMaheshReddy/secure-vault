package com.securevault.util;

import com.securevault.exception.RateLimitExceededException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> requestCounts = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(@Value("${securevault.verify.rate-limit-per-min:30}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public synchronized boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            clientIp = "UNKNOWN";
        }

        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L; // 1 minute window

        Deque<Long> timestamps = requestCounts.computeIfAbsent(clientIp, k -> new ArrayDeque<>());

        // Evict expired timestamps
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequestsPerMinute) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public void checkLimit(String clientIp) {
        if (!tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Too many requests from this IP. Please try again in a minute.");
        }
    }

    public void reset() {
        requestCounts.clear();
    }
}
