package com.unravel.challenge.deadlock.lock;

import lombok.Getter;

/**
 * Defines global lock ordering to prevent deadlocks.
 * <p>
 * Rule: Always acquire locks in the order defined here (lower ordinal first).
 * This breaks the "circular wait" condition required for deadlocks.
 *
 */
@Getter
public enum LockOrder {
    RESOURCE_A(1),
    RESOURCE_B(2);

    private final int priority;

    LockOrder(int priority) {
        this.priority = priority;
    }
}