
# benchmarks — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `benchmarks` module provides JMH-based microbenchmarks for protocol throughput, latency, and serialization performance. Benchmarks run as non-blocking CI gates that warn on regression without failing the build.

## Key Notes

- **Maven-only** — No build.gradle.kts; excluded from standard Gradle module tree
- **Structurally broken for Maven** — Dependency mismatches are pre-existing; excluded from `mvn` builds via `-pl '!benchmarks'`
- **No COMPLIANCE.md required** — This is a benchmark harness, not a protocol implementation
- **No test-scoped dependencies** — Benchmarks use JMH, not JUnit

## Running Benchmarks

```bash
# Via Maven (produces shaded JAR)
mvn -pl benchmarks package -DskipTests -q
java -jar benchmarks/target/lego-flow-benchmarks-0.2.0-SNAPSHOT.jar ".*ComparisonBenchmark.*"

# Via Gradle
./gradlew :benchmarks:runBenchmarks --args=".*HttpComparisonBenchmark.*"
```

## Benchmark Conventions

- Use `@BenchmarkMode(Mode.Throughput)` and `@BenchmarkMode(Mode.AverageTime)` as appropriate
- Warmup: 5 iterations, 1s each; Measurement: 10 iterations, 1s each
- Fork: 1 to isolate JVM state
- Always benchmark both sync and async paths where applicable
