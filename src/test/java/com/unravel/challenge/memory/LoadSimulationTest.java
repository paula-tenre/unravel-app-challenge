package com.unravel.challenge.memory;

import com.unravel.challenge.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * This test simulates production load to reproduce the memory leak.
 */
@Slf4j
public class LoadSimulationTest {

    @Test
    void simulateProductionLoad() {
        log.info("Starting load simulation...");

        SessionManager sessionManager = new SessionManager();

        // Simulate 200 users over time
        for (int i = 0; i < 200; i++) {
            String userId = "user_" + i;

            // User logs in
            String loginResult = sessionManager.login(userId);
            log.debug("User {} logged in", userId);

            // Add session data (simulating heavy user data)
            String sessionId = extractSessionId(loginResult);
            if (sessionId != null) {
                MemoryManager.addSessionData(sessionId);
            }

            // Simulate some users logging out (but not all)
            // This mimics real behavior where some sessions expire
            if (i % 5 == 0) {
                sessionManager.logout(userId);
                log.debug("User {} logged out", userId);
            }

            // Print memory every 50 users
            if (i % 50 == 0) {
                printMemoryUsage();
            }

            // Small delay to simulate real usage
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Load simulation complete");
        printMemoryUsage();

        // Keep JVM alive to take a heap dump
        log.info("Waiting 30 seconds for profiling...");
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractSessionId(String loginResult) {
        if (loginResult.contains("Session ID:")) {
            return loginResult.split("Session ID: ")[1].trim();
        }
        return null;
    }

    private void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        log.info("Memory: {} MB / {} MB ({}%)",
                usedMemory,
                maxMemory,
                (usedMemory * 100) / maxMemory);
    }
}