package com.unravel.challenge.session;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe session manager using atomic operations from ConcurrentHashMap.
 * <p>
 * Fixed issues:
 * 1. Race condition in login() - now uses putIfAbsent()
 * 2. Race condition in getSessionDetails() - now uses single get()
 * 3. Better exception handling with custom SessionNotFoundException
 */
@Slf4j
public class SessionManager {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    /**
     * Login a user and create a session.
     * Thread-safe: uses putIfAbsent() to atomically check and insert.
     *
     * @param userId The user ID to login
     * @return Success or already logged in message
     */
    public String login(String userId) {
        // Generate new session ID
        String newSessionId = "SESSION_" + UUID.randomUUID();

        // Atomically check if user exists and insert if not
        // Returns null if inserted, or existing value if already present
        String existingSession = sessions.putIfAbsent(userId, newSessionId);

        if (existingSession != null) {
            log.debug("User {} already logged in with session {}", userId, existingSession);
            return "User already logged in.";
        }

        log.info("User {} logged in successfully with session {}", userId, newSessionId);
        return "Login successful. Session ID: " + newSessionId;
    }

    /**
     * Logout a user and remove their session.
     *
     * @param userId The user ID to logout
     * @return Success or not logged in message
     */
    public String logout(String userId) {
        // remove() is already atomic in ConcurrentHashMap
        String removedSession = sessions.remove(userId);

        if (removedSession == null) {
            log.debug("Logout failed: User {} not logged in", userId);
            return "User not logged in.";
        }

        log.info("User {} logged out successfully", userId);
        return "Logout successful.";
    }

    /**
     * Get session details for a user.
     * Thread-safe: uses single get() operation instead of check-then-act.
     *
     * @param userId The user ID to get session for
     * @return Session details message
     * @throws SessionNotFoundException if user has no active session
     */
    public String getSessionDetails(String userId) {
        // Single atomic operation - no race condition possible
        String sessionId = sessions.get(userId);

        if (sessionId == null) {
            log.debug("Session details requested for non-existent user {}", userId);
            throw new SessionNotFoundException(userId);
        }

        log.debug("Retrieved session {} for user {}", sessionId, userId);
        return "Session ID for user " + userId + ": " + sessionId;
    }

    /**
     * Get the number of active sessions.
     * Useful for testing and monitoring.
     *
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}