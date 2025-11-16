# Challenge 4: Deadlock Analysis - Work Log

## Step 1: Initial Code Review

### What I Observed

**Original Code Pattern**:
```java
method1: synchronized(lock1) { synchronized(lock2) { ... } }
method2: synchronized(lock2) { synchronized(lock1) { ... } }
```

**Red Flag**: Different lock order
- method1: lock1 → lock2
- method2: lock2 → lock1

### Hypothesis

Classic lock ordering deadlock.

**Scenario**:
```
T0: Thread-1 gets lock1, Thread-2 gets lock2
T1: Thread-1 waits for lock2 (held by Thread-2)
    Thread-2 waits for lock1 (held by Thread-1)
→ DEADLOCK
```

**Coffman Conditions**: All 4 present
- ✅ Mutual Exclusion (synchronized)
- ✅ Hold and Wait (nested locks)
- ✅ No Preemption (can't force release)
- ✅ Circular Wait (L1→L2 vs L2→L1)

---

## Step 2: Instrument Code

**Goal**: Reproduce and observe the issue

**Changes to `DeadlockSimulator.java`**:
- Added logging (before/after lock acquisition)
- Added metrics (AtomicInteger counters)
- Added `printStatistics()` method

**Why**: Need visibility into what's happening without changing lock behavior

---

## Step 3: Write Reproduction Tests

**File**: `DeadlockReproductionTest.java`

**Test 1 - Minimal**: 2 threads, 2s timeout → Proves deadlock exists

**Test 2 - Frequency**: 50 runs, 2s timeout each → Measures failure rate

**Test 3 - High Concurrency**: 20 threads, 15s timeout → Tests realistic load

---

## Step 4: Test Results

### Test 1: Minimal Deadlock
✅ Deadlock occurred (not every time, but majority)

### Test 2: Frequency Analysis
```
Total Runs: 50
Deadlocks: 16 (32%)
Successes: 34 (68%)
```
**Observation**: Fails randomly at 20-50% rate

### Test 3: High Concurrency
```
Started: 20 threads
Completed: 0 threads
Deadlocked: 20 threads
```
**Observation**: Immediate deadlock with concurrent load

---

## Conclusions

**Problem Confirmed**:
1. ✅ Deadlock exists and is reproducible
2. ✅ High frequency (32% in test, varies 20-50%)
3. ✅ Critical under load (100% failure with 20 threads)
4. ✅ Root cause: Lock ordering mismatch

**Next**: Design and implement fix

---

## Step 5: Design the Fix

### Options Considered

#### Option 1: Just Swap Lock Order
```java
// Change method2 to: lock1 → lock2 (same as method1)
```
**Pros**: 1 line fix, zero overhead, solves this specific case  
**Cons**:
- Fixes the symptom, not the underlying problem
- No enforcement mechanism - easy to introduce the same bug elsewhere
- No protection if someone adds more locks later
- No safety net if ordering is violated

**Decision**: ❌ This fixes the bug but doesn't prevent similar ones in the future

---

#### Option 2: Ordered Locks + Timeout ✅
**Components**:
- `LockOrder` enum (defines priorities)
- `OrderedLock` (wraps ReentrantLock with timeout)
- `LockAcquisitionHelper` (sorts locks, handles retry)

**Pros**:
- Fixes the underlying problem (enforces ordering systematically)
- Prevents this bug from happening again elsewhere
- Timeout prevents permanent hang (defensive layer)
- Reusable across codebase
- Observable (logging + metrics)

**Cons**:
- A lot more lines of code
- Small overhead (~2ms)
- Requires using the helper instead of raw locks

**Decision**: ✅ Best approach - fixes the **problem pattern**, not just this instance

---

### Why This Approach?

**Key Difference**:
- Option 1 fixes **this deadlock**
- Option 2 fixes **the ability to create deadlocks this way**

With ordered lock system:
- Helper automatically sorts locks before acquiring
- Impossible to create circular wait
- Works for any number of locks
- Future-proof

---

### Why ReentrantLock?
Need `tryLock(timeout)` - `synchronized` can't time out

---

### Three-Layer Defense

1. **Prevention**: Lock ordering (eliminates circular wait)
2. **Timeout**: 5sec + 3 retries with exponential backoff
3. **Observability**: Logging + metrics

---

## Handling Third-Party Library Locks

### The Challenge

Third-party libraries may have internal synchronized blocks we can't control.

### The Solution: Wrapper Pattern

We can't control third-party locks, so we create a wrapper lock that establishes
ordering before entering third-party code.

#### How would it look like in code?
A new LockOrder would be created for third-party libraries (e.g. LockOrder.EXTERNAL_SERVICE),
with a new order priority (e.g. 3)

Third-party calls would be wrapped with the lock ordering system:
```java
private final OrderedLock thirdPartyWrapperLock = 
    new OrderedLock("third_party_wrapper", LockOrder.EXTERNAL_SERVICE);

public void useThirdPartyService() {
    List<OrderedLock> locks = null;
    try {
        // Acquire our wrapper lock in correct order
        locks = LockAcquisitionHelper.acquireAll(
            LockA,                     // Order priority 1
            thirdPartyWrapperLock      // Order priority 3
        );
        
        if (locks.isEmpty()) {
            throw new RuntimeException("Failed to acquire locks");
        }
        
        // Now safe to call third-party code
        thirdPartyService.doSomething();
        
    } finally {
        if (locks != null) {
            LockAcquisitionHelper.releaseAll(locks);
        }
    }
}
```

### Why This Works

1. **Our locks acquired first** in global order
2. **Then** we call third-party code
3. Third-party internal locks are "nested inside" our lock boundary
4. No conflict because we control the outer ordering