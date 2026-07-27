
# Lego Flow WAMP — Web Application Messaging Protocol

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Tests](https://img.shields.io/badge/Tests-295_passing-brightgreen.svg)]()

WAMP protocol implementation with a two-layer architecture: transport-agnostic invariant core and pluggable WebSocket adapter.

## Features

### Basic Profile
- **RPC** — Caller/Callee roles with procedure registration and invocation
- **Pub/Sub** — Publisher/Subscriber roles with topic-based event routing
- **Router** — Combined Broker (pub/sub) + Dealer (RPC) message routing
- **Realms** — Isolated routing domains with session management
- **Session Management** — Full WAMP session lifecycle (HELLO/WELCOME/GOODBYE)
- **WebSocket Adapter** — Subprotocol negotiation (`wamp.2.json`, `wamp.2.msgpack`, `wamp.2.cbor`), bidirectional frame wiring, ENCODE/DECODE filter, virtual-thread session service
- **Transport SPI** — Pluggable transport layer for future adapters (raw TCP, long-polling)
- **Dual API** — Sync + async, procedural + functional

### Binary Serialization (from scratch, no external libraries)
- **MessagePack** — Full MessagePack spec: fixint, uint/int 8-64, fixstr/str8-32, bin8-32, fixarray/array16-32, fixmap/map16-32, nil, bool, float32/64
- **CBOR** — RFC 8949 encoding: major types 0-7, nested structures, half/single/double precision floats

### Advanced Profile
- **Pattern-based subscriptions** — Prefix matching and wildcard matching with `match` option
- **Progressive call results** — Streaming RPC results with `progress: true`
- **Call cancellation** — CANCEL/INTERRUPT messages with skip, kill, killnowait modes
- **Caller identification** — Disclose caller session ID via `disclose_me` option
- **Publisher identification** — Disclose publisher session ID to subscribers
- **Subscriber black/white listing** — `eligible` (whitelist) and `exclude` (blacklist) by session ID
- **Publisher exclusion** — `exclude_me` option (default true)
- **Session meta events** — `wamp.session.on_join`, `wamp.session.on_leave`
- **Session meta procedures** — `wamp.session.count`, `wamp.session.list`, `wamp.session.get`
- **WAMP-CRA authentication** — HMAC-SHA256 challenge-response
- **Ticket authentication** — Simple token-based auth
- **Cryptosign authentication** — Ed25519 digital signatures
- **Authorization** — Role-based permissions: canPublish, canSubscribe, canCall, canRegister
- **Event retention** — Retained events delivered to new subscribers
- **Shared registrations** — Load-balancing policies: single, first, last, roundrobin, random

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     WebSocket Adapter                        │  adapter.websocket package
│  WampWebSocketHandler  — HTTP Upgrade, wamp.2.json           │
│  WampWebSocketFilter   — ENCODE/DECODE (AbstractDataFilter)  │
│  WebSocketWampTransport — bidirectional frame wiring         │
│                           onFrame / injectFrame / tryReceive │
│  WebSocketWampService  — session lifecycle, virtual threads  │
├──────────────────────────────────────────────────────────────┤
│                      Invariant Core                          │  core package
│  Messages ─ Sessions ─ Router ─ Realms                      │
│  Caller ─ Callee ─ Publisher ─ Subscriber                   │
│  WampTransport SPI ─ InMemoryTransport (testing)            │
└──────────────────────────────────────────────────────────────┘
```

The **core** layer contains pure WAMP protocol logic with no transport dependency. The **adapter** layer bridges the core to WebSocket: `WampWebSocketHandler` negotiates the `wamp.2.json` subprotocol during HTTP Upgrade; `WampWebSocketFilter` handles ENCODE/DECODE of WAMP JSON in WebSocket frames; `WebSocketWampTransport` provides bidirectional wiring with test hooks (`injectFrame`, `tryReceive`); `WebSocketWampService` drives the session lifecycle on virtual threads. New transports can be added by implementing the `WampTransport` SPI.

## Quick Start

### Simple RPC (Caller/Callee)

```java
var router = new WampRouter();
var realm = router.getOrCreateRealm("default");

// Register a procedure (Callee)
realm.register("com.example.add", (args) -> {
    int a = (int) args[0];
    int b = (int) args[1];
    return a + b;
});

// Call the procedure (Caller)
var result = realm.call("com.example.add", 3, 4);
// result == 7
```

### Simple Pub/Sub (Publisher/Subscriber)

```java
var router = new WampRouter();
var realm = router.getOrCreateRealm("default");

// Subscribe to a topic
realm.subscribe("com.example.events", (event) -> {
    System.out.println("Received: " + event);
});

// Publish an event
realm.publish("com.example.events", "Hello, WAMP!");
// Subscriber prints: Received: Hello, WAMP!
```

## Demos

### In-process demos (`demo/base/`) — use InMemoryTransport, no network required

| Demo | Description |
|---|---|
| `MultiRealmDemo` | Realm isolation: overlapping topic/procedure names in two independent realms |
| `CalculatorServiceDemo` | RPC with add/subtract/multiply/divide and divide-by-zero error handling |
| `ChatRoomDemo` | Pub/Sub fan-out with multiple subscribers and explicit unsubscribe lifecycle |

### WebSocket demos (`demo/websocket/`) — full adapter stack

| Demo | Description |
|---|---|
| `WsRpcDemo` | RPC call/response over a real WebSocket transport |
| `WsPubSubDemo` | Pub/Sub with two independent subscribers over WebSocket |
| `FullWampServerDemo` | Multi-realm concurrent RPC + Pub/Sub (stress/integration demo) |

Each demo has a corresponding test class that exercises it end-to-end.

## Build

```bash
mvn compile -pl wamp -am
mvn test -pl wamp
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
