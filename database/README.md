
# Lego Flow Database — Database Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for database protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [redis](redis/) | `lego-flow-redis` | Redis protocol client/codec |
| [postgresql](postgresql/) | `lego-flow-postgresql` | PostgreSQL v3 wire protocol |
| [mysql](mysql/) | `lego-flow-mysql` | MySQL client/server wire protocol |

## Test Coverage

| Module | Test Files |
|--------|------------|
| redis | 21 |
| postgresql | 20 |
| mysql | 27 |
| **Total** | **68** |

## Build Commands

```bash
# Build all database modules
mvn test -pl database/redis,database/postgresql,database/mysql -am

# Gradle
./gradlew :database:redis:test :database:postgresql:test :database:mysql:test
```
