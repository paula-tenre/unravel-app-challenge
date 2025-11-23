package com.unravel.challenge.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Manages session data with automatic memory management using Caffeine cache.
 * <p>
 * FIXES:
 * 1. Removed static fields entirely (was causing GC root leak)
 * 2. Instance-based design with proper lifecycle management
 * 3. Caffeine cache with size + TTL limits
 * 4. Thread-safe by default
 * 5. Automatic eviction prevents unbounded growth
 */
@Slf4j
@Service
public class MemoryManager {

    private final Cache<String, byte[]> sessionDataCache;

    public MemoryManager() {
        this.sessionDataCache = Caffeine.newBuilder()
                // Maximum 100 sessions in cache (prevents unbounded growth)
                // low value to be able to test it
                .maximumSize(100)
                // Expire after 1 hour of inactivity (auto-cleanup)
                .expireAfterAccess(1, TimeUnit.HOURS)
                // Log evictions for monitoring
                .removalListener((String key, byte[] value, RemovalCause cause) -> {
                    if (value != null) {
                        long sizeMB = value.length / (1024 * 1024);
                        log.info("Session {} evicted: {} (freed {} MB)", key, cause, sizeMB);
                    }
                })
                // Enable statistics
                .recordStats()
                .build();

        log.info("MemoryManager initialized with Caffeine cache (max: 100, TTL: 1h)");
    }

    /**
     * Add session data
     */
    public void addSessionData(String sessionId) {
        if (sessionId == null) {
            log.warn("Cannot add session data: sessionId is null");
            return;
        }

        sessionDataCache.put(sessionId, new byte[10 * 1024 * 1024]);
        log.debug("Added 10MB session data for: {}", sessionId);
    }

    /**
     * Remove session data
     */
    public void removeSessionData(String sessionId) {
        if (sessionId == null) {
            log.warn("Cannot remove session data: sessionId is null");
            return;
        }

        sessionDataCache.invalidate(sessionId);
        log.debug("Removed session data for: {}", sessionId);
    }

    /**
     * Get session data (if present)
     */
    public byte[] getSessionData(String sessionId) {
        return sessionDataCache.getIfPresent(sessionId);
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return sessionDataCache.stats();
    }

    /**
     * Get current cache size
     */
    public long getCacheSize() {
        return sessionDataCache.estimatedSize();
    }

    /**
     * Print detailed statistics
     */
    public void printStats() {
        CacheStats stats = sessionDataCache.stats();
        log.info("=== Cache Statistics ===");
        log.info("Size: {} sessions", sessionDataCache.estimatedSize());
        log.info("Hit Rate: {}", String.format("%.2f", stats.hitRate() * 100));
        log.info("Hits: {}", stats.hitCount());
        log.info("Misses: {}", stats.missCount());
        log.info("Evictions: {}", stats.evictionCount());
    }
}