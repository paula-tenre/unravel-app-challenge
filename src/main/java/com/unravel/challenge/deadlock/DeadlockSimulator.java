package com.unravel.challenge.deadlock;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DeadlockSimulator {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    // Metrics for analysis
    private final AtomicInteger method1Attempts = new AtomicInteger(0);
    private final AtomicInteger method1Successes = new AtomicInteger(0);
    private final AtomicInteger method2Attempts = new AtomicInteger(0);
    private final AtomicInteger method2Successes = new AtomicInteger(0);

    public void method1() {
        String threadName = Thread.currentThread().getName();
        int attemptNumber = method1Attempts.incrementAndGet();

        log.info("[{}] [{}] Method1 - Attempt #{}: Trying to acquire lock1",
                LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

        synchronized (lock1) {
            log.info("[{}] [{}] Method1 - Attempt #{}: Acquired lock1, now trying lock2",
                    LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

            synchronized (lock2) {
                log.info("[{}] [{}] Method1 - Attempt #{}: Acquired lock1 and lock2",
                        LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

                method1Successes.incrementAndGet();
                System.out.println("Method1: Acquired lock1 and lock2");
            }

            log.info("[{}] [{}] Method1 - Attempt #{}: Released lock2",
                    LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);
        }

        log.info("[{}] [{}] Method1 - Attempt #{}: Released lock1 - COMPLETE",
                LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);
    }

    public void method2() {
        String threadName = Thread.currentThread().getName();
        int attemptNumber = method2Attempts.incrementAndGet();

        log.info("[{}] [{}] Method2 - Attempt #{}: Trying to acquire lock2",
                LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

        synchronized (lock2) {
            log.info("[{}] [{}] Method2 - Attempt #{}: Acquired lock2, now trying lock1",
                    LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

            synchronized (lock1) {
                log.info("[{}] [{}] Method2 - Attempt #{}: Acquired lock2 and lock1",
                        LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);

                method2Successes.incrementAndGet();
                System.out.println("Method2: Acquired lock2 and lock1");
            }

            log.info("[{}] [{}] Method2 - Attempt #{}: Released lock1",
                    LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);
        }

        log.info("[{}] [{}] Method2 - Attempt #{}: Released lock2 - COMPLETE",
                LocalDateTime.now().format(TIME_FORMAT), threadName, attemptNumber);
    }

    public void printStatistics() {
        log.info("=== STATISTICS ===");
        log.info("Method1: Attempts={}, Successes={}", method1Attempts.get(), method1Successes.get());
        log.info("Method2: Attempts={}, Successes={}", method2Attempts.get(), method2Successes.get());
    }
}