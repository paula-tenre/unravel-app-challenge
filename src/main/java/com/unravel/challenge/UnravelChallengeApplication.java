package com.unravel.challenge;

import com.unravel.challenge.deadlock.DeadlockSimulator;
import com.unravel.challenge.processor.model.Consumer;
import com.unravel.challenge.processor.LogProcessor;
import com.unravel.challenge.processor.model.Producer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UnravelChallengeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnravelChallengeApplication.class, args);
        challenge2();
    }

    private static void challenge2() {
        LogProcessor processor = new LogProcessor();
        Producer producer = new Producer(processor);
        Consumer consumer = new Consumer(processor);
        producer.start();
        consumer.start();
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