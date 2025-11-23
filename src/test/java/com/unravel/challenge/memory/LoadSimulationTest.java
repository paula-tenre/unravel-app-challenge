package com.unravel.challenge.memory;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test simulates production load to reproduce the memory leak
 * and ensure that it's fixed.
 */
@Slf4j
public class LoadSimulationTest {

    @Test
    void simulateProductionLoad() {
        log.info("Starting load simulation...");

        // Create instance of MemoryManager (no longer static)
        MemoryManager memoryManager = new MemoryManager();

        // Simulate 200 sessions being created
        for (int i = 0; i < 200; i++) {
            String sessionId = "SESSION_" + i;

            // Add session data (10 MB each)
            memoryManager.addSessionData(sessionId);
            log.debug("Added session data for: {}", sessionId);

            // Simulate that only 20% are properly cleaned up (users properly logging out)
            if (i % 5 == 0) {
                memoryManager.removeSessionData(sessionId);
                log.debug("Removed session data for: {}", sessionId);
            }

            // Print stats every 50 iterations
            if (i % 50 == 0) {
                printMemoryUsage();
                log.info("Cache size: {} sessions", memoryManager.getCacheSize());
            }

            // Small delay to simulate real usage
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Load simulation complete");
        printMemoryUsage();

        // Print final cache statistics
        memoryManager.printStats();

        log.info("Final cache size: {} sessions", memoryManager.getCacheSize());

        // Keep JVM alive for profiling
        log.info("Waiting 30 seconds for profiling...");
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testCacheEvictionAndHits() {
        MemoryManager memoryManager = new MemoryManager();

        // 1. Add sessions beyond limit (triggers size-based eviction)
        for (int i = 0; i < 200; i++) {  // Exceeds max of 100
            memoryManager.addSessionData("session-" + i);
        }

        // 2. Access some sessions (creates hits/misses)
        memoryManager.getSessionData("session-150");  // Should be a HIT
        memoryManager.getSessionData("session-50");    // Should be a MISS (evicted)

        // 3. Print stats
        memoryManager.printStats();

        CacheStats stats = memoryManager.getStats();

        // Now you should see:
        assertTrue(stats.evictionCount() > 0);  // ~100 evictions
        assertTrue(stats.hitCount() > 0);       // At least 1 hit
        assertTrue(stats.missCount() > 0);      // At least 1 miss
    }

    private void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        log.info("Memory: {} MB / {} MB ({}%)",
                usedMemory,
                maxMemory,
                (usedMemory * 100) / maxMemory);
    }
}