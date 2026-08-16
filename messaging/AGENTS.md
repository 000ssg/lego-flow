
# messaging — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `messaging` module is a parent POM (packaging=pom) that groups all messaging protocol sub-modules under a single build hierarchy.

## Module Structure

```
messaging/                       <- parent POM (lego-flow-messaging)
  kafka/                         <- Apache Kafka wire protocol
  amqp/                          <- AMQP 1.0 (ISO 19464)
  stomp/                         <- STOMP messaging
  nats/                          <- NATS cloud-native messaging
  mqtt/                          <- MQTT messaging
  xmpp/                          <- XMPP with IoT extensions
  wamp/                          <- WAMP — Web Application Messaging Protocol
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-messaging (messaging/pom.xml)
      -> lego-flow-kafka (messaging/kafka/pom.xml)
      -> lego-flow-amqp (messaging/amqp/pom.xml)
      -> lego-flow-stomp (messaging/stomp/pom.xml)
      -> lego-flow-nats (messaging/nats/pom.xml)
      -> lego-flow-mqtt (messaging/mqtt/pom.xml)
      -> lego-flow-xmpp (messaging/xmpp/pom.xml)
      -> lego-flow-wamp (messaging/wamp/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| kafka | 25 |
| amqp | 18 |
| stomp | 13 |
| nats | 28 |
| mqtt | 21 |
| xmpp | 33 |
| wamp | 32 |

## Build Commands

```bash
# Build all messaging modules
mvn test -pl messaging/kafka,messaging/amqp,messaging/stomp,messaging/nats,messaging/mqtt,messaging/xmpp,messaging/wamp -am

# Build single module
mvn test -pl messaging/nats -am
```
