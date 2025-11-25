# Challenge 5: Database Connection Pool Optimization - Work Log

## Initial Code Review
**Base Configuration Issues**:
- `maximum-pool-size=100` - Likely too high, arbitrary number
- `DatabaseManager.closeConnection()` - Manual connection management (leak risk)
- No monitoring/metrics
- No leak detection configured

---

## Step 1: Environment Setup

### MySQL Database (Docker Compose)

**Created**: `scripts/docker-compose.yml` and `scripts/schema.sql`

**Usage**:
```bash
# Start MySQL (from scripts folder)
cd scripts
docker-compose up -d

# Stop (keep data)
docker-compose down

# Stop and remove all data (fresh start)
docker-compose down -v
```

## Step 2: Reproduce the Problem

### 2.1 Create Test Repository

**File**: `TestDataRepository.java`

**Purpose**: Simulate typical database operations:
- `getAllData()` - Fast query (normal case)
- `getDataWithDelay(ms)` - Slow query using `SLEEP()` to hold connections
- `insertData()` - Write operations
- `countRecords()` - Simple aggregation

**Why?** Need realistic workload to expose connection pool issues.

---

### 2.2 Load Test Suite

**File**: `ConnectionPoolLoadTest.java`

**Test 1 - Basic Connection**: Verify setup works
- ✅ Passed
- Database has 5 test records

**Test 2 - Moderate Load**: 50 concurrent requests
- ✅ Passed
- All requests succeeded
- Pool size 100 is sufficient for this load

**Test 3 - High Load with Slow Queries**: 150 concurrent requests, 2s each
- ✅ All requests completed (no timeouts)
- ⚠️ **But performance degraded severely**

**Results**:
```
Total requests: 150
Successful: 150
Failed: 0
Total duration: 5994ms
Avg response time: 4385ms  ← Expected ~2000ms
P95 response time: 5753ms  ← 3x slower!
P99 response time: 5941ms  ← Nearly 6 seconds!
```

**Test 4 - Connection Leak Scenario**:
- Demonstrates what happens when connections aren't returned
- Intentionally leaked 10 connections, then cleaned up

---

## Step 3: Analysis

### The Math Behind Test 3

**Setup**:
- 150 concurrent requests
- Each holds connection for 2 seconds (via `SLEEP(2)`)
- Pool size = 100 connections
- Connection timeout = 30 seconds

**Expected behavior without bottleneck**:
- All 150 threads start simultaneously
- Each grabs a connection
- Executes query in ~2 seconds
- Returns connection
- Total time: ~2 seconds

**Actual behavior**:
```
Requests 1-100:   Get connection immediately → 2 seconds
Requests 101-150: Wait for connection to free up → 2s + wait time
```

**Why requests are slow**:
- First 100 requests grab all connections
- Requests 101-150 must wait in queue
- As connections return to pool, waiting requests get served
- Later requests wait longer

**Visual representation**:
```
Time 0s:   [100 connections busy] → 50 threads waiting
Time 2s:   [50 connections busy]  → 50 threads still executing
Time 4s:   [25 connections busy]  → Last batch executing
Time 6s:   All complete
```

### Key Insight

**No requests "failed"** because timeout is 30 seconds, but **performance degraded 3x**.

In production, this means:
- Users experience slow response times
- API latency spikes during peak load
- Application-level timeouts might occur
- Poor user experience

---

## Step 4: Root Causes Identified

### Issue 1: Pool Size is Wrong

**Current**: `maximum-pool-size=100`

**Problem**: Arbitrary number, not based on actual system capacity.

**Right approach**: Use the formula from HikariCP documentation
```
connections = (CPU cores × 2) + number of disks

Example:
4 cores + 1 disk = 9 connections
8 cores + 2 disks = 18 connections
```

**Why?** Because:
- More connections ≠ better performance
- Each connection uses memory
- Database has its own connection limit
- Too many threads competing for CPU creates contention

**Hypothesis**: Pool size 20-30 would be optimal, not 100.

---

### Issue 2: No Monitoring

**Current state**: Flying blind
- Can't see how many connections are active
- Don't know how long requests wait
- No alerts when pool is exhausted

**What we need**:
- Active connections count
- Idle connections count
- Threads waiting for connections
- Connection acquisition time (p50, p95, p99)
- Pool utilization %

---

### Issue 3: Connection Leak Risk

**Current code**:
```java
public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
}

public void closeConnection(Connection connection) {
    // If you forget to call this, connection never returns!
    connection.close();
}
```

**Problem**: Manual lifecycle management
- Easy to forget to call `closeConnection()`
- If exception occurs before close, connection leaks
- Leaked connections never return to pool

**Solution**: Use try-with-resources (automatic cleanup)
```java
try (Connection conn = dataSource.getConnection()) {
    // Use connection
} // Automatically returned to pool - GUARANTEED
```

---

### Issue 4: Missing HikariCP Configuration

**Current configuration is incomplete**:
```properties
spring.datasource.hikari.maximum-pool-size=100
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=60000
spring.datasource.hikari.max-lifetime=1800000
```

**Missing**:
- `leak-detection-threshold` - Detect connections held too long
- `register-mbeans` - Enable JMX monitoring
- Proper pool sizing

---

## Next Steps

### Phase 1: Add Monitoring

**Created**: `ConnectionPoolMonitor.java` and `ConnectionPoolMonitorTest.java`

**What it does**:
- Monitors pool every 10 seconds using `@Scheduled`
- Tracks key metrics via HikariCP's MXBean interface:
    - Active connections (currently in use)
    - Idle connections (available in pool)
    - Total connections (active + idle)
    - Threads waiting for connections
    - Pool utilization percentage

**Alert conditions**:
1. ⚠️ High utilization (>80%)
2. ⚠️ Threads waiting for connections
3. ⚠️ Pool approaching max size (>90%)
4. 🚨 Pool exhausted (max size reached + threads waiting)

**Key features**:
```java
// Get current metrics
PoolMetrics metrics = monitor.getCurrentMetrics();

// Check pool health
boolean healthy = metrics.isHealthy();        // <80% utilization, no waiting
boolean pressure = metrics.isUnderPressure(); // >80% or threads waiting  
boolean exhausted = metrics.isExhausted();    // Max size + threads waiting

// Print detailed statistics
monitor.printDetailedStats();
```

**Testing Results:**
- Test 1: Monitor initialization ✅
- Test 2: Track metrics under load ✅
    - 50 concurrent requests: 4 active, 46 waiting
    - Pool grows on-demand as needed
- Test 3: Detect pool pressure ✅
    - 120 concurrent requests: 52 active, 68 waiting, 100% utilization
    - Successfully detected and logged pool pressure
- Test 4: PoolMetrics helper methods ✅

**Key Discoveries from Tests:**
1. ✅ Monitor successfully tracks real-time metrics
2. ⚠️ Pool only grew to 52 connections (not 100 max) under 120 concurrent requests
3. ⚠️ Leak detection threshold: 0ms (not configured!)
4. ✅ Alerts triggered correctly when pool under pressure
5. ⚠️ 68 threads waiting with 52 active connections = performance bottleneck

**Implications for Phase 2:**
- Current max pool size (100) may be way too high
- Pool is growing on-demand but stopped at 52
- Need to test with different pool sizes to find optimal size
- Should enable leak detection threshold

---

### Phase 2: Optimize Pool Size
- Run tests with different pool sizes (5, 10, 20, 50, 100)
- Measure throughput and latency for each
- Find the sweet spot (likely 20-30)
- Document why that size works

### Phase 3: Fix Connection Management
- Replace manual `closeConnection()` with try-with-resources
- Add defensive checks
- Ensure all code paths return connections

### Phase 4: Enhance HikariCP Configuration
- Add leak detection
- Enable metrics
- Tune timeouts
- Add connection validation

### Phase 5: Identify Bottlenecks Beyond Pool
- Slow queries (add query logging)
- Missing indexes
- N+1 query problems
- Long-running transactions

### Phase 6: Load Testing & Verification
- Re-run all tests with optimizations
- Compare before/after metrics
- Ensure P95/P99 latencies are acceptable
- Document improvements

---

## Key Takeaways So Far

1. **Problem confirmed**: Pool exhaustion causes 3x performance degradation
2. **Pool size 100 is arbitrary**: Not based on system capacity
3. **No visibility**: Can't diagnose issues without monitoring
4. **Leak risk**: Manual connection management is dangerous
5. **Test methodology works**: Load tests successfully reproduce production scenarios

---

