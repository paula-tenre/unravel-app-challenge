package com.unravel.challenge.memory;

import java.util.HashMap;
import java.util.Map;

public class MemoryManager {
    private static Map<String, byte[]> largeSessionData = new HashMap<>();

    public static void addSessionData(String sessionId) {
        largeSessionData.put(sessionId, new byte[10 * 1024 * 1024]); // 10MB per session
    }

    public static void removeSessionData(String sessionId) {
        largeSessionData.remove(sessionId);
    }
}