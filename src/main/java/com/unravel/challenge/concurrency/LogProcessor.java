package com.unravel.challenge.concurrency;

import com.unravel.challenge.concurrency.model.LogEntry;
import com.unravel.challenge.concurrency.model.LogPriority;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.PriorityQueue;

/**
 * Enhanced LogProcessor with priority support and starvation prevention.
 * <p>
 * FIXES:
 * 1. Added priority levels for logs
 * 2. Changed from LinkedList to PriorityQueue
 * 3. Removed manual synchronization (PriorityQueue + synchronized methods)
 * 4. Added aging mechanism to prevent starvation
 */
@Slf4j
public class LogProcessor {

    // Changed from Queue<String> to PriorityQueue<LogEntry>
    private final PriorityQueue<LogEntry> logQueue = new PriorityQueue<>();

    /**
     * Produce a log with NORMAL priority (default)
     */
    public synchronized void produceLog(String log) {
        produceLog(log, LogPriority.NORMAL);
    }

    /**
     * Produce a log with specified priority.
     * New method to support priority-based processing.
     */
    public synchronized void produceLog(String logMessage, LogPriority priority) {
        logQueue.add(new LogEntry(logMessage, priority));
        notify();
        log.debug("Produced log: {} (priority: {})", logMessage, priority);
    }

    /**
     * Consume a log (highest priority first)
     */
    public synchronized String consumeLog() throws InterruptedException {
        while (logQueue.isEmpty()) {
            wait();
        }

        LogEntry entry = logQueue.poll();
        if (entry != null) {
            log.debug("Consumed log: {} (priority: {}, age: {}s)",
                    entry.getMessage(),
                    entry.getPriority(),
                    Instant.now().getEpochSecond() - entry.getTimestamp().getEpochSecond()
            );
            return entry.getMessage();
        }

        return null;
    }

    /**
     * Get current queue size (for monitoring).
     */
    public synchronized int getQueueSize() {
        return logQueue.size();
    }
}