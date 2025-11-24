package com.unravel.challenge.concurrency;

import com.unravel.challenge.concurrency.workers.Consumer;
import com.unravel.challenge.concurrency.model.LogPriority;
import com.unravel.challenge.concurrency.workers.Producer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying priority-based processing works correctly.
 */
@Slf4j
class LogProcessorPriorityTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPriorityOrdering() throws InterruptedException {
        log.info("TEST: Priority Ordering");

        LogProcessor processor = new LogProcessor();

        // Submit logs in "wrong" order
        processor.produceLog("Low priority", LogPriority.LOW);
        processor.produceLog("Critical issue", LogPriority.CRITICAL);
        processor.produceLog("Normal log", LogPriority.NORMAL);
        processor.produceLog("High priority", LogPriority.HIGH);

        // Small delay to ensure all are in queue
        Thread.sleep(100);

        // Consume and verify order: CRITICAL → HIGH → NORMAL → LOW
        List<String> consumed = new ArrayList<>();
        while (processor.getQueueSize() > 0) {
            consumed.add(processor.consumeLog());
        }

        assertEquals(4, consumed.size());
        assertTrue(consumed.get(0).contains("Critical"));
        assertTrue(consumed.get(1).contains("High"));
        assertTrue(consumed.get(2).contains("Normal"));
        assertTrue(consumed.get(3).contains("Low"));

        log.info("SUCCESS: Logs consumed in priority order");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testStarvationPrevention() throws InterruptedException {
        log.info("TEST: Starvation Prevention");

        LogProcessor processor = new LogProcessor();

        // Submit 1 LOW priority log
        processor.produceLog("Low priority task", LogPriority.LOW);
        log.info("Submitted LOW priority log");

        // Wait 35 seconds for aging
        log.info("Waiting 35 seconds for aging...");
        Thread.sleep(35000);

        // Now submit 20 NORMAL priority logs
        log.info("Submitting 20 NORMAL priority logs");
        for (int i = 0; i < 20; i++) {
            processor.produceLog("Normal log " + i, LogPriority.NORMAL);
        }

        // The LOW log should be processed early (not at position 21)
        // because it has aged for 35+ seconds
        int position = 0;
        boolean foundLow = false;

        while (processor.getQueueSize() > 0) {
            String logMessage = processor.consumeLog();
            position++;

            if (logMessage.contains("Low priority task")) {
                foundLow = true;
                log.info("LOW priority log found at position {} (aged 35+ seconds)", position);

                // Should be in first ~15 positions due to aging
                assertTrue(position <= 15,
                        "LOW priority log starved: position " + position);
                break;
            }
        }

        assertTrue(foundLow, "LOW priority log not found");
        log.info("SUCCESS: Starvation prevented - LOW log processed early");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMultipleProducersAndConsumers() throws InterruptedException {
        log.info("TEST: Multiple Producers and Consumers");

        LogProcessor processor = new LogProcessor();

        // Create multiple producers and consumers
        int producerCount = 3;
        int consumerCount = 5;
        int logsPerProducer = 20;

        Producer[] producers = new Producer[producerCount];
        Consumer[] consumers = new Consumer[consumerCount];

        // Start consumers
        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new Consumer(processor, 12); // Each consumes 12 logs
            consumers[i].setName("Consumer-" + i);
            consumers[i].start();
        }

        // Start producers
        for (int i = 0; i < producerCount; i++) {
            producers[i] = new Producer(processor, logsPerProducer);
            producers[i].setName("Producer-" + i);
            producers[i].start();
        }

        // Wait for producers
        for (Producer p : producers) {
            p.join();
        }
        log.info("All producers completed");

        // Wait for consumers
        for (Consumer c : consumers) {
            c.join(10000);
        }

        log.info("All consumers completed");

        // Verify queue is empty or nearly empty
        int remainingLogs = processor.getQueueSize();
        log.info("Remaining logs in queue: {}", remainingLogs);
        assertTrue(remainingLogs < 10, "Too many logs remaining: " + remainingLogs);

        log.info("SUCCESS: Multiple producers/consumers handled correctly");
    }
}