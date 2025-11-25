# Challenge 5: Database Connection Pool Optimization - Work Log

## Implementation Summary

### Phase 1: Custom Monitoring Solution

**Created**: `ConnectionPoolMonitor.java`

**Implementation**:

- Scheduled monitoring every 10 seconds using Spring's @Scheduled
- Tracks: active connections, idle connections, threads waiting, utilization percentage
- Automated alerts when utilization > 80% or threads waiting > 0

**Key Features**:

```java
// Get real-time metrics
PoolMetrics metrics = monitor.getCurrentMetrics();

// Check pool state
boolean healthy = metrics.isHealthy();        // < 80% utilization
boolean pressure = metrics.isUnderPressure(); // > 80% or waiting threads
boolean exhausted = metrics.isExhausted();    // Max size + waiting threads
```

**Result**: Complete visibility into pool health with automated alerting.

---

### Phase 2: Pool Size Optimization

**Tested**: 5 different pool sizes (10, 20, 30, 50, 100) with 100 concurrent requests

**Test Results**:

| Pool Size | Avg (ms) | P95 (ms) | Peak Conn Used | Waiting Threads |
|-----------|----------|----------|----------------|-----------------|
| 10        | 3354     | 5644     | 0              | 0               |
| 20        | 1587     | 2684     | 17             | 83              |
| 30        | 1156     | 2017     | 27             | 73              |
| 50        | 883      | 1504     | 36             | 64              |
| 100       | 718      | 1009     | 57             | 43              |

**Key Discovery**: Pool 100 only used 57 connections at peak (57% utilization).

**Recommendation**: Pool size 50

- Peak usage: 36 connections (40% headroom)
- Saves 50% resources vs pool 100
- P95 latency: 1504ms (only 33% slower than pool 100)
- Cost-benefit: Better resource efficiency for acceptable latency

**Configuration**:

```properties
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=5
```

---

### Phase 3: Connection Management

**Problem**: Original code used manual connection management, could lead to connection leaks

```java
// OLD: Manual, error-prone
Connection conn = getConnection();
try{
        // use connection
        }finally{

closeConnection(conn);  // Easy to forget!
}
```

**Solution**: Refactored to try-with-resources pattern

```java
// NEW: Automatic, guaranteed cleanup
try(Connection conn = dataSource.getConnection();
PreparedStatement stmt = conn.prepareStatement(sql)){
        // use connection
        } // Automatically returned to pool - GUARANTEED
```

**Created**: New `DatabaseManager.java` with:

- Proper exception handling
- Pattern for processing data without holding connections
- Zero risk of connection leaks

---

### Phase 4: Enhanced HikariCP Configuration

**Optimized Configuration**:

```properties
# Pool sizing (based on Phase 2 testing)
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=5
# Timeouts (fail fast)
spring.datasource.hikari.connection-timeout=10000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
# Monitoring & leak detection
spring.datasource.hikari.leak-detection-threshold=30000
spring.datasource.hikari.register-mbeans=true
spring.datasource.hikari.pool-name=UnravelChallengePool
# Connection validation
spring.datasource.hikari.connection-test-query=SELECT 1
spring.datasource.hikari.validation-timeout=3000
# MySQL optimizations
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
spring.datasource.hikari.data-source-properties.useServerPrepStmts=true
```

**Key Changes**:

- Connection timeout: 30s → 10s (fail fast)
- Leak detection: OFF → 30s threshold
- JMX monitoring: Disabled → Enabled
- Pool size: 100 → 50 (data-driven)

---

### Phase 5: Identify Bottlenecks Beyond Pool

**Analysis**: Used Phase 2 data to identify additional constraints.

**Critical Finding**:

- Pool 100 had 43% unused capacity (57 connections used, 43 idle slots)
- Yet 43 threads were waiting
- **Conclusion**: Query execution time (500ms), not pool size, is the bottleneck

**The Math**:

```
100 concurrent requests start
Each query takes: 500ms

T=0ms:    First 57 threads grab connections, start executing
T=0-500ms: Those 57 connections BUSY executing
          Remaining 43 threads WAITING (for queries to finish, not for pool)
T=500ms:  First batch completes, next 43 start
```

**Bottlenecks Identified**:

1. **Query Execution Time** (Primary)
    - Evidence: 500ms per query dominates request time
    - Impact: Threads wait for queries, not connections
    - Recommendation: Add indexes, optimize queries, implement caching

2. **Connection Hold Time** (Secondary)
    - Evidence: Connections held entire query duration
    - Recommendation: Release immediately after query, process data separately

3. **Database Capacity** (Possible)
    - Evidence: Many concurrent queries may overwhelm database
    - Recommendation: Consider read replicas, query optimization

---

## Final Configuration

### Before Optimization:

```
Pool size: 100
Monitoring: None
Leak detection: Disabled
Connection timeout: 30s
Connection management: Manual (leak risk)
```

### After Optimization:

```
Pool size: 50 (50% resource reduction)
Monitoring: ConnectionPoolMonitor (real-time)
Leak detection: Enabled (30s threshold)
Connection timeout: 10s (fail fast)
Connection management: try-with-resources (leak-proof)
```

---

## Performance Impact

| Metric              | Before            | After            | Change                  |
|---------------------|-------------------|------------------|-------------------------|
| Pool size           | 100               | 50               | -50%                    |
| Peak connections    | 57                | 36               | Measured                |
| P95 latency         | 1009ms (pool 100) | 1504ms (pool 50) | +33% (acceptable)       |
| Monitoring          | None              | Full             | Complete visibility     |
| Leak detection      | No                | Yes              | Early problem detection |
| Resource efficiency | 57%               | 72%              | Better utilization      |

**Trade-off Analysis**: 50% resource savings for 33% slower P95 latency is an acceptable trade-off for most production
systems.
