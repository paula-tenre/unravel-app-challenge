package com.unravel.challenge.session;

/**
 * Exception thrown when a session is not found for a given user.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String userId) {
        super("Session not found for user: " + userId);
    }
}