package com.unravel.challenge.database.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Monitors HikariCP connection pool health and logs metrics.
 * <p>
 * Tracks:
 * - Active connections (currently being used)
 * - Idle connections (available in pool)
 * - Total connections (active + idle)
 * - Threads waiting for connections
 * - Pool utilization percentage
 * <p>
 * Alerts when:
 * - Pool utilization > 80%
 * - Threads are waiting for connections
 * - Pool is approaching exhaustion
 */
@Slf4j
@Component
@EnableScheduling
public class ConnectionPoolMonitor {

    private final HikariDataSource hikariDataSource;
    private HikariPoolMXBean poolMXBean;

    public ConnectionPoolMonitor(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            log.info("ConnectionPoolMonitor initialized");
        } else {
            throw new IllegalStateException("DataSource is not HikariDataSource");
        }
    }

    /**
     * Get the pool MXBean (lazy initialization)
     */
    private HikariPoolMXBean getPoolMXBean() {
        if (poolMXBean == null) {
            poolMXBean = hikariDataSource.getHikariPoolMXBean();
        }
        return poolMXBean;
    }

    /**
     * Check pool health every 10 seconds
     */
    @Scheduled(fixedRate = 10000)
    public void monitorPoolHealth() {
        try {
            // Skip if pool not yet initialized
            if (getPoolMXBean() == null) {
                log.debug("Pool not yet initialized, skipping monitoring");
                return;
            }

            PoolMetrics metrics = getCurrentMetrics();
            logMetrics(metrics);
            checkAlerts(metrics);
        } catch (Exception e) {
            log.error("Error monitoring connection pool", e);
        }
    }

    /**
     * Get current pool metrics
     */
    public PoolMetrics getCurrentMetrics() {
        HikariPoolMXBean bean = getPoolMXBean();
        if (bean == null) {
            // Pool not initialized yet, return zero metrics
            return new PoolMetrics(0, 0, 0, 0, hikariDataSource.getMaximumPoolSize(), 0.0);
        }

        int active = bean.getActiveConnections();
        int idle = bean.getIdleConnections();
        int total = bean.getTotalConnections();
        int waiting = bean.getThreadsAwaitingConnection();
        int maxPoolSize = hikariDataSource.getMaximumPoolSize();

        double utilizationPercent = total > 0 ? (double) active / total * 100 : 0;

        return new PoolMetrics(active, idle, total, waiting, maxPoolSize, utilizationPercent);
    }

    /**
     * Log current metrics (DEBUG level to avoid spam)
     */
    private void logMetrics(PoolMetrics metrics) {
        log.debug("Pool Status: {}/{} active, {} idle, {} waiting, {}/{} total ({}% utilized)",
                metrics.active,
                metrics.maxPoolSize,
                metrics.idle,
                metrics.waiting,
                metrics.total,
                metrics.maxPoolSize,
                String.format("%.1f", metrics.utilizationPercent));
    }

    /**
     * Check for alert conditions
     */
    private void checkAlerts(PoolMetrics metrics) {
        // Alert 1: High utilization (> 80%)
        if (metrics.utilizationPercent > 80) {
            log.warn("HIGH POOL UTILIZATION: {}% ({}/{} connections active)",
                    String.format("%.1f", metrics.utilizationPercent),
                    metrics.active,
                    metrics.total);
        }

        // Alert 2: Threads waiting for connections
        if (metrics.waiting > 0) {
            log.warn("THREADS WAITING FOR CONNECTIONS: {} threads blocked",
                    metrics.waiting);
        }

        // Alert 3: Pool approaching max size
        if (metrics.total >= metrics.maxPoolSize * 0.9) {
            log.warn("POOL APPROACHING MAX SIZE: {}/{} connections",
                    metrics.total,
                    metrics.maxPoolSize);
        }

        // Alert 4: Pool exhausted
        if (metrics.total >= metrics.maxPoolSize && metrics.waiting > 0) {
            log.error("POOL EXHAUSTED: All {} connections in use, {} threads waiting!",
                    metrics.maxPoolSize,
                    metrics.waiting);
        }
    }

    /**
     * Print detailed statistics (call manually for debugging)
     */
    public void printDetailedStats() {
        PoolMetrics metrics = getCurrentMetrics();

        log.info("=== CONNECTION POOL STATISTICS ===");
        log.info("Pool Name: {}", hikariDataSource.getPoolName());
        log.info("Active Connections: {}", metrics.active);
        log.info("Idle Connections: {}", metrics.idle);
        log.info("Total Connections: {}", metrics.total);
        log.info("Max Pool Size: {}", metrics.maxPoolSize);
        log.info("Threads Waiting: {}", metrics.waiting);
        log.info("Utilization: {}%", String.format("%.2f", metrics.utilizationPercent));
        log.info("Connection Timeout: {}ms", hikariDataSource.getConnectionTimeout());
        log.info("Idle Timeout: {}ms", hikariDataSource.getIdleTimeout());
        log.info("Max Lifetime: {}ms", hikariDataSource.getMaxLifetime());
        log.info("Leak Detection Threshold: {}ms", hikariDataSource.getLeakDetectionThreshold());
    }

    /**
     * Data class for pool metrics
     */
    public record PoolMetrics(
            int active,
            int idle,
            int total,
            int waiting,
            int maxPoolSize,
            double utilizationPercent
    ) {
        public boolean isHealthy() {
            return utilizationPercent < 80 && waiting == 0;
        }

        public boolean isUnderPressure() {
            return utilizationPercent > 80 || waiting > 0;
        }

        public boolean isExhausted() {
            return total >= maxPoolSize && waiting > 0;
        }
    }
}