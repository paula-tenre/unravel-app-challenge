package com.unravel.challenge;

import com.unravel.challenge.concurrency.LogProcessor;
import com.unravel.challenge.deadlock.DeadlockSimulator;
import com.unravel.challenge.concurrency.workers.Consumer;
import com.unravel.challenge.concurrency.workers.Producer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class UnravelChallengeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnravelChallengeApplication.class, args);
        challenge3WithThreadPools();
    }

    private static void challenge3WithThreadPools() {
        LogProcessor processor = new LogProcessor();

        ExecutorService producerPool = Executors.newFixedThreadPool(3);
        ExecutorService consumerPool = Executors.newFixedThreadPool(5);

        // Submit producers
        for (int i = 0; i < 3; i++) {
            producerPool.submit(new Producer(processor, 20));
        }

        // Submit consumers
        for (int i = 0; i < 5; i++) {
            consumerPool.submit(new Consumer(processor));
        }

        // Cleanup
        producerPool.shutdown();
        try {
            producerPool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        consumerPool.shutdown();
        try {
            consumerPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void challenge4() {
        DeadlockSimulator simulator = new DeadlockSimulator();

        Thread t1 = new Thread(simulator::method1, "Thread-1");
        Thread t2 = new Thread(simulator::method2, "Thread-2");

        t1.start();
        t2.start();

        try {
            t1.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            t2.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        simulator.printMetrics();
    }
}