
# Lego Flow Demos

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

End-to-end integration demos exercising cross-module scenarios in the Lego Flow framework.

## Demo Categories

| Category | Description |
|----------|-------------|
| **Cluster** | Multi-node clustering (partition tolerance, cache coherence, leader election, discovery) |
| **Messaging** | Cross-protocol messaging (NATS, Kafka, AMQP, MQTT) |
| **HTTP** | Server-client integration, load balancing, sticky sessions |
| **Protocol** | Protocol-specific demos (SIP, RTP, DNS, Redis, etc.) |

## Running Demos

```bash
# Run all demos via Gradle
./gradlew :demos:test

# Run specific demo
./gradlew :demos:test --tests "*PartitionToleranceDemoTest*"
```

## Note

This module is a development/test harness. No library code is published.
