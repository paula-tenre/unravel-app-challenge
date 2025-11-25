package com.unravel.challenge.database;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test to reproduce connection pool issues under high concurrency.
 * <p>
 * This test simulates the problems mentioned in the challenge:
 * - High concurrency causing connection waits
 * - Pool exhaustion during peak loads
 */
@Slf4j
@SpringBootTest
class ConnectionPoolLoadTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestDataRepository repository;

    /**
     * Test 1: Verify basic connection works
     */
    @Test
    void testBasicConnection() throws SQLException {
        log.info("TEST 1: Basic Connection Test");

        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            log.info("✅ Basic connection successful");
        }

        int count = repository.countRecords();
        log.info("✅ Database has {} records", count);
        assertTrue(count > 0, "Database should have test data");
    }

    /**
     * Test 2: Simulate moderate load (50 concurrent requests)
     * This should work with pool size 100, but let's measure performance
     */
    @Test
    void testModerateLoad() throws InterruptedException {
        log.info("TEST 2: Moderate Load (50 concurrent requests)");

        int concurrentRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> responseTimes = new CopyOnWriteArrayList<>();

        long testStart = System.currentTimeMillis();

        // Submit all requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();  // Wait for signal

                    long start = System.currentTimeMillis();

                    // Execute query
                    List<String> data = repository.getAllData();

                    long duration = System.currentTimeMillis() - start;
                    responseTimes.add(duration);

                    if (!data.isEmpty()) {
                        successCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Request {} failed: {}", requestId, e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all at once
        startLatch.countDown();

        // Wait for completion
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        long totalDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();

        // Calculate statistics
        responseTimes.sort(Long::compareTo);
        long avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        long p95ResponseTime = responseTimes.get((int) (responseTimes.size() * 0.95));
        long p99ResponseTime = responseTimes.get((int) (responseTimes.size() * 0.99));

        log.info("=== MODERATE LOAD TEST RESULTS ===");
        log.info("Total requests: {}", concurrentRequests);
        log.info("Successful: {}", successCount.get());
        log.info("Failed: {}", failureCount.get());
        log.info("Total duration: {}ms", totalDuration);
        log.info("Avg response time: {}ms", avgResponseTime);
        log.info("P95 response time: {}ms", p95ResponseTime);
        log.info("P99 response time: {}ms", p99ResponseTime);

        assertTrue(completed, "Test should complete within timeout");
        assertEquals(concurrentRequests, successCount.get(), "All requests should succeed");
    }

    /**
     * Test 3: Simulate high load with slow queries (Connection held for 2 seconds)
     * This SHOULD expose pool exhaustion issues
     */
    @Test
    void testHighLoadWithSlowQueries() throws InterruptedException {
        log.info("TEST 3: High Load with Slow Queries (simulating pool exhaustion)");

        int concurrentRequests = 150;  // More than pool size (100)
        long queryDelayMs = 2000;       // Hold connection for 2 seconds

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        List<Long> responseTimes = new CopyOnWriteArrayList<>();

        long testStart = System.currentTimeMillis();

        // Submit all requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long start = System.currentTimeMillis();

                    // Execute slow query (holds connection for 2 seconds)
                    List<String> data = repository.getDataWithDelay(queryDelayMs);

                    long duration = System.currentTimeMillis() - start;
                    responseTimes.add(duration);

                    successCount.incrementAndGet();

                } catch (SQLException e) {
                    failureCount.incrementAndGet();

                    if (e.getMessage().contains("timeout")) {
                        timeoutCount.incrementAndGet();
                        log.warn("Request {} timed out waiting for connection", requestId);
                    } else {
                        log.error("Request {} failed: {}", requestId, e.getMessage());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Request {} failed: {}", requestId, e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all at once
        startLatch.countDown();

        // Wait for completion (allow extra time for timeouts)
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        long totalDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();

        // Calculate statistics
        if (!responseTimes.isEmpty()) {
            responseTimes.sort(Long::compareTo);
            long avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
            long p95ResponseTime = responseTimes.get((int) (responseTimes.size() * 0.95));
            long p99ResponseTime = responseTimes.get((int) (responseTimes.size() * 0.99));

            log.info("=== HIGH LOAD TEST RESULTS ===");
            log.info("Total requests: {}", concurrentRequests);
            log.info("Successful: {}", successCount.get());
            log.info("Failed: {} (timeouts: {})", failureCount.get(), timeoutCount.get());
            log.info("Total duration: {}ms", totalDuration);
            log.info("Avg response time: {}ms", avgResponseTime);
            log.info("P95 response time: {}ms", p95ResponseTime);
            log.info("P99 response time: {}ms", p99ResponseTime);
        }

        assertTrue(completed, "Test should complete within timeout");

        // We EXPECT failures here - this demonstrates the problem
        if (failureCount.get() > 0) {
            log.warn("⚠️ CONNECTION POOL EXHAUSTION DETECTED!");
            log.warn("⚠️ {} requests failed due to pool limits", failureCount.get());
            log.warn("⚠️ This demonstrates the problem we need to fix");
        }
    }

    /**
     * Test 4: Simulate connection leak scenario
     * (This would be the bug if we didn't use try-with-resources)
     */
    @Test
    void testConnectionLeakScenario() throws SQLException {
        log.info("TEST 4: Connection Leak Scenario (demonstrating the old bug)");

        log.info("⚠️ This test demonstrates what happens with connection leaks");
        log.info("⚠️ We'll intentionally NOT return connections to the pool");

        int leakCount = 10;
        List<Connection> leakedConnections = new ArrayList<>();

        try {
            // Intentionally leak connections
            for (int i = 0; i < leakCount; i++) {
                Connection conn = dataSource.getConnection();
                leakedConnections.add(conn);
                log.debug("Leaked connection {}", i + 1);
            }

            log.warn("⚠️ {} connections leaked (not returned to pool)", leakCount);

            // Try to get more connections - should still work if pool > 10
            try (Connection conn = dataSource.getConnection()) {
                log.info("✅ Can still get connections (pool has more)");
            }

        } finally {
            // Clean up - return leaked connections
            log.info("Cleaning up leaked connections...");
            for (Connection conn : leakedConnections) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Error closing leaked connection", e);
                }
            }
            log.info("✅ Cleanup complete");
        }
    }
}