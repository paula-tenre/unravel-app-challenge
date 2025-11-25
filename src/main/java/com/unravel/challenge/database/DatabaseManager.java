package com.unravel.challenge.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-ready DatabaseManager using try-with-resources pattern.
 * <p>
 * IMPROVEMENTS FROM ORIGINAL:
 * 1. Removed getConnection()/closeConnection() methods
 * 2. All methods use try-with-resources for automatic cleanup
 * 3. Connections GUARANTEED to return to pool
 * 4. No risk of connection leaks
 */
@Slf4j
@Component
public class DatabaseManager {

    private final DataSource dataSource;

    public DatabaseManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Execute a SELECT query and return results.
     */
    public List<String> executeQuery(String sql) {
        List<String> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(rs.getString(1));
            }

        } catch (SQLException e) {
            log.error("Query execution failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }

        return results;
    }

    /**
     * Execute a query with parameters.
     */
    public List<String> executeQueryWithParams(String sql, Object... params) {
        List<String> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString(1));
                }
            }

        } catch (SQLException e) {
            log.error("Query with params failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }

        return results;
    }
}