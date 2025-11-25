# Unravel Backend Challenge - Setup and Execution Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Project Setup](#project-setup)
3. [Database Setup](#database-setup)
4. [Running the Application](#running-the-application)
5. [Running Tests](#running-tests)
6. [Profiling and Monitoring](#profiling-and-monitoring)

---

## Prerequisites

### Required Software
- **Java 21** (JDK 21 or higher)
```bash
  java -version  # Should show version 21+
```

- **Maven 3.8+**
```bash
  mvn -version
```

- **Docker & Docker Compose** (for MySQL database)
```bash
  docker --version
  docker-compose --version
```

- **Git**
```bash
  git --version
```

### Optional Tools (for profiling and analysis)
- **IntelliJ IDEA** (recommended IDE with built-in profiler)
- **VisualVM** or **JProfiler** (for heap dump analysis)
- **jmap** and **jstack** (comes with JDK)

---

## Project Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd unravel-app-challenge
```

### 2. Verify Project Structure
```bash
unravel-app-challenge/
├── src/
│   ├── main/
│   │   ├── java/com/unravel/challenge/
│   │   └── resources/
│   └── test/
│       └── java/com/unravel/challenge/
├── scripts/
│   ├── docker-compose.yml
│   └── schema.sql
├── docs/
│   ├── challenge-1-work-log.md
│   ├── challenge2/
│   ├── challenge-3-work-log.md
│   ├── challenge-4-work-log.md
│   └── challenge-5-work-log.md
├── pom.xml
└── README.md
```

### 3. Build the Project
```bash
mvn clean install
```

This will:
- Compile the code
- Download dependencies
- Run unit tests
- Create the executable JAR

---

## Database Setup

### Start MySQL Database with Docker

1. **Navigate to scripts directory:**
```bash
   cd scripts
```

2. **Start MySQL container:**
```bash
   docker-compose up -d
```

3. **Verify database is running:**
```bash
   docker ps  # Should show mysql-challenge container running
```

4. **Check database initialization:**
```bash
   docker logs mysql-challenge
   # Look for: "MySQL init process done. Ready for start up."
```

5. **Test connection (optional):**
```bash
   docker exec -it mysql-challenge mysql -u dbuser -pdbpassword mydb
   # Inside MySQL:
   SHOW TABLES;  # Should show 'test_data'
   SELECT * FROM test_data;  # Should show 5 test records
   exit;
```

### Database Configuration

The application is pre-configured in `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=dbuser
spring.datasource.password=dbpassword
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=5
```

**Note:** If you need to change the pool size for testing, modify these properties or use the `PoolSizeOptimizationTest` which dynamically adjusts pool size.

### Stop/Restart Database
```bash
# Stop database
docker-compose down

# Stop and remove all data (fresh start)
docker-compose down -v

# Restart database
docker-compose up -d
```

---

## Running the Application

### Run Spring Boot Application
```bash
# From project root
mvn spring-boot:run
```

The application will start on `http://localhost:8080` (default Spring Boot port).

### Run Specific Challenge Demos

The `UnravelChallengeApplication.java` contains commented-out methods for running individual challenges:
```java
public static void main(String[] args) {
    SpringApplication.run(UnravelChallengeApplication.class, args);
    
    // Uncomment to run specific challenge:
    // challenge3WithThreadPools();  // Producer-Consumer
    // challenge4();                  // Deadlock Simulator
}
```

To run a specific challenge:
1. Uncomment the desired method in `main()`
2. Run: `mvn spring-boot:run`

---

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Tests for Specific Challenge

**Challenge 1: Session Management**
```bash
mvn test -Dtest=SessionManagerTest
```

**Challenge 2: Memory Management**
```bash
mvn test -Dtest=LoadSimulationTest
```

**Challenge 3: Producer-Consumer**
```bash
mvn test -Dtest=LogProcessorPriorityTest
```

**Challenge 4: Deadlock Prevention**
```bash
mvn test -Dtest=DeadlockReproductionTest
```

**Challenge 5: Database Connection Pool**
```bash
# Requires database to be running!
mvn test -Dtest=ConnectionPoolMonitorTest
mvn test -Dtest=ConnectionPoolLoadTest
mvn test -Dtest=PoolSizeOptimizationTest
```

### Important Test Notes

1. **Database Tests:** Challenge 5 tests require MySQL to be running (see [Database Setup](#database-setup))

2. **Long-Running Tests:**
   - `PoolSizeOptimizationTest`
   - `LoadSimulationTest`

3. **Test Output:**
   Tests include detailed logging. Check console output for:
   - Performance metrics (latency, throughput)
   - Pool statistics (active/idle connections)
   - Cache statistics (hit rate, evictions)

---

## Profiling and Monitoring

Please, check the `/docs/challenge-X-work-log.md` document to find specific information about this for each challenge.

