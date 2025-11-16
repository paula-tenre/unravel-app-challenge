package com.unravel.challenge.deadlock;

import com.unravel.challenge.deadlock.lock.LockAcquisitionHelper;
import com.unravel.challenge.deadlock.lock.LockOrder;
import com.unravel.challenge.deadlock.lock.OrderedLock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeadlockSimulator {
    // Instead of Object locks, use OrderedLocks with priority
    private final OrderedLock lock1 = new OrderedLock("lock1", LockOrder.RESOURCE_A);
    private final OrderedLock lock2 = new OrderedLock("lock2", LockOrder.RESOURCE_B);

    public void method1() {
        // Request locks in original order
        if (!LockAcquisitionHelper.acquireAll(lock1, lock2)) {
            log.error("method1: Failed to acquire locks");
            return;
        }
        // Print original message
        System.out.println("Method1: Acquired lock1 and lock2");
        LockAcquisitionHelper.releaseAll(lock1, lock2);
    }

    public void method2() {
        // Request locks in original order
        // Even though we request in "reverse" order (B, A),
        // the helper will still acquire in correct order (A, B)
        if (!LockAcquisitionHelper.acquireAll(lock2, lock1)) {
            log.error("method2: Failed to acquire locks");
            return;
        }
        // Print original message
        System.out.println("Method2: Acquired lock2 and lock1");
        LockAcquisitionHelper.releaseAll(lock2, lock1);
    }

    // Expose metrics (only for easy testing purposes)
    public void printMetrics() {
        log.debug("LOCK METRICS:");
        log.debug("Lock1: acquisitions={}, timeouts={}",
                lock1.getTotalAcquisitions(), lock1.getTimeoutFailures());
        log.debug("Lock2: acquisitions={}, timeouts={}",
                lock2.getTotalAcquisitions(), lock2.getTimeoutFailures());
    }
}