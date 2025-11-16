package com.unravel.challenge;

import com.unravel.challenge.deadlock.DeadlockSimulator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UnravelChallengeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnravelChallengeApplication.class, args);
        challenge4();
    }

    private static void challenge4() {
        DeadlockSimulator simulator = new DeadlockSimulator();
        Thread t1 = new Thread(simulator::method1);
        Thread t2 = new Thread(simulator::method2);
        t1.start();
        t2.start();
    }
}