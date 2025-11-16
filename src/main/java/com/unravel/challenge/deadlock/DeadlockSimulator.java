package com.unravel.challenge.deadlock;

import com.unravel.challenge.deadlock.lock.LockAcquisitionHelper;
import com.unravel.challenge.deadlock.lock.LockOrder;
import com.unravel.challenge.deadlock.lock.OrderedLock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeadlockSimulator {
    // Instead of Object locks, use OrderedLocks with priority
    private final OrderedLock lockA = new OrderedLock("lockA", LockOrder.RESOURCE_A);
    private final OrderedLock lockB = new OrderedLock("lockB", LockOrder.RESOURCE_B);

    public void method1() {
        // Request locks in original order
        if (!LockAcquisitionHelper.acquireAll(lockA, lockB)) {
            log.error("method1: Failed to acquire locks");
            return;
        }
        // Print original message
        System.out.println("Method1: Acquired lockA and lockB");
        LockAcquisitionHelper.releaseAll(lockA, lockB);
    }

    public void method2() {
        // Request locks in original order
        // Even though we request in "reverse" order (B, A),
        // the helper will still acquire in correct order (A, B)
        if (!LockAcquisitionHelper.acquireAll(lockB, lockA)) {
            log.error("method2: Failed to acquire locks");
            return;
        }
        // Print original message
        System.out.println("Method2: Acquired lock2 and lock1");
        LockAcquisitionHelper.releaseAll(lockB, lockA);
    }

    // Expose metrics (only for easy testing purposes)
    public void printMetrics() {
        log.debug("LOCK METRICS:");
        log.debug("LockA: acquisitions={}, timeouts={}",
                lockA.getTotalAcquisitions(), lockA.getTimeoutFailures());
        log.debug("LockB: acquisitions={}, timeouts={}",
                lockB.getTotalAcquisitions(), lockB.getTimeoutFailures());
    }
}