# Challenge 1: Session Management API - Work Log

## Step 1: Initial Code Review

### Issues Identified

**Issue 1: Race Condition in `login()`**
```java
if (sessions.containsKey(userId)) {     // Thread A checks
    return "User already logged in.";
}
sessions.put(userId, "SESSION_...");    // Thread B can check and put between these lines
```
**Problem**: Check-then-act pattern. Two threads can both pass the check and create duplicate sessions.

---

**Issue 2: Race Condition in `getSessionDetails()`**
```java
if (!sessions.containsKey(userId)) {    // Check
    throw new RuntimeException(...);
}
return "Session ID: " + sessions.get(userId);  // Act - session might be removed between check and get
```
**Problem**: Session could be removed after the check but before the get, causing NPE in string concatenation.

---

**Issue 3: Poor Exception Handling**
- Using generic `RuntimeException` instead of custom exceptions
- No clear contract for error cases
- Hard for callers to distinguish different error types

---

## Step 2: Design the Fix

### Solution 1: `login()` - Use `putIfAbsent()`

**Atomic operation** that checks and inserts in one step:
```java
String newSession = "SESSION_" + UUID.randomUUID();
String existing = sessions.putIfAbsent(userId, newSession);

if (existing != null) {
    return "User already logged in.";
}
return "Login successful. Session ID: " + newSession;
```

**Why this works**: `putIfAbsent()` is atomic - no race condition possible.

---

### Solution 2: `getSessionDetails()` - Use `get()` directly

**Skip the containsKey check**:
```java
String session = sessions.get(userId);
if (session == null) {
    throw new SessionNotFoundException(userId);
}
return "Session ID: " + session;
```

**Why this works**: Single atomic operation, no window for race condition.

---

### Solution 3: Custom Exception

```java
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String userId) {
        super("Session not found for user: " + userId);
    }
}
```

**Benefits**: Clear intent, easier to catch specifically, better error messages.

---

## Step 3: Implementation Summary

### Changes Made

1. ✅ Fixed `login()` with `putIfAbsent()`
2. ✅ Fixed `getSessionDetails()` by removing `containsKey()` check
3. ✅ Created `SessionNotFoundException` for better error handling
4. ✅ Added thread-safe tests to verify concurrent behavior

### Result

- No race conditions
- Proper exception handling
- Thread-safe under high concurrency