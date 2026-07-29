# messaging / wamp — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The **wamp** module implements the WAMP (Web Application Messaging Protocol) with a two-layer architecture:
- **Invariant core** — transport-agnostic pure WAMP logic (messages, sessions, roles, router, realms)
- **WebSocket adapter** — bridges the core to HTTP WebSocket transport

This separation allows the WAMP protocol logic to remain independent of any specific transport, enabling future adapters (raw TCP, long-polling) without modifying core code.

## Key Interfaces

### WampMessage (sealed)
Sealed interface for all WAMP message types (HELLO, WELCOME, GOODBYE, PUBLISH, SUBSCRIBE, CALL, REGISTER, etc.). Pattern matching via `switch` expressions.

### WampSession
Represents a client session within a realm. Manages session lifecycle and message routing.

### WampRouter (Broker + Dealer)
Central routing component combining:
- **Broker** — routes pub/sub messages between publishers and subscribers
- **Dealer** — routes RPC calls between callers and callees

### WampTransport (SPI)
Transport-agnostic interface for send/receive operations. Allows plugging in different transport mechanisms.

### Roles
- **Caller** — initiates RPC calls
- **Callee** — registers and handles RPC procedures
- **Publisher** — publishes events to topics
- **Subscriber** — subscribes to topics and receives events

### Realm & RealmManager
Realms provide isolated routing domains. RealmManager handles realm creation and session assignment.

## Package Structure

### Core — `ssg.legoflow.wamp.core`
Pure WAMP logic with no transport dependency:
- Messages, sessions, roles, router, realms
- WampTransport SPI definition
- InMemoryTransport for testing

### Adapter — `ssg.legoflow.wamp.adapter.websocket`
Bridges WAMP core to HTTP WebSocket:
- `WebSocketWampTransport` — fully implements `WampTransport` over WebSocket frames; bidirectional wiring, `onFrame` consumer hook, `injectFrame` (test injection), `tryReceive` (non-blocking poll)
- `WampWebSocketHandler` — HTTP Upgrade handler; `createUpgradeResponse` advertises `wamp.2.json` subprotocol
- `WebSocketWampService` — manages WAMP sessions over WebSocket; drives HELLO/WELCOME/GOODBYE lifecycle, routes through realm Broker/Dealer, uses virtual threads per session
- `WampWebSocketFilter` — `AbstractDataFilter<ByteBuffer>` with ENCODE and DECODE modes for WAMP JSON in WebSocket frames

## WampTransport SPI

The `WampTransport` interface defines the contract for send/receive operations. To add a new transport:
1. Implement `WampTransport`
2. Handle message serialization for the transport
3. Wire into WampRouter

Current implementations:
- `WebSocketWampTransport` — production WebSocket adapter (bidirectional wiring, onFrame, injectFrame, tryReceive)
- `InMemoryTransport` — in-process transport for testing (no HTTP needed)

## Dependencies
- blocks (core DP/DF framework)
- service (service lifecycle, dual API)
- http (HTTP protocol)
- web-services (web service components)

## Dual API Convention

- **Sync procedural** — direct method calls for session operations, RPC, pub/sub
- **Async wrapper** — `CompletableFuture<T>` return types via virtual threads
- **Functional style** — lambda-friendly registration and subscription

## Testing

- **Framework**: JUnit 5, AssertJ
- **Core tests**: use `InMemoryTransport` — no HTTP/WebSocket server needed
- **Adapter tests**: use `injectFrame` / `tryReceive` on `WebSocketWampTransport` for deterministic unit tests without a live server
- **Demo tests**: `WsRpcDemoTest`, `WsPubSubDemoTest`, `FullWampServerDemoTest`, `RpcErrorHandlingDemoTest` exercise end-to-end WebSocket flows
- **Total tests**: 295 passing
  - `core/CallerCalleeTest` — 6 tests
  - `core/PublisherSubscriberTest` — 7 tests
  - `core/WampRouterTest` — 10 tests
  - `core/NewMessageTypesTest` — 14 tests
  - `core/AdvancedBrokerTest` — 14 tests
  - `core/AdvancedDealerTest` — 13 tests
  - `core/SessionMetaTest` — 7 tests
  - `core/serialization/MessagePackEncoderTest` — 23 tests
  - `core/serialization/MessagePackDecoderTest` — 20 tests
  - `core/serialization/MessagePackRoundTripTest` — 11 tests
  - `core/serialization/CborEncoderTest` — 16 tests
  - `core/serialization/CborRoundTripTest` — 12 tests
  - `core/serialization/WampMessagePackSerializerTest` — 21 tests
  - `core/serialization/WampCborSerializerTest` — 9 tests
  - `core/serialization/WampSerializerFactoryTest` — 4 tests
  - `core/auth/WampCraAuthTest` — 7 tests
  - `core/auth/TicketAuthTest` — 5 tests
  - `core/auth/CryptosignAuthTest` — 5 tests
  - `core/auth/WampAuthorizerTest` — 3 tests
  - `adapter/websocket/WebSocketWampTransportTest` — 9 tests
  - `adapter/websocket/WampWebSocketHandlerTest` — 9 tests
  - `demo/websocket/WsRpcDemoTest` — 2 tests
  - `demo/websocket/WsPubSubDemoTest` — 2 tests
  - `demo/websocket/FullWampServerDemoTest` — 6 tests
  - `demo/base/RpcErrorHandlingDemoTest` — 4 tests
  - (previous tests) — 55 tests
