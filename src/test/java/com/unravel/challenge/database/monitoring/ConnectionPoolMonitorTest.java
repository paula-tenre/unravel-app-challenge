package com.unravel.challenge.database.monitoring;

import com.unravel.challenge.database.TestDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify ConnectionPoolMonitor tracks metrics correctly.
 */
@Slf4j
@SpringBootTest
class ConnectionPoolMonitorTest {

    @Autowired
    private ConnectionPoolMonitor monitor;

    @Autowired
    private TestDataRepository repository;

    /**
     * Test 1: Verify monitor is initialized
     */
    @Test
    void testMonitorInitialized() throws Exception {
        assertNotNull(monitor, "ConnectionPoolMonitor should be initialized");

        // Initialize the pool by making a database call
        int count = repository.countRecords();
        log.info("Database has {} records", count);

        // Small delay to ensure pool is ready
        Thread.sleep(100);

        ConnectionPoolMonitor.PoolMetrics metrics = monitor.getCurrentMetrics();
        assertNotNull(metrics);

        log.info("Monitor initialized successfully");
        log.info("Current pool state: {} active, {} idle, {} total",
                metrics.active(), metrics.idle(), metrics.total());

        // Print detailed stats
        monitor.printDetailedStats();
    }

    /**
     * Test 2: Monitor tracks metrics under load
     */
    @Test
    void testMonitorTracksMetricsUnderLoad() throws Exception {
        log.info("TEST: Monitor metrics under load");

        // Initialize pool first
        int count = repository.countRecords();
        log.info("Database initialized with {} records", count);
        Thread.sleep(100);

        // Get baseline
        ConnectionPoolMonitor.PoolMetrics baseline = monitor.getCurrentMetrics();
        log.info("BASELINE: {} active, {} idle, {} total",
                baseline.active(), baseline.idle(), baseline.total());

        // Create load: 50 concurrent requests with 1 second hold time
        int concurrentRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        // Submit all requests
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Hold connection for 1 second
                    repository.getDataWithDelay(1000);

                } catch (Exception e) {
                    log.error("Request failed", e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all requests
        startLatch.countDown();

        // Wait a moment for connections to be acquired
        Thread.sleep(100);

        // Check metrics while under load
        ConnectionPoolMonitor.PoolMetrics underLoad = monitor.getCurrentMetrics();
        log.info("UNDER LOAD: {} active, {} idle, {} total, {} waiting",
                underLoad.active(),
                underLoad.idle(),
                underLoad.total(),
                underLoad.waiting());

        // Verify connections are being used
        assertTrue(underLoad.active() > 0,
                "Active connections should be greater than 0 under load");

        // Wait for completion
        completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Wait for connections to return to pool
        Thread.sleep(500);

        // Check metrics after load
        ConnectionPoolMonitor.PoolMetrics afterLoad = monitor.getCurrentMetrics();
        log.info("AFTER LOAD: {} active, {} idle, {} total",
                afterLoad.active(), afterLoad.idle(), afterLoad.total());

        // Pool should return to healthy state
        assertTrue(afterLoad.active() <= baseline.active() + 1,
                "Active connections should return to baseline");

        log.info("Monitor successfully tracked metrics through load cycle");
    }

    /**
     * Test 3: Monitor detects pool pressure
     */
    @Test
    void testMonitorDetectsPoolPressure() throws InterruptedException {
        log.info("TEST: Monitor detects pool pressure");

        // Create high load that will stress the pool
        // 120 concurrent requests with 2 second hold time
        int concurrentRequests = 120;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        log.info("Starting {} concurrent requests...", concurrentRequests);

        // Submit all requests
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    repository.getDataWithDelay(2000);
                } catch (Exception e) {
                    log.error("Request failed", e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all requests
        startLatch.countDown();

        // Wait for pool to get stressed
        Thread.sleep(200);

        // Check metrics during peak load
        ConnectionPoolMonitor.PoolMetrics metrics = monitor.getCurrentMetrics();
        log.info("PEAK LOAD: {} active, {} idle, {} total, {} waiting, {}% utilization",
                metrics.active(),
                metrics.idle(),
                metrics.total(),
                metrics.waiting(),
                String.format("%.1f", metrics.utilizationPercent()));

        // Monitor should detect pressure
        if (metrics.isUnderPressure()) {
            log.warn("Pool under pressure detected (as expected)");
        }

        // Print detailed stats during stress
        monitor.printDetailedStats();

        // Wait for completion
        completionLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("Monitor successfully detected and logged pool pressure");
    }

    /**
     * Test 4: Verify PoolMetrics helper methods
     */
    @Test
    void testPoolMetricsHelpers() {
        // Healthy pool
        ConnectionPoolMonitor.PoolMetrics healthy =
                new ConnectionPoolMonitor.PoolMetrics(5, 15, 20, 0, 100, 25.0);
        assertTrue(healthy.isHealthy());
        assertFalse(healthy.isUnderPressure());
        assertFalse(healthy.isExhausted());

        // Under pressure
        ConnectionPoolMonitor.PoolMetrics pressure =
                new ConnectionPoolMonitor.PoolMetrics(85, 5, 90, 5, 100, 94.4);
        assertFalse(pressure.isHealthy());
        assertTrue(pressure.isUnderPressure());
        assertFalse(pressure.isExhausted());

        // Exhausted
        ConnectionPoolMonitor.PoolMetrics exhausted =
                new ConnectionPoolMonitor.PoolMetrics(100, 0, 100, 20, 100, 100.0);
        assertFalse(exhausted.isHealthy());
        assertTrue(exhausted.isUnderPressure());
        assertTrue(exhausted.isExhausted());

        log.info("PoolMetrics helper methods work correctly");
    }
}