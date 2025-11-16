package com.unravel.challenge.deadlock.lock;

/**
 * Defines global lock ordering to prevent deadlocks.
 * <p>
 * Rule: Always acquire locks in the order defined here (lower ordinal first).
 * This breaks the "circular wait" condition required for deadlocks.
 *
 */
public enum LockOrder {
    RESOURCE_A(1),
    RESOURCE_B(2);

    private final int priority;

    LockOrder(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public static boolean isValidOrder(LockOrder first, LockOrder second) {
        return first.priority < second.priority;
    }
}