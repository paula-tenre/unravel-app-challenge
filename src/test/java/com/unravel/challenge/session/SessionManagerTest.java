package com.unravel.challenge.session;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionManager focusing on thread safety and race conditions.
 */
@Slf4j
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    void testLoginSuccess() {
        String result = sessionManager.login("john.doe");

        assertTrue(result.startsWith("Login successful"));
        assertTrue(result.contains("SESSION_"));
        assertEquals(1, sessionManager.getActiveSessionCount());
    }

    @Test
    void testLoginAlreadyLoggedIn() {
        sessionManager.login("john.doe");
        String result = sessionManager.login("john.doe");

        assertEquals("User already logged in.", result);
        assertEquals(1, sessionManager.getActiveSessionCount());
    }

    @Test
    void testLogoutSuccess() {
        sessionManager.login("john.doe");
        String result = sessionManager.logout("john.doe");

        assertEquals("Logout successful.", result);
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    void testLogoutNotLoggedIn() {
        String result = sessionManager.logout("john.doe");

        assertEquals("User not logged in.", result);
    }

    @Test
    void testGetSessionDetailsSuccess() {
        sessionManager.login("john.doe");
        String result = sessionManager.getSessionDetails("john.doe");

        assertTrue(result.contains("Session ID for user john.doe"));
        assertTrue(result.contains("SESSION_"));
    }

    @Test
    void testGetSessionDetailsNotFound() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionManager.getSessionDetails("john.doe"));
    }

    // ========== Thread Safety Tests ==========

    @Test
    void testConcurrentLoginsSameUser() throws InterruptedException {
        log.info("TEST: Concurrent logins for same user");

        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<String> results = new CopyOnWriteArrayList<>();

        // Submit 100 threads that all try to login as "john.doe" simultaneously
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal
                    String result = sessionManager.login("john.doe");
                    results.add(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify: Only ONE session should be created
        assertEquals(1, sessionManager.getActiveSessionCount());

        // Verify: Exactly one "Login successful" and 99 "already logged in"
        long successCount = results.stream()
                .filter(r -> r.startsWith("Login successful"))
                .count();
        long alreadyLoggedInCount = results.stream()
                .filter(r -> r.equals("User already logged in."))
                .count();

        assertEquals(1, successCount, "Should have exactly 1 successful login");
        assertEquals(99, alreadyLoggedInCount, "Should have 99 'already logged in' responses");

        log.info("SUCCESS: No race condition - only 1 session created");
    }

    @Test
    void testConcurrentLoginsMultipleUsers() throws InterruptedException {
        log.info("TEST: Concurrent logins for multiple users");

        int usersCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(usersCount);

        ExecutorService executor = Executors.newFixedThreadPool(usersCount);

        // Each thread logs in a different user
        for (int i = 0; i < usersCount; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sessionManager.login(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // All 50 users should have sessions
        assertEquals(usersCount, sessionManager.getActiveSessionCount());

        log.info("SUCCESS: All {} users logged in correctly", usersCount);
    }

    @Test
    void testConcurrentGetSessionDetailsWhileLoggingOut() throws InterruptedException {
        log.info("TEST: Concurrent getSessionDetails while logging out");

        sessionManager.login("john.doe");

        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        List<String> successResults = new CopyOnWriteArrayList<>();

        // Half threads try to get session details, half try to logout
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    if (threadId % 2 == 0) {
                        // Try to get session details
                        String result = sessionManager.getSessionDetails("john.doe");
                        successResults.add(result);
                    } else {
                        // Try to logout
                        sessionManager.logout("john.doe");
                    }
                } catch (SessionNotFoundException e) {
                    // Expected if logout happened first
                    exceptions.add(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Either session was retrieved OR exception was thrown
        // No NPE or inconsistent state
        int totalResponses = successResults.size() + exceptions.size();
        assertEquals(threadCount / 2, totalResponses,
                "All getSessionDetails calls should either succeed or throw SessionNotFoundException");

        log.info("SUCCESS: No NPE or race condition - {} successes, {} exceptions",
                successResults.size(), exceptions.size());
    }

    @Test
    void testLoginLogoutStressTest() throws InterruptedException {
        log.info("TEST: Login/Logout stress test");

        int iterations = 1000;
        CountDownLatch completionLatch = new CountDownLatch(iterations * 2);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Rapidly login and logout the same user
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    sessionManager.login("john.doe");
                } finally {
                    completionLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    sessionManager.logout("john.doe");
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Should complete without deadlock or exceptions
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Final state should be valid (0 or 1 sessions, not corrupted)
        int finalCount = sessionManager.getActiveSessionCount();
        assertTrue(finalCount == 0 || finalCount == 1,
                "Final session count should be 0 or 1, got: " + finalCount);

        log.info("SUCCESS: {} operations completed without issues", iterations * 2);
    }
}