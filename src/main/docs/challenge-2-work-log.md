# Challenge 2: Memory Leak Investigation - Work Log

## Initial Observation

With an initial code review, the issue is obvious: `MemoryManager` uses a **static HashMap** that's never cleaned up when users logout. The static field makes it a GC root, preventing garbage collection.

However, I decided to investigate this as if I weren't aware of the root cause, simulating a real-world debugging scenario where you must diagnose memory issues under load using profiling tools.

---

## Investigation Approach: Load Simulation & Profiling

### Load Test Design

Created `LoadSimulationTest` to reproduce production-like conditions:
- **200 users** log in (each creates 10 MB session data)
- **Only 20% logout** properly (simulating real user behavior)
- **Expected leak**: 160 sessions × 10 MB = 1.6 GB

This should create a visible leak suitable for profiling tools to detect.

---

## Tool 1: IntelliJ Profiler - Finding Allocation Hotspots

Run LoadSimulationTest with IntelliJ Profiler.

**Confirmed allocation source**: `MemoryManager.addSessionData()` allocates 97.6% of memory  

**Still unknown**: Are these objects being garbage collected or retained?

**Next step needed**: Profiler shows *allocation*, but not *retention*. Need heap dump to prove objects can't be collected.

---

## Tool 2: jmap - Capturing Heap Dumps

### How I used it
```bash
# Find running Java process
jps

jmap -dump:live,format=b,file=challenge-2-leak.hprof 8648
```
Captured immediately after test execution to catch leaked objects before application exits.

---

## Tool 3: IntelliJ Heap Dump Analyzer - Finding the Leak

### Path to GC Root
```
byte[10485760] 10.49 MB
  ↓ value of java.util.HashMap$Node
  ↓ java.util.HashMap$Node[512] 
  ↓ table of java.util.HashMap 2.1 GB
  ↓ statically from largeSessionData of com.unravel.challenge.memory.MemoryManager
  ↓ java.lang.Object[823]
  ↓ elementData of java.util.ArrayList
  ↓ classes of jdk.internal.loader.ClassLoaders$AppClassLoader, GC Root: Global JNI
```

**Why it leaks**: Static fields are held by Class objects, which are held by ClassLoaders, which are GC roots. GC roots are **never collected** → everything they reference is **never collected**.



### Evidence Summary

✅ **Found**: 160 byte arrays of 10.49 MB each (1.6 GB total)  
✅ **Survived GC**: Objects remained after `jmap -dump:live` (forced garbage collection)  
✅ **Root cause identified**: Static HashMap in MemoryManager prevents garbage collection  
✅ **Missing cleanup**: `SessionManager.logout()` doesn't call `MemoryManager.removeSessionData()`
---

## Key Takeaways

### Investigation Process
1. **Profiler first**: Find allocation hotspots (what's allocating memory)
2. **Heap dump second**: Prove retention (what's preventing collection)
3. **GC root analysis**: Identify root cause (why objects can't be collected)

### Tools Used
- **IntelliJ Profiler**: Real-time allocation tracking → Found `addSessionData()` allocating 97.6%
- **jmap**: Heap snapshot creation → Captured 2.1 GB dump after test
- **IntelliJ Heap Analyzer**: GC root path analysis → Proved static field prevents collection

### Root Cause
Static HashMap in MemoryManager creates GC root → objects never collected → memory leak

### Solution
1. **Quick fix**: Add cleanup call in logout()
2. **Better fix**: Remove static modifier + dependency injection for lifecycle coordination
3. **Best fix**: Implement proper caching (Caffeine) with TTL, size limits, and automatic eviction
