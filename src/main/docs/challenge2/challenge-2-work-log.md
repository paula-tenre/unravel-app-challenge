# Challenge 2: Memory Leak Investigation - Work Log

## Initial Observation

Code review shows the issue: `MemoryManager` uses a static HashMap with no size limits or TTL, causing unbounded memory growth that will eventually lead to OutOfMemoryError.

I investigated as if unaware of the root cause, simulating real-world debugging with profiling tools.

---

## Investigation: Load Test & Profiling

**Test**: 200 users, 20% logout → Expected leak: 160 sessions × 10 MB = 1.6 GB

**IntelliJ Profiler**: Confirmed `MemoryManager.addSessionData()` allocates 97.6% of memory

**jmap**: Captured heap dump: `jmap -dump:live,format=b,file=challenge-2-leak.hprof 8648`

---

## Evidence: Heap Dump Analysis (Before Fix)

### Classes View

![Heap dump showing the leak](/src/main/docs/challenge2/Challenge2LeakEvidence.png)

```
byte[]            Count: 18,919    Retained: 2.1 GB
HashMap           Count: 436       Retained: 2.1 GB
HashMap$Node      Count: 5,044     Retained: 2.1 GB
```

### Path to GC Root (The Smoking Gun)

![GC Root path showing static field](/src/main/docs/challenge2/Challenge2LeakEvidenceGCPath.png)

```
byte[10485760]
  ↓ HashMap$Node
  ↓ HashMap$Node[512]
  ↓ table of HashMap
  ↓ statically from largeSessionData of MemoryManager ← PROBLEM!
  ↓ ClassLoaders$AppClassLoader, GC Root: Global JNI
```

**Why it's a problem**: `statically from largeSessionData` proves static field → unmanaged lifecycle → no size limits → unbounded growth

**Evidence**: 160 byte[10MB] arrays retained in static HashMap (no eviction, no TTL)

---

## Solution: Caffeine Cache

**Implementation**:
```java
public class MemoryManager {
    private final Cache<String, byte[]> sessionDataCache;
    
    public MemoryManager() {
        this.sessionDataCache = Caffeine.newBuilder()
                .maximumSize(100)                     // Size limit
                .expireAfterAccess(1, TimeUnit.HOURS) // TTL
                .recordStats()
                .build();
    }
}
```

**Key improvements**:
- ✅ No static fields → proper lifecycle management
- ✅ Max 100 sessions → prevents unbounded growth
- ✅ 1h TTL → auto-cleanup of stale sessions
- ✅ Thread-safe

**Production usage**: In a real application, MemoryManager would be a Spring-managed singleton (`@Service`), staying alive for the application's lifetime. The fix replaces an **unmanaged static field** with a **properly lifecycle-managed instance**, enabling bounded growth via cache limits and clean shutdown.

---

## Verification: Heap Dump After Fix

### Classes View

![Heap dump after fix](/src/main/docs/challenge2/Challenge2FixedLeakEvidence.png)

```
MemoryManager        Count: 1       Retained: 1.05 GB
BoundedLocalCache    Count: 1       Retained: 1.05 GB  ← Caffeine
PSAMS                Count: 101     Retained: 4.04 kB  ← Caffeine
```

### Path to GC Root

![GC Root path after fix](/src/main/docs/challenge2/Challenge2FixedLeakEvidenceGCPath.png)

```
byte[10485760]
  ↓ PSAMS
  ↓ ConcurrentHashMap$Node
  ↓ ConcurrentHashMap
  ↓ BoundedLocalCache
  ↓ sessionDataCache of MemoryManager  ← Instance field (not static!)
  ↓ Java Frame (LoadSimulationTest)    ← Test method scope
```
**Note**: This heap dump is from the test environment. In production as a Spring `@Service`,
the GC Root would be: ApplicationContext → MemoryManager singleton → cache

**Critical change**: No `statically from` → instance field with proper lifecycle management

**What this proves**:
- ✅ Replaced unmanaged static field with lifecycle-managed instance
- ✅ Cache enforces size limits (max 100) and TTL (1h)
- ✅ Memory bounded regardless of whether cleanup is called

---

## Results: Before vs After

| Metric | Before | After |
|--------|--------|-------|
| **Sessions retained** | 160 | ~100 |
| **Memory** | 2.1 GB | 1.05 GB |
| **Field type** | Static (unmanaged) | Instance field (Spring-managed) |
| **Lifecycle** | Permanent (class-level) | Permanent (Spring singleton) |
| **Memory limits** | None (unbounded) | Size (100) + TTL (1h) |
| **Can prevent OOM** | ❌ NO | ✅ YES |

**Proof**: Cache enforces 100-session limit, memory reduced 50%, instance-based with bounded growth

**Key difference**: Both live for the application's lifetime, but the fixed version has **cache limits** that prevent unbounded growth.

---

## Key Takeaways

**Investigation Process**:
1. Profiler → allocation hotspots
2. Heap dump → retention proof
3. GC root analysis → root cause

**Tools**: IntelliJ Profiler, jmap, IntelliJ Heap Analyzer

**Root Cause**: Static HashMap = unmanaged, unbounded memory growth

**The Fix**: Replace static field with instance-based cache having:
- Bounded size (max 100 sessions)
- TTL expiration (1 hour)
- Proper lifecycle management (Spring-managed)


## Real scenario Solution: Redis

**Why Caffeine for this challenge**:
- ✅ Simple to implement and test
- ✅ No external dependencies
- ✅ Demonstrates the issue and the fix

**Why Redis for production**:

### 1. Horizontal Scalability
```
Current (Caffeine):
[App Instance 1] → Local Cache (100 sessions)
[App Instance 2] → Local Cache (100 sessions)
[App Instance 3] → Local Cache (100 sessions)
Problem: Session data NOT shared across instances
```

```
Production (Redis):
[App Instance 1] ──┐
[App Instance 2] ──┼──→ [Redis Cluster] (shared session store)
[App Instance 3] ──┘
Benefit: Single source of truth, any instance can serve any user
```

**Scenario**: User logs in via Load Balancer → Instance 1 creates session. Next request hits Instance 2 → needs to access same session data. With Caffeine, Instance 2 has no visibility. With Redis, it's centralized.

### 2. Memory Pressure Management
- **Caffeine**: Session data consumes application heap (10 MB × 100 = 1 GB per instance)
- **Redis**: Offloaded to dedicated cache tier, freeing app memory for business logic
- **Impact**: Application can scale independently of session storage needs

### 3. Persistence & Durability
- **Caffeine**: Lost on app restart/crash
- **Redis**: Configurable persistence
- **Impact**: Users don't lose sessions during deployments/crashes
