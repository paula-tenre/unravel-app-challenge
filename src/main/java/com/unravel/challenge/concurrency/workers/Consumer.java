package com.unravel.challenge.concurrency.workers;

import com.unravel.challenge.concurrency.LogProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced Consumer that processes logs with graceful shutdown support.
 * <p>
 * IMPROVEMENTS:
 * 1. Added shutdown flag instead of infinite loop
 * 2. Better error handling
 * 3. Configurable number of logs to consume (or run until shutdown)
 */
@Slf4j
public class Consumer extends Thread {
    private final LogProcessor processor;
    private final Integer maxLogsToConsume; // null = run until shutdown
    private volatile boolean shutdown = false;

    public Consumer(LogProcessor processor) {
        this(processor, null);
    }

    /**
     * Constructor with max logs to consume.
     */
    public Consumer(LogProcessor processor, Integer maxLogsToConsume) {
        this.processor = processor;
        this.maxLogsToConsume = maxLogsToConsume;
    }

    @Override
    public void run() {
        log.info("Consumer started");
        int consumedCount = 0;

        try {
            while (!shutdown) {
                // Check if we've consumed enough logs
                if (maxLogsToConsume != null && consumedCount >= maxLogsToConsume) {
                    log.info("Consumer reached max logs ({}), exiting", maxLogsToConsume);
                    break;
                }

                String log = processor.consumeLog();
                System.out.println("Consumed: " + log);
                consumedCount++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Consumer interrupted");
        }

        log.info("Consumer stopped (consumed {} logs)", consumedCount);
    }

    /**
     * Gracefully shutdown the consumer.
     */
    public void shutdown() {
        log.info("Consumer shutdown requested");
        shutdown = true;
        this.interrupt(); // Wake up from wait() if blocked
    }
}