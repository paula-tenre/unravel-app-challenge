package com.unravel.challenge.deadlock.lock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A lock wrapper that:
 * 1. Enforces ordering
 * 2. Supports timeout
 * 3. Tracks basic metrics
 */
@Slf4j
@Getter
public class OrderedLock {
    private final String name;
    private final LockOrder order;
    private final ReentrantLock lock;

    private final AtomicLong totalAcquisitions = new AtomicLong(0);
    private final AtomicLong timeoutFailures = new AtomicLong(0);

    public OrderedLock(String name, LockOrder order) {
        this.name = name;
        this.order = order;
        this.lock = new ReentrantLock(true); // Fair lock for predictable behavior
    }

    /**
     * Try to acquire lock with timeout
     *
     * @param timeout How long to wait
     * @param unit    Time unit
     * @return true if acquired, false if timeout
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        try {
            boolean acquired = lock.tryLock(timeout, unit);
            if (acquired) {
                totalAcquisitions.incrementAndGet();
                log.debug("Thread {} acquired lock {}", Thread.currentThread().getName(), name);
            } else {
                timeoutFailures.incrementAndGet();
                log.warn("Thread {} failed to acquire lock {} (timeout)", Thread.currentThread().getName(), name);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread {} interrupted while waiting for lock {}", Thread.currentThread().getName(), name);
            return false;
        }
    }

    /**
     * Release the lock
     */
    public void release() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Thread {} released lock {}", Thread.currentThread().getName(), name);
        }
    }

    public long getTotalAcquisitions() {
        return totalAcquisitions.get();
    }

    public long getTimeoutFailures() {
        return timeoutFailures.get();
    }
}