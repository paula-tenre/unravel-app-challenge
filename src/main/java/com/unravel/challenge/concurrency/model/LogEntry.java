package com.unravel.challenge.concurrency.model;


import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal log entry with priority and aging.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private final String id;
    private final String message;
    private final LogPriority priority;
    private final Instant timestamp;

    // Aging factor: every 30 seconds = 1 priority level boost
    private static final long AGING_FACTOR_SECONDS = 30;

    public LogEntry(String message, LogPriority priority) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.message = message;
        this.priority = priority;
        this.timestamp = Instant.now();
    }

    /**
     * Calculate effective priority with aging to prevent starvation.
     */
    public double getEffectivePriority() {
        long ageInSeconds = Instant.now().getEpochSecond() - timestamp.getEpochSecond();
        double agingBoost = (double) ageInSeconds / AGING_FACTOR_SECONDS;
        return Math.max(0, priority.getValue() - agingBoost);
    }

    @Override
    public int compareTo(LogEntry other) {
        // Lower effective priority = higher urgency (processed first)
        int priorityComp = Double.compare(this.getEffectivePriority(), other.getEffectivePriority());
        if (priorityComp != 0) {
            return priorityComp;
        }
        // Same priority: FIFO (older first)
        return this.timestamp.compareTo(other.timestamp);
    }

}
