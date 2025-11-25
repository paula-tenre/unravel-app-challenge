package com.unravel.challenge.database;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify DatabaseManager properly uses try-with-resources
 * and connections are returned to pool.
 */
@Slf4j
@SpringBootTest
class DatabaseManagerTest {

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private TestDataRepository testDataRepository;

    @Test
    void testExecuteQuery() throws Exception {
        List<String> results = databaseManager.executeQuery("SELECT name FROM test_data LIMIT 3");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        log.info("Query returned {} results", results.size());
    }

    @Test
    void testExecuteQueryWithParams() throws Exception {
        List<String> results = databaseManager.executeQueryWithParams(
                "SELECT name FROM test_data WHERE id = ?",
                1
        );

        assertNotNull(results);
        log.info("Query with params returned {} results", results.size());
    }

    @Test
    void testMultipleOperationsReturnConnectionsToPool() throws Exception {
        // Execute 10 operations sequentially
        for (int i = 0; i < 10; i++) {
            testDataRepository.getAllData();
        }

        // If connections weren't returned, pool would be exhausted
        // This should still work
        List<String> results = testDataRepository.getAllData();
        assertNotNull(results);

        log.info("All operations completed successfully - connections properly returned to pool");
    }

    @Test
    void testExceptionDoesNotLeakConnection() {
        // This should throw exception but not leak connection
        assertThrows(RuntimeException.class, () -> {
            databaseManager.executeQuery("SELECT * FROM non_existent_table");
        });

        // Pool should still be healthy
        List<String> results = databaseManager.executeQuery("SELECT name FROM test_data LIMIT 1");
        assertNotNull(results);

        log.info("Exception handled correctly - no connection leak");
    }
}