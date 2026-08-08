
# Lego Flow MQTT Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-193-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)]()

MQTT protocol module for the Lego Flow framework, providing publish/subscribe messaging with broker and client implementations.

## Overview

This module implements MQTT v3.1.1 (OASIS) and v5.0 protocols, enabling Java applications to build message brokers and clients for IoT and real-time messaging. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
MQTT Client / Broker (application layer)
  → Session Management (persistent/clean sessions, message queuing)
    → Topic Engine (topic tree, wildcard matching, retained messages)
      → QoS State Machines (QoS 0/1/2 delivery flows)
        → Packet Codec (binary encode/decode for all 15 packet types)
          → TCP Transport (service module channels)
```

## Features

- **MQTT v3.1.1 and v5.0** — dual protocol version support with version negotiation
- **Publish/Subscribe** — topic-based message routing with decoupled producers and consumers
- **QoS 0/1/2** — at most once, at least once, exactly once delivery guarantees
- **Topic wildcards** — single-level (+) and multi-level (#) subscription filters
- **Retained messages** — last message per topic stored and delivered to new subscribers
- **Last Will and Testament** — automatic message on ungraceful disconnect
- **Broker** — topic tree routing, session persistence, subscription management, client registry
- **Client** — auto-reconnect, keep-alive (PINGREQ/PINGRESP), inflight message tracking
- **Packet codec** — all 15 MQTT control packet types (CONNECT, CONNACK, PUBLISH, PUBACK, PUBREC, PUBREL, PUBCOMP, SUBSCRIBE, SUBACK, UNSUBSCRIBE, UNSUBACK, PINGREQ, PINGRESP, DISCONNECT, AUTH)
- **Dual API** — sync + async (CompletableFuture), procedural + functional styles

## Quick Start

### Start a broker

```java
var broker = MqttBroker.builder()
    .port(1883)
    .maxConnections(1000)
    .build();
broker.start();
```

### Connect a client and subscribe

```java
var client = MqttClient.builder()
    .host("localhost").port(1883)
    .clientId("sensor-01")
    .cleanSession(true)
    .build();
client.connect();
client.subscribe("home/temperature", QoS.AT_LEAST_ONCE, msg ->
    System.out.println("Temperature: " + msg.payloadAsString()));
```

### Publish a message

```java
client.publish("home/temperature", "22.5".getBytes(), QoS.AT_LEAST_ONCE, false);
```

### Retained message and last will

```java
var client = MqttClient.builder()
    .host("localhost").port(1883)
    .clientId("thermostat")
    .willTopic("home/thermostat/status")
    .willPayload("offline".getBytes())
    .willQos(QoS.AT_LEAST_ONCE)
    .willRetain(true)
    .build();
client.connect();
client.publish("home/thermostat/status", "online".getBytes(), QoS.AT_LEAST_ONCE, true);
```

## Package Structure

```
ssg.legoflow.mqtt/
├── broker/            — Broker implementation: client registry, session store, topic tree routing
├── client/            — Client implementation: connect, subscribe, publish, auto-reconnect
├── codec/             — Packet codec: encode/decode all 15 MQTT control packet types
├── qos/               — QoS state machines: QoS 0/1/2 delivery flows, inflight tracking
├── session/           — Session management: persistent/clean sessions, message queuing
├── topic/             — Topic engine: topic tree, wildcard matching (+, #), retained messages
├── will/              — Last Will and Testament: will message storage and delivery
└── demo/              — Demo applications and examples
```

## Demo Applications

1. **SimpleBrokerDemo** — Starts an MQTT broker, accepts connections, routes messages
2. **PubSubDemo** — Client publish/subscribe with QoS levels and topic wildcards
3. **RetainedMessageDemo** — Demonstrates retained messages and last will behavior
4. **SessionPersistenceDemo** — Clean vs persistent sessions with offline message queuing
5. **MultiClientDemo** — Multiple clients with different QoS levels and topic patterns

## Dependencies

This module depends on:
- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
