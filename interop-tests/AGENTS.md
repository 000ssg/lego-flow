
# interop-tests — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `interop-tests` module provides interoperability tests against reference implementations (nginx, mosquitto, redis, postgres). Validates that Lego Flow protocol implementations work correctly with production-grade servers and clients.

## Key Notes

- **Maven-only** — No build.gradle.kts; uses Docker Compose for reference servers
- **No COMPLIANCE.md required** — Tests validate interoperability, not spec compliance
- **Requires Docker** — Reference servers run via Docker Compose
- **Excluded from default builds** — Run explicitly via `mvn test -pl interop-tests`

## Reference Servers

| Protocol | Server | Port |
|----------|--------|------|
| HTTP | nginx:alpine | 8080 |
| MQTT | eclipse-mosquitto | 1883 |
| Redis | redis:7-alpine | 6379 |
| PostgreSQL | postgres:17-alpine | 5432 |
