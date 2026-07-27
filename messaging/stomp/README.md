# Lego Flow STOMP -- Simple Text Oriented Messaging Protocol

A complete STOMP 1.2 implementation with transport-agnostic invariant core, TCP adapter, and WebSocket adapter. Provides both broker and client implementations.

## Architecture

The module follows the **invariant core + adapter** pattern (same as the WAMP module):

```
ssg.legoflow.messaging.stomp/
├── core/                           -- Transport-agnostic STOMP protocol
│   ├── StompCommand.java           -- Enum of all 16 commands (client + server + heartbeat)
│   ├── StompFrame.java             -- Immutable frame record (command, headers, body)
│   ├── StompCodec.java             -- Frame parser/serializer with header escaping
│   ├── StompHeaders.java           -- Case-sensitive header map with standard constants
│   ├── StompSession.java           -- Session lifecycle (CONNECTING/CONNECTED/DISCONNECTING/DISCONNECTED)
│   ├── StompBroker.java            -- Message broker: routing, subscriptions, transactions, receipts
│   ├── StompClient.java            -- Client: connect, send, subscribe, ack/nack, transactions
│   ├── StompTransaction.java       -- Transaction buffer (SEND/ACK/NACK held until COMMIT/ABORT)
│   ├── HeartbeatMonitor.java       -- Heart-beat negotiation and monitoring
│   ├── StompProtocolException.java -- Protocol violation exceptions
│   └── transport/
│       └── StompTransport.java     -- SPI interface for frame send/receive
├── adapter/
│   ├── tcp/
│   │   ├── TcpStompTransport.java  -- StompTransport over raw TCP sockets
│   │   ├── TcpStompServer.java     -- TCP server accepting STOMP connections
│   │   └── TcpStompClient.java     -- TCP client connecting to STOMP broker
│   └── websocket/
│       ├── WebSocketStompTransport.java -- StompTransport over WebSocket text frames
│       └── WebSocketStompHandler.java   -- HTTP upgrade handler for STOMP-over-WebSocket
└── demo/
    ├── InMemoryStompTransport.java  -- In-memory transport for testing
    ├── SimplePubSubDemo.java        -- Basic publish/subscribe demo
    ├── RequestReplyDemo.java        -- Request-reply pattern demo
    └── TransactionalDemo.java       -- Transactional messaging demo
```

## Quick Start

### In-Memory Pub/Sub (No Network)

```java
var broker = new StompBroker();

// Create in-memory transport pairs
var pubPair = InMemoryStompTransport.createPair();
var subPair = InMemoryStompTransport.createPair();

// Connect transports to broker
broker.accept(pubPair[1]);
broker.accept(subPair[1]);

// Create and connect clients
var publisher = new StompClient(pubPair[0]);
var subscriber = new StompClient(subPair[0]);
publisher.connect("localhost");
subscriber.connect("localhost");

// Subscribe
subscriber.subscribe("/topic/news", msg -> {
    System.out.println("Received: " + msg.bodyAsText());
});

// Publish
publisher.send("/topic/news", "Breaking news!", "text/plain");
```

### TCP Client-Server

```java
// Server
var broker = new StompBroker();
var server = new TcpStompServer(broker, 61613);
server.start();

// Client
var client = new TcpStompClient("localhost", 61613);
client.connect("localhost");
client.getClient().subscribe("/queue/work", msg -> {
    System.out.println("Task: " + msg.bodyAsText());
});
```

### Transactional Messaging

```java
client.begin("tx-1");
client.send("/queue/orders", "order-1", "text/plain", "tx-1");
client.send("/queue/orders", "order-2", "text/plain", "tx-1");
client.commit("tx-1");  // Both messages delivered atomically
// Or: client.abort("tx-1");  // Both messages discarded
```

### Acknowledgment Modes

```java
// Auto (default) -- no ACK needed
client.subscribe("/topic/auto", "auto", handler);

// Client -- cumulative ACK
client.subscribe("/topic/cumulative", "client", msg -> {
    // ACK this message and all previous
    client.ack(msg.header(StompHeaders.ACK));
});

// Client-individual -- per-message ACK
client.subscribe("/topic/individual", "client-individual", msg -> {
    // ACK only this specific message
    client.ack(msg.header(StompHeaders.ACK));
});
```

## Features

- **STOMP 1.2 compliant** -- full protocol implementation per [stomp.github.io](https://stomp.github.io/)
- **Transport-agnostic core** -- protocol logic independent of transport layer
- **TCP adapter** -- raw TCP with frame boundary detection via NULL byte
- **WebSocket adapter** -- STOMP-over-WebSocket (subprotocol `v12.stomp`)
- **Broker** -- destination routing, subscription management, ack modes, transactions, receipts
- **Client** -- connect, send, subscribe, ack/nack, transactions, receipts, heart-beats
- **Heart-beats** -- bidirectional negotiation and monitoring
- **Transactions** -- BEGIN/COMMIT/ABORT with buffered SEND/ACK/NACK
- **Three ack modes** -- auto, client (cumulative), client-individual
- **Header escaping** -- \n, \\, \c (colon), \r per STOMP 1.2 spec
- **Binary body support** -- content-length header enables NULL bytes in body
- **Version negotiation** -- supports 1.0, 1.1, 1.2; prefers highest
- **Virtual threads** -- connections handled on virtual threads (JDK 25)

## Dependencies

- `lego-flow-blocks` -- core DP/DF framework
- `lego-flow-service` -- service lifecycle
- `lego-flow-http` -- WebSocket adapter (optional runtime dependency)
- SLF4J -- logging

## Test Coverage

**157 tests** covering:
- Frame codec (43 tests): parse/serialize all commands, header escaping, binary body, round-trips
- StompHeaders (14 tests): put/get, case sensitivity, standard constants
- StompFrame (10 tests): construction, text body, heartbeat
- StompCommand (5 tests): client vs server, parsing
- StompSession (10 tests): lifecycle, subscriptions, transactions, receipts
- StompTransaction (9 tests): buffer, commit, abort, error handling
- HeartbeatMonitor (17 tests): parsing, negotiation, timer management
- StompBroker (20 tests): pub/sub, ack modes, transactions, receipts, error handling
- StompClient (12 tests): connect, subscribe, transactions, disconnect
- TCP adapter (8 tests): full round-trip over real TCP sockets
- Demo tests (9 tests): pub/sub, request-reply, transactional messaging

## Documentation

- [COMPLIANCE.md](COMPLIANCE.md) -- STOMP 1.2 protocol compliance matrix
- [doc/REQUIREMENTS.md](doc/REQUIREMENTS.md) -- requirements and design decisions
- [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) -- architectural decisions
