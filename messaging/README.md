
# Lego Flow Messaging — Messaging Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for messaging protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [kafka](kafka/) | `lego-flow-kafka` | Apache Kafka wire protocol |
| [amqp](amqp/) | `lego-flow-amqp` | AMQP 1.0 (ISO 19464) |
| [stomp](stomp/) | `lego-flow-stomp` | STOMP messaging |
| [nats](nats/) | `lego-flow-nats` | NATS cloud-native messaging |
| [mqtt](mqtt/) | `lego-flow-mqtt` | MQTT messaging |
| [xmpp](xmpp/) | `lego-flow-xmpp` | XMPP with IoT extensions |
| [wamp](wamp/) | `lego-flow-wamp` | WAMP — Web Application Messaging |

## Test Coverage

| Module | Test Files |
|--------|------------|
| kafka | 25 |
| amqp | 18 |
| stomp | 13 |
| nats | 28 |
| mqtt | 21 |
| xmpp | 33 |
| wamp | 32 |
| **Total** | **170** |

## Build Commands

```bash
# Build all messaging modules
mvn test -pl messaging/kafka,messaging/amqp,messaging/stomp,messaging/nats,messaging/mqtt,messaging/xmpp,messaging/wamp -am

# Gradle
./gradlew :messaging:kafka:test :messaging:amqp:test
```
