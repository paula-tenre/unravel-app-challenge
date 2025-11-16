package com.unravel.challenge.deadlock.lock;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helper to acquire multiple locks in correct order with timeout support.
 * <p>
 * This is the key component that prevents deadlocks by:
 * 1. Sorting locks by priority before acquisition
 * 2. Using timeouts to prevent permanent hangs
 * 3. Releasing all locks if acquisition fails
 */
@Slf4j
public class LockAcquisitionHelper {

    // TODO this should be a configuration property
    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;

    /**
     * Acquire multiple locks in correct order with retry logic
     *
     * @param locks Locks to acquire (will be sorted automatically)
     * @return true if all locks acquired, false otherwise
     */
    public static boolean acquireAll(OrderedLock... locks) {
        return acquireAll(Arrays.asList(locks));
    }

    public static boolean acquireAll(List<OrderedLock> locks) {
        // Sort by lock order, preventing deadlock
        locks.sort(Comparator.comparing(OrderedLock::getOrder, Comparator.comparing(LockOrder::getPriority)));

        log.debug("Thread {} attempting to acquire {} locks in order: {}",
                Thread.currentThread().getName(),
                locks.size(),
                locks.stream().map(OrderedLock::getName).toArray());

        // Try with retry and exponential backoff
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (tryAcquireAll(locks, attempt)) {
                return true;
            }

            // Exponential backoff before retry
            if (attempt < MAX_RETRIES) {
                long backoffMs = (long) (100 * Math.pow(2, attempt - 1));
                log.debug("Thread {} backing off for {}ms before retry",
                        Thread.currentThread().getName(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        log.error("Thread {} failed to acquire all locks after {} attempts",
                Thread.currentThread().getName(), MAX_RETRIES);
        return false;
    }

    /**
     * Try to acquire all locks once
     */
    private static boolean tryAcquireAll(List<OrderedLock> locks, int attemptNumber) {
        int acquiredCount = 0;

        try {
            for (OrderedLock lock : locks) {
                boolean acquired = lock.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (!acquired) {
                    log.warn("Thread {} failed to acquire lock {} (attempt {}/{})",
                            Thread.currentThread().getName(),
                            lock.getName(),
                            attemptNumber,
                            MAX_RETRIES);
                    // Release what we already got
                    releaseAll(locks.subList(0, acquiredCount));
                    return false;
                }

                acquiredCount++;
            }

            log.debug("Thread {} successfully acquired all {} locks (attempt {})",
                    Thread.currentThread().getName(), locks.size(), attemptNumber);
            return true;

        } catch (Exception e) {
            log.error("Exception during lock acquisition", e);
            releaseAll(locks.subList(0, acquiredCount));
            return false;
        }
    }

    /**
     * Release all locks
     */
    public static void releaseAll(List<OrderedLock> locks) {
        for (int i = locks.size() - 1; i >= 0; i--) {
            locks.get(i).release();
        }
    }

    public static void releaseAll(OrderedLock... locks) {
        releaseAll(Arrays.asList(locks));
    }
}