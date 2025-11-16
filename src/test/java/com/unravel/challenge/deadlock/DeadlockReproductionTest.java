package com.unravel.challenge.deadlock;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test designed to reproduce and analyze the deadlock
 */
@Slf4j
class DeadlockReproductionTest {
    /**
     * Test 1: Minimal reproduction (2 threads)
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMinimalDeadlock() {
        log.info("TEST 1: Minimal Deadlock Reproduction (2 threads)");

        DeadlockSimulator simulator = new DeadlockSimulator();
        AtomicBoolean deadlockOccurred = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                simulator.method1();
            } catch (Exception e) {
                log.error("Thread 1 error", e);
            }
        }, "Thread-Method1");

        Thread t2 = new Thread(() -> {
            try {
                simulator.method2();
            } catch (Exception e) {
                log.error("Thread 2 error", e);
            }
        }, "Thread-Method2");

        t1.start();
        t2.start();

        try {
            t1.join(2000);
            t2.join(2000);

            // Check if threads are still alive (= deadlocked)
            if (t1.isAlive() || t2.isAlive()) {
                deadlockOccurred.set(true);
                log.error("DEADLOCK DETECTED: Threads did not complete within timeout");

                // Interrupt threads to allow test to complete
                t1.interrupt();
                t2.interrupt();
            } else {
                log.info("Threads completed successfully (no deadlock this run)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        simulator.printStatistics();
        assertTrue(deadlockOccurred.get(), "Expected deadlock did not occur");
    }

    /**
     * Test 2: Measure deadlock frequency
     */
    @Test
    void testDeadlockFrequency() {
        log.info("TEST 2: Deadlock Frequency Analysis");

        final int TEST_RUNS = 50;
        int deadlockCount = 0;
        int successCount = 0;

        for (int run = 0; run < TEST_RUNS; run++) {
            DeadlockSimulator simulator = new DeadlockSimulator();
            CountDownLatch latch = new CountDownLatch(2);

            Thread t1 = new Thread(() -> {
                try {
                    simulator.method1();
                } finally {
                    latch.countDown();
                }
            }, "Run" + run + "-Method1");

            Thread t2 = new Thread(() -> {
                try {
                    simulator.method2();
                } finally {
                    latch.countDown();
                }
            }, "Run" + run + "-Method2");

            t1.start();
            t2.start();

            try {
                // Wait up to 2 seconds for completion
                boolean completed = latch.await(2, TimeUnit.SECONDS);

                if (!completed) {
                    deadlockCount++;
                    log.warn("Run {}: DEADLOCK", run + 1);
                    t1.interrupt();
                    t2.interrupt();
                } else {
                    successCount++;
                    log.debug("Run {}: Success", run + 1);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        double deadlockRate = (double) deadlockCount / TEST_RUNS * 100;

        log.info("FREQUENCY ANALYSIS RESULTS");
        log.info("Total Runs: {}", TEST_RUNS);
        log.info("Deadlocks: {} ({}%)", deadlockCount, String.format("%.1f", deadlockRate));
        log.info("Successes: {} ({}%)", successCount, String.format("%.1f", 100 - deadlockRate));

        assertTrue(deadlockCount > 0, "No deadlocks occurred in " + TEST_RUNS + " runs. Increase concurrency or delays.");
    }

    /**
     * Test 3: High concurrency scenario
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testHighConcurrencyDeadlock() {
        log.info("TEST 3: High Concurrency Deadlock");

        final int THREAD_COUNT = 20; // 10 threads per method
        DeadlockSimulator simulator = new DeadlockSimulator();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);

        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    if (threadId % 2 == 0) {
                        simulator.method1();
                    } else {
                        simulator.method2();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            }, "ConcurrentThread-" + i);
        }

        // Start all threads simultaneously
        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown(); // Release all threads at once

        try {
            // Wait for completion with timeout
            boolean completed = completionLatch.await(15, TimeUnit.SECONDS);

            if (!completed) {
                log.error("DEADLOCK DETECTED: Not all threads completed");
                log.error("Remaining threads: {}", completionLatch.getCount());

                // Interrupt all threads
                for (Thread t : threads) {
                    if (t.isAlive()) {
                        log.error("  - Still alive: {}", t.getName());
                        t.interrupt();
                    }
                }
            } else {
                log.info("All threads completed (no deadlock in this run)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        simulator.printStatistics();
    }
}