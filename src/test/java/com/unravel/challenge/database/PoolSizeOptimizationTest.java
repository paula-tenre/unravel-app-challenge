package com.unravel.challenge.database;

import com.unravel.challenge.database.monitoring.ConnectionPoolMonitor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to find optimal connection pool size.
 * <p>
 * Strategy:
 * 1. Test multiple pool sizes: 10, 20, 30, 50, 100
 * 2. For each size, run load test with 100 concurrent requests
 * 3. Measure: throughput, latency (avg, p95, p99), failures
 * 4. Identify sweet spot where performance is best
 */
@Slf4j
@SpringBootTest
class PoolSizeOptimizationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestDataRepository repository;

    @Autowired
    private ConnectionPoolMonitor monitor;

    /**
     * Main test: Find optimal pool size
     */
    @Test
    void findOptimalPoolSize() throws Exception {
        log.info("=".repeat(80));
        log.info("POOL SIZE OPTIMIZATION TEST");
        log.info("=".repeat(80));

        // Test different pool sizes
        int[] poolSizes = {10, 20, 30, 50, 100};
        List<PoolSizeResult> results = new ArrayList<>();

        for (int poolSize : poolSizes) {
            log.info("\n" + "=".repeat(80));
            log.info("TESTING POOL SIZE: {}", poolSize);
            log.info("=".repeat(80));

            // Configure pool size
            configurePoolSize(poolSize);

            // Wait for pool to stabilize
            Thread.sleep(2000);

            // Run load test
            PoolSizeResult result = runLoadTest(poolSize);
            results.add(result);

            // Print result
            printResult(result);

            // Wait before next test
            Thread.sleep(3000);
        }

        // Analyze and print comparison
        printComparison(results);
        printRecommendation(results);
    }

    /**
     * Run load test for a given pool size
     */
    private PoolSizeResult runLoadTest(int poolSize) throws InterruptedException {
        int concurrentRequests = 100;
        int slowQueryDelayMs = 500; // Simulates moderate database load

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());

        long testStart = System.currentTimeMillis();

        // Submit all requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long start = System.currentTimeMillis();

                    // Execute query with delay
                    repository.getDataWithDelay(slowQueryDelayMs);

                    long duration = System.currentTimeMillis() - start;
                    responseTimes.add(duration);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.warn("Request {} failed: {}", requestId, e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Capture metrics before starting load
        Thread.sleep(100);

        // Start all requests
        startLatch.countDown();

        // Wait a bit, then capture peak metrics
        Thread.sleep(200);
        ConnectionPoolMonitor.PoolMetrics peakMetrics = monitor.getCurrentMetrics();

        // Wait for completion
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        long totalDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();

        if (!completed) {
            log.error("Test did not complete within timeout!");
        }

        // Calculate statistics
        return calculateResult(
                poolSize,
                concurrentRequests,
                successCount.get(),
                failureCount.get(),
                totalDuration,
                responseTimes,
                peakMetrics
        );
    }

    /**
     * Calculate result statistics
     */
    private PoolSizeResult calculateResult(
            int poolSize,
            int totalRequests,
            int successCount,
            int failureCount,
            long totalDuration,
            List<Long> responseTimes,
            ConnectionPoolMonitor.PoolMetrics peakMetrics
    ) {
        if (responseTimes.isEmpty()) {
            return new PoolSizeResult(poolSize, totalRequests, 0, failureCount,
                    totalDuration, 0, 0, 0, 0, peakMetrics);
        }

        responseTimes.sort(Long::compareTo);

        long avgResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .sum() / responseTimes.size();

        long p50 = responseTimes.get(responseTimes.size() / 2);
        long p95 = responseTimes.get((int) (responseTimes.size() * 0.95));
        long p99 = responseTimes.get((int) (responseTimes.size() * 0.99));

        return new PoolSizeResult(
                poolSize,
                totalRequests,
                successCount,
                failureCount,
                totalDuration,
                avgResponseTime,
                p50,
                p95,
                p99,
                peakMetrics
        );
    }

    /**
     * Print individual result
     */
    private void printResult(PoolSizeResult result) {
        log.info("");
        log.info("RESULTS FOR POOL SIZE: {}", result.poolSize);
        log.info("-".repeat(80));
        log.info("Requests: {} total, {} successful, {} failed",
                result.totalRequests, result.successCount, result.failureCount);
        log.info("Total Duration: {}ms", result.totalDuration);
        log.info("Response Times:");
        log.info("  - Average: {}ms", result.avgResponseTime);
        log.info("  - P50: {}ms", result.p50);
        log.info("  - P95: {}ms", result.p95);
        log.info("  - P99: {}ms", result.p99);
        log.info("Peak Pool Metrics:");
        log.info("  - Active: {}", result.peakMetrics.active());
        log.info("  - Idle: {}", result.peakMetrics.idle());
        log.info("  - Total: {}", result.peakMetrics.total());
        log.info("  - Waiting: {}", result.peakMetrics.waiting());
        log.info("  - Utilization: {}%", String.format("%.1f", result.peakMetrics.utilizationPercent()));
    }

    /**
     * Print comparison table
     */
    private void printComparison(List<PoolSizeResult> results) {
        log.info("\n" + "=".repeat(80));
        log.info("COMPARISON: ALL POOL SIZES");
        log.info("=".repeat(80));
        log.info(String.format("%-10s | %-10s | %-10s | %-10s | %-10s | %-10s | %-10s",
                "Pool Size", "Success", "Avg (ms)", "P95 (ms)", "P99 (ms)", "Peak Conn", "Waiting"));
        log.info("-".repeat(80));

        for (PoolSizeResult result : results) {
            log.info(String.format("%-10d | %-10d | %-10d | %-10d | %-10d | %-10d | %-10d",
                    result.poolSize,
                    result.successCount,
                    result.avgResponseTime,
                    result.p95,
                    result.p99,
                    result.peakMetrics.total(),
                    result.peakMetrics.waiting()));
        }
        log.info("=".repeat(80));
    }

    /**
     * Provide recommendation based on results
     */
    private void printRecommendation(List<PoolSizeResult> results) {
        log.info("\n" + "=".repeat(80));
        log.info("RECOMMENDATION");
        log.info("=".repeat(80));

        // Find best result (lowest P95, all requests succeeded)
        PoolSizeResult best = results.stream()
                .filter(r -> r.failureCount == 0)
                .min(Comparator.comparingLong(r -> r.p95))
                .orElse(results.get(0));

        log.info("OPTIMAL POOL SIZE: {}", best.poolSize);
        log.info("");
        log.info("Why this size?");
        log.info("  - P95 latency: {}ms (lowest among all sizes)", best.p95);
        log.info("  - Peak connections used: {}", best.peakMetrics.total());
        log.info("  - Threads waiting: {}", best.peakMetrics.waiting());
        log.info("  - All {} requests succeeded", best.successCount);

        // Check for diminishing returns
        log.info("");
        log.info("Observations:");

        for (int i = 0; i < results.size() - 1; i++) {
            PoolSizeResult current = results.get(i);
            PoolSizeResult next = results.get(i + 1);

            long improvement = current.p95 - next.p95;
            double improvementPercent = (double) improvement / current.p95 * 100;

            if (improvementPercent < 5) {
                log.info("  Pool {} -> {}: Only {}% improvement (diminishing returns)",
                        current.poolSize, next.poolSize, String.format("%.1f", improvementPercent));
            }
        }

        // Formula recommendation
        int systemCores = Runtime.getRuntime().availableProcessors();
        int formulaRecommendation = (systemCores * 2) + 1; // Simplified: cores * 2 + spindles

        log.info("");
        log.info("System Info:");
        log.info("  - CPU Cores: {}", systemCores);
        log.info("  - Formula recommendation: {} connections", formulaRecommendation);
        log.info("    (cores × 2 + 1 disk)");

        if (Math.abs(best.poolSize - formulaRecommendation) <= 10) {
            log.info("  Optimal size matches formula recommendation!");
        } else {
            log.info("  Optimal size differs from formula - workload-specific tuning");
        }

        log.info("=".repeat(80));
    }

    /**
     * Configure pool size (requires HikariCP)
     */
    private void configurePoolSize(int poolSize) {
        if (dataSource instanceof HikariDataSource hikariDS) {
            hikariDS.setMaximumPoolSize(poolSize);
            hikariDS.setMinimumIdle(Math.max(2, poolSize / 10)); // 10% or 2 minimum
            log.info("Configured pool: max={}, min={}", poolSize, hikariDS.getMinimumIdle());
        } else {
            throw new IllegalStateException("DataSource is not HikariDataSource");
        }
    }

    /**
     * Result data class
     */
    private record PoolSizeResult(
            int poolSize,
            int totalRequests,
            int successCount,
            int failureCount,
            long totalDuration,
            long avgResponseTime,
            long p50,
            long p95,
            long p99,
            ConnectionPoolMonitor.PoolMetrics peakMetrics
    ) {
    }
}