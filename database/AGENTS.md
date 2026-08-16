
# database — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `database` module is a parent POM (packaging=pom) that groups all database protocol sub-modules under a single build hierarchy. It sits between the root `lego-flow` POM and the individual database modules.

## Module Structure

```
database/                        <- parent POM (lego-flow-database)
  redis/                         <- Redis protocol client and codec
  postgresql/                    <- PostgreSQL v3 wire protocol
  mysql/                         <- MySQL client/server wire protocol
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-database (database/pom.xml)
      -> lego-flow-redis (database/redis/pom.xml)
      -> lego-flow-postgresql (database/postgresql/pom.xml)
      -> lego-flow-mysql (database/mysql/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| redis | 21 |
| postgresql | 20 |
| mysql | 27 |

## Build Commands

```bash
# Build all database modules
mvn test -pl database/redis,database/postgresql,database/mysql -am

# Build single module
mvn test -pl database/redis -am
```
