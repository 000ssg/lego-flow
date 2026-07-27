
# Lego Flow XMPP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-268-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)]()

XMPP protocol module for the Lego Flow framework, providing presence, messaging, roster management, and IoT extensions.

## Overview

This module implements XMPP core (RFC 6120) and XMPP Instant Messaging (RFC 6121) with IoT extensions, enabling Java applications to build real-time communication and machine-to-machine systems. The architecture layers application protocols on top of XML stream handling:

```
IoT Extensions (XEP-0323/0325/0347: sensor data, control, discovery)
  → IM Features (presence, messaging, roster, MUC)
    → Stanza Processing (message, presence, iq routing and handling)
      → Stream Management (XEP-0198: acks, resumption)
        → SASL Authentication + TLS Negotiation
          → XML Stream (open/close, feature negotiation)
            → TCP Transport (service module channels)
```

## Features

- **XMPP Core (RFC 6120)** — XML stream establishment, TLS negotiation, SASL authentication, resource binding
- **XMPP IM (RFC 6121)** — presence (available, away, dnd, xa), messaging, roster with subscription states
- **IoT Sensor Data (XEP-0323)** — read sensor values, momentary/historical data requests
- **IoT Control (XEP-0325)** — set actuator values, control commands with confirmation
- **IoT Discovery (XEP-0347)** — device registration, discovery, and provisioning
- **Multi-User Chat (XEP-0045)** — room creation, join/leave, occupant management, room messages
- **Publish-Subscribe (XEP-0060)** — node creation, item publish, subscription management
- **Stream Management (XEP-0198)** — stanza acknowledgement, session resumption after disconnect
- **Stanza types** — message (chat, groupchat, headline), presence, iq (get, set, result, error)
- **Dual API** — sync + async (CompletableFuture), procedural + functional styles

## Quick Start

### Connect and send a message

```java
var client = XmppClient.builder()
    .host("localhost").port(5222)
    .jid("alice@example.com")
    .password("secret")
    .build();
client.connect();
client.sendMessage("bob@example.com", "Hello from Lego Flow!");
```

### Manage presence

```java
client.setPresence(PresenceShow.AVAILABLE, "Online and ready");
client.onPresence(presence ->
    System.out.println(presence.from() + " is " + presence.show()));
```

### Read IoT sensor data (XEP-0323)

```java
var sensorData = client.iot().readSensorData("sensor@example.com");
sensorData.fields().forEach(field ->
    System.out.println(field.name() + " = " + field.value() + " " + field.unit()));
```

### Control an IoT device (XEP-0325)

```java
client.iot().controlSet("actuator@example.com",
    ControlField.of("targetTemperature", 22.5));
```

## Package Structure

```
ssg.legoflow.xmpp/
├── stream/            — XML stream lifecycle: open, features, close, error handling
├── auth/              — SASL authentication (PLAIN, SCRAM-SHA-1/256), TLS negotiation
├── stanza/            — Stanza model: message, presence, iq with type/from/to/id
├── roster/            — Roster management: contact list, subscription states, groups
├── presence/          — Presence engine: show, status, priority, directed presence
├── muc/               — Multi-User Chat (XEP-0045): rooms, occupants, room messages
├── pubsub/            — Publish-Subscribe (XEP-0060): nodes, items, subscriptions
├── iot/               — IoT extensions: sensor data (XEP-0323), control (XEP-0325), discovery (XEP-0347)
├── sm/                — Stream Management (XEP-0198): acks, resumption, unacknowledged stanza queue
└── demo/              — Demo applications and examples
```

## Demo Applications

1. **SimpleClientDemo** — Connect, authenticate, send/receive messages
2. **PresenceDemo** — Presence management with roster subscriptions and status updates
3. **MultiUserChatDemo** — Create/join chat rooms, send room messages, manage occupants
4. **IoTSensorDemo** — Register an IoT sensor, publish data, respond to read requests
5. **IoTControlDemo** — Register an actuator, handle control commands, confirm actions

## Dependencies

This module depends on:
- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
