# Lego Flow Benchmarks

JMH-based microbenchmarks for protocol throughput, latency, and serialization performance.

## Running Benchmarks

### Build the benchmark JAR

```bash
mvn package -pl benchmarks -am -DskipTests
```

### Run all benchmarks

```bash
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar
```

### Run specific benchmark category

```bash
# HTTP throughput only
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*HttpThroughputBenchmark.*"

# MQTT latency only  
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*MqttLatencyBenchmark.*"

# Auth handshake only
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*AuthHandshakeBenchmark.*"

# Codec serialization only
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*CodecSerializationBenchmark.*"
```

## Benchmark Suite

### HTTP Throughput (`HttpThroughputBenchmark`)
- **Metrics**: Operations per millisecond (throughput)
- **Tests**: Request/response serialization, small/medium/large payloads, roundtrip latency
- **Purpose**: Measures HTTP protocol encoding/decoding performance under various payload sizes

### MQTT Latency (`MqttLatencyBenchmark`)  
- **Metrics**: Average time in microseconds (latency)
- **Tests**: CONNECT, PUBLISH (QoS 0/1/2), encode-only, roundtrip encode+decode
- **Purpose**: Measures MQTT packet encoding/decoding latency for IoT message patterns

### Auth Handshake (`AuthHandshakeBenchmark`)
- **Metrics**: Average time in microseconds (latency)  
- **Tests**: Basic auth validation, challenge flow, credential extraction, full handshake
- **Purpose**: Measures per-request authentication overhead against 100-user store

### Codec Serialization (`CodecSerializationBenchmark`)
- **Metrics**: Operations per millisecond (throughput)
- **Tests**: HTTP vs RESP codec serialization/deserialization, roundtrip comparisons
- **Purpose**: Compares wire-format encoding performance across different protocol codecs

## Verifying Results

### Maven path

```bash
# Build the benchmark JAR
mvn package -pl benchmarks -am -DskipTests

# Run all benchmarks — output shows ops/ms and µs/op per method:
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar
# Expected: each benchmark prints throughput (ops/ms) or latency (µs/op) with 95% confidence intervals

# Run only specific category:
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*HttpThroughputBenchmark.*"

# Run a single benchmark method:
java -jar benchmarks/target/lego-flow-benchmarks-0.1.0-SNAPSHOT.jar ".*serializeSmallRequest"
```

### Gradle path (mirrors Maven above)

```bash
# Compile benchmarks + dependencies:
./gradlew :benchmarks:classes

# Run all benchmarks:
./gradlew :benchmarks:runBenchmarks
# Expected: same JMH output as Maven path — throughput/latency per method

# Run specific category (pass --args filter):
./gradlew :benchmarks:runBenchmarks --args=".*HttpThroughputBenchmark.*"
```

### Interpreting Results

JMH reports include:
- **Score**: mean throughput (ops/ms) or latency (µs/op)
- **Error**: margin of error at 95% confidence level
- **Mode label**: thrpt = throughput, avgt = average time
- **Benchmark name**: fully qualified class method name

Compare scores across commits by examining the Score column. Lower µs values are better for latency benchmarks; higher ops/ms values are better for throughput benchmarks.

## CI Integration

Benchmarks are excluded from the install/deploy lifecycle. They run as non-blocking
CI gates that warn on regression without failing the build.

### Viewing Results in CI

Benchmark results are published as artifacts in GitHub Actions runs under the
`benchmarks` artifact name. Compare results across builds to track performance trends.
