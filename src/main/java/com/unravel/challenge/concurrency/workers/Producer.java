package com.unravel.challenge.concurrency.workers;

import com.unravel.challenge.concurrency.LogProcessor;
import com.unravel.challenge.concurrency.model.LogPriority;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

/**
 * Enhanced Producer that generates logs with varying priorities.
 * <p>
 * IMPROVEMENTS:
 * 1. Now generates logs with different priorities (not just strings)
 * 2. Realistic priority distribution (10% CRITICAL, 20% HIGH, 50% NORMAL, 20% LOW)
 * 3. Configurable number of logs to produce
 * 4. Better logging for observability
 */
@Slf4j
public class Producer extends Thread {
    private final LogProcessor processor;
    private final int logCount;
    private final Random random;

    /**
     * Constructor maintaining compatibility with original.
     * Defaults to 100 logs.
     */
    public Producer(LogProcessor processor) {
        this(processor, 100);
    }

    /**
     * Constructor with configurable log count.
     */
    public Producer(LogProcessor processor, int logCount) {
        this.processor = processor;
        this.logCount = logCount;
        this.random = new Random();
    }

    @Override
    public void run() {
        log.info("Producer started (will produce {} logs)", logCount);

        for (int i = 0; i < logCount; i++) {
            // Generate priority with realistic distribution
            LogPriority priority = generatePriority();

            String logMessage = "Log " + i + " [" + priority + "]";
            processor.produceLog(logMessage, priority);

            // Small delay to simulate realistic workload
            try {
                Thread.sleep(10 + random.nextInt(20)); // 10-30ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Producer interrupted");
                return;
            }
        }

        log.info("Producer completed ({} logs produced)", logCount);
    }

    /**
     * Generate priority with realistic distribution:
     * - CRITICAL: 10%
     * - HIGH: 20%
     * - NORMAL: 50%
     * - LOW: 20%
     */
    private LogPriority generatePriority() {
        int rand = random.nextInt(100);

        if (rand < 10) {
            return LogPriority.CRITICAL;
        } else if (rand < 30) {
            return LogPriority.HIGH;
        } else if (rand < 80) {
            return LogPriority.NORMAL;
        } else {
            return LogPriority.LOW;
        }
    }
}