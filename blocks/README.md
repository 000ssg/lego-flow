
# Lego Flow Blocks — Core Data Processing Framework

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)]()

Core data processing framework providing composable DP/DF building blocks with context, state management, and statistics tracking.

## Key Abstractions

- **`DataProcessor<I,O>`** — bidirectional data processor with consume/produce/accept/submit
- **`DataFilter<T>`** — positioned filter for data transformation/validation
- **`Context`** — logging, statistics, error handling, attributes
- **`ProcessorState`** — IDLE, CONNECTING, READY, PAUSED, FAILED, STOPPED
- **`ProcessorStatistics`** — count/amount tracking per type

## Usage

```java
var processor = new StringToIntProcessor();
var ctx = new DefaultContext();

// Add filters
processor.addInputFilter(new ValidationFilter<>(String.class, s -> !s.isBlank()));
processor.addOutputFilter(new ValidationFilter<>(Integer.class, i -> i > 0));

// Process data
processor.consume(ctx, "42", "invalid", "7");
// → accept() receives [42, 7]

// Reverse direction
processor.submit(ctx, 100);
// → produce() receives ["100"]
```

## Build

```bash
mvn compile -pl blocks -am
mvn test -pl blocks
```

## Documentation

- [Code Overview](doc/CODE_OVERVIEW.md) | [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
- [Root README](../README.md) | [Root Code Overview](../doc/CODE_OVERVIEW.md) | [Root Architecture](../doc/ARCHITECTURE.md)
