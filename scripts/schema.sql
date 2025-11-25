DROP TABLE IF EXISTS test_data;

CREATE TABLE test_data
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    value      VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX      idx_name (name)
);

-- Insert test data
INSERT INTO test_data (name, value)
VALUES ('test1', 'value1'),
       ('test2', 'value2'),
       ('test3', 'value3'),
       ('test4', 'value4'),
       ('test5', 'value5');