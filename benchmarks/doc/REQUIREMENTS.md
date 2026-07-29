# Benchmarks Module — Requirements Evolution

## Module Timeline Overview

- **Start Date**: 2026-07-29
- **Total Tests**: 0 (benchmarking module — no unit tests, benchmark assertions only)
- **Purpose**: JMH microbenchmarks for protocol throughput, latency, and serialization performance

---

## Table of Contents

- [Module Timeline Overview](#module-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Initial Commit — CI/CD Pipeline, JMH Benchmarks, Protocol Interoperability Tests](#commit-long-term-enhancements-2026-07-29)

---

## Initial Commit — CI/CD Pipeline, JMH Benchmarks, Protocol Interoperability Tests

### Original Request
> "Apply improvements from section 'Long-Term Enhancements' described in attached document including LT-1 (CI/CD pipeline), LT-2 (JMH benchmark suite), and LT-3 (protocol interoperability tests)."

### Reformulated Requirements

1. **LT-1: CI/CD Pipeline with protocol-specific test matrices**
   - GitHub Actions workflow with build/test matrix (ubuntu + macos, Java 25)
   - Maven dependency caching for faster builds
   - JaCoCo code coverage reporting with threshold gates
   - Protocol-specific test profiles (web-only, messaging-only, network-only, auth-only, database-only)
   - Docker-based interoperability services

2. **LT-2: Cross-module performance benchmark suite (JMH)**
   - HTTP request/response throughput benchmarks (small/medium/large payloads)
   - Message queue publish/subscribe latency benchmarks (MQTT QoS 0/1/2)
   - Authentication handshake performance benchmarks
   - Protocol serialization/deserialization speed comparison (HTTP vs RESP codecs)
   - Non-blocking CI gates that warn on regression

3. **LT-3: Protocol interoperability test suite**
   - HTTP/HTTPS tests against real nginx server
   - MQTT tests against Mosquitto broker
   - Redis tests against real Redis server
   - PostgreSQL wire protocol tests against real PostgreSQL instance
   - Docker Compose infrastructure for CI service containers

### Final Design Decisions

- **Benchmarks as separate module**: Excluded from install/deploy lifecycle per proposal; fat JAR via shade plugin equivalent in Gradle
- **4 benchmark categories**: HTTP throughput, MQTT latency, auth handshake, codec serialization — covering the 3 most critical protocol paths
- **JMH annotation processor**: Required for generating benchmark runner classes at compile time
- **Docker Compose for interop tests**: Lightweight container infrastructure with health checks; configurable via system properties
- **Gradle parity**: `./gradlew :benchmarks:runBenchmarks --args=".*HttpThroughputBenchmark.*"` mirrors Maven's `java -jar benchmarks/target/...jar`
- **System property injection**: Docker target addresses configurable via `-Dinterop.nginx.host=...` for both Maven and Gradle

### Implementation Details

#### Benchmarks Module (benchmarks/)
- `pom.xml`: 151 lines — JMH dependency management, shade plugin, exclude-from-publish config
- `build.gradle.kts`: Mirrors Maven pom with annotation processor config, fat JAR packaging, runBenchmarks task
- `HttpThroughputBenchmark.java`: 123 lines — 6 benchmark methods covering small/medium/large serialization and roundtrip
- `MqttLatencyBenchmark.java`: 145 lines — 7 benchmark methods for CONNECT, PUBLISH QoS 0/1/2, encode-only, roundtrip
- `AuthHandshakeBenchmark.java`: 124 lines — 6 benchmark methods for validation, challenge flow, credential extraction, full handshake
- `CodecSerializationBenchmark.java`: 203 lines — HTTP vs RESP codec comparison (encode, decode, roundtrip)

#### Interop Tests Module (interop-tests/)
- `pom.xml`: 121 lines — protocol module test dependencies, failsafe plugin with system property config
- `build.gradle.kts`: Mirrors Maven pom with test dependency injection and system property configuration
- `docker-compose.yml`: 76 lines — nginx, mosquitto, redis, postgresql services with health checks
- `docker/nginx.conf`: 28 lines — custom endpoints for HTTP protocol testing (/health, /api/data, /echo)
- `docker/mosquitto.conf`: 12 lines — anonymous auth, logging enabled
- `HttpNginxInteropTest.java`: 133 lines — 4 tests (health, JSON API, echo, HTML homepage)
- `MqttMosquittoInteropTest.java`: 165 lines — 4 tests (connect/disconnect, publish/subscribe, wildcards, multi-topic)
- `RedisInteropTest.java`: 174 lines — 7 tests (PING, SET/GET, INCR, hashes, lists, KEYS, TTL)
- `PostgresqlInteropTest.java`: 154 lines — 6 tests (version, DDL/DML, aggregates, transactions, database listing, connection params)

### Test Coverage

- **Benchmarks**: 0 unit tests (benchmarking module — uses JMH assertions only)
- **Interop Tests**: 21 integration test methods across 4 test classes
- **Total new benchmark methods**: 26 (across 4 benchmark classes)

### Cost Estimate

| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~80,000 |
| Agent tool calls | ~95 |
| Agent wall time | ~120 min |
| Files created/modified | 18 |
| Lines added/removed | +2,141 / -0 |
| Tests added | 21 interop tests (total: 8,603) |

---
