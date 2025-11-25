package com.unravel.challenge.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple repository to test database operations.
 * This simulates typical application queries.
 */
@Slf4j
@Repository
public class TestDataRepository {

    private final DataSource dataSource;

    public TestDataRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Simulate a typical SELECT query
     */
    public List<String> getAllData() throws SQLException {
        String query = "SELECT name, value FROM test_data";
        List<String> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                results.add(rs.getString("name") + ": " + rs.getString("value"));
            }
        }

        return results;
    }

    /**
     * Simulate a query with a sleep (slow query scenario)
     */
    public List<String> getDataWithDelay(long delayMs) throws SQLException {
        String query = "SELECT name, value, SLEEP(?) as delay FROM test_data LIMIT 1";
        List<String> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setDouble(1, delayMs / 1000.0);  // Convert ms to seconds

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("name") + ": " + rs.getString("value"));
                }
            }
        }

        return results;
    }

    /**
     * Simulate an INSERT operation
     */
    public void insertData(String name, String value) throws SQLException {
        String query = "INSERT INTO test_data (name, value) VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, name);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Count total records
     */
    public int countRecords() throws SQLException {
        String query = "SELECT COUNT(*) as total FROM test_data";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        }

        return 0;
    }
}