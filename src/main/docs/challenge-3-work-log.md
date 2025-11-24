# Challenge 3: Producer-Consumer - Work Log

## Step 1: Initial Code Review

### What I Observed

**Original Code Pattern**:
```java
private Queue<String> logQueue = new LinkedList<>();

public synchronized void produceLog(String log) {
    logQueue.add(log);
    notify();
}

public synchronized String consumeLog() throws InterruptedException {
    while (logQueue.isEmpty()) {
        wait();
    }
    return logQueue.poll();
}
```

**Red Flags**:
- ❌ No priority support - simple FIFO queue
- ❌ All logs treated equally
- ❌ No way to prioritize critical tasks
- ❌ Starvation possible (if high-priority tasks keep arriving)
- ❌ Single producer/consumer (not scalable)

### Hypothesis

The system needs:
1. **Priority mechanism** - Different task types need different priorities
2. **Starvation prevention** - Low-priority tasks shouldn't wait forever
3. **Scalability** - Multiple producers/consumers

---

## Step 2: Design the Solution

### Core Components

**1. Priority Model**
- `LogPriority` enum with 4 levels: CRITICAL (1), HIGH (2), NORMAL (3), LOW (4)
- Lower numeric value = higher priority

**2. PriorityQueue Implementation**
- Replace `LinkedList<String>` with `PriorityQueue<LogEntry>`
- `LogEntry` wraps message + priority + timestamp
- Implements `Comparable` for automatic ordering

**3. Aging Mechanism**
Dynamic priority calculation to prevent starvation:
```
effectivePriority = basePriority - (ageInSeconds / 30)

Examples:
- LOW (4) at t=0s:   priority = 4.0 (lowest)
- LOW (4) at t=30s:  priority = 3.0 (now NORMAL)
- LOW (4) at t=60s:  priority = 2.0 (now HIGH)
- LOW (4) at t=120s: priority = 0.0 (top priority!)
```

**Mathematical guarantee**: As time → ∞, any task reaches top priority → **No starvation possible**

**Why 30 seconds?**
- Too fast (10s): Low-priority interferes with urgent work
- Too slow (60s): Unacceptable wait times
- 30s: Good balance - LOW becomes HIGH after 60s

**4. Thread Pools**
Use `ExecutorService` instead of manual thread creation:
- 3 producer threads, 5 consumer threads
- Graceful shutdown with `awaitTermination()`
- Better resource management

**5. Thread Safety**
Keep `synchronized` methods because `PriorityQueue` is **not thread-safe**
- Need synchronized for add/poll operations
- Need wait/notify for producer-consumer coordination

---

## Step 3: Implementation

### Key Changes

**LogProcessor.java**
- Changed: `LinkedList<String>` → `PriorityQueue<LogEntry>`
- Added: `produceLog(String, LogPriority)` overload
- Kept: `synchronized` methods for thread safety
- `poll()` automatically returns highest priority log

**LogEntry.java**
- Implements `Comparable<LogEntry>`
- Contains aging logic in `getEffectivePriority()`
- `compareTo()` orders by effective priority, then timestamp (FIFO within same priority)

**Producer.java**
- Generates logs with realistic priority distribution (10% CRITICAL, 20% HIGH, 50% NORMAL, 20% LOW)
- Configurable log count
- Small delays to simulate realistic workload

**Consumer.java**
- Added graceful shutdown mechanism (not infinite loop)
- Optional max logs to consume (useful for testing)

**UnravelChallengeApplication.java**
- Uses `ExecutorService` with fixed thread pools
- 3 producers, 5 consumers
- Proper shutdown sequence: producers finish → wait for queue to drain → shutdown consumers

---

## Step 4: Testing Strategy

### Test 1: Priority Ordering
**Goal**: Verify logs are consumed in correct priority order

Submit logs in "wrong" order (LOW, CRITICAL, NORMAL, HIGH) and verify consumption order is CRITICAL → HIGH → NORMAL → LOW.

### Test 2: Starvation Prevention
**Goal**: Verify aging prevents LOW priority tasks from starving

1. Submit 1 LOW priority log
2. Wait 35 seconds (aging)
3. Submit 20 NORMAL priority logs
4. Verify LOW is processed early (not at position 21)

### Test 3: Multiple Producers/Consumers
**Goal**: Verify system works under concurrent load

3 producers × 20 logs = 60 total, 5 consumers processing. All logs should be processed correctly with no race conditions.

---

## Step 5: Comparison Before/After

| Aspect | Original | Enhanced |
|--------|----------|----------|
| **Queue Type** | LinkedList (FIFO) | PriorityQueue (priority-ordered) |
| **Ordering** | First-in-first-out | Priority-based + aging |
| **Priorities** | None | 4 levels (CRITICAL/HIGH/NORMAL/LOW) |
| **Starvation** | Possible | Prevented (mathematical guarantee) |
| **Scalability** | 1 producer, 1 consumer | Thread pools (3 producers, 5 consumers) |
| **Thread Management** | Manual thread creation | ExecutorService pools |
| **Shutdown** | No mechanism | Graceful with awaitTermination |
