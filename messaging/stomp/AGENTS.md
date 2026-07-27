# STOMP Module -- Development Guide

## Module Purpose

The `stomp` module implements STOMP 1.2 (Simple Text Oriented Messaging Protocol) for text-based publish/subscribe messaging. It provides both broker and client implementations with a transport-agnostic invariant core and pluggable adapters for TCP and WebSocket.

## Key Interfaces

- `StompBroker` -- message broker: destination routing, subscription management, transactions, ack modes, receipts
- `StompClient` -- client: connect, send, subscribe, ack/nack, transactions, receipts, heart-beats
- `StompCodec` -- text-based frame parser/serializer with header escaping per STOMP 1.2 spec
- `StompFrame` -- immutable frame record (command, headers, body)
- `StompTransport` -- SPI interface for pluggable frame send/receive transports
- `StompSession` -- session lifecycle (CONNECTING/CONNECTED/DISCONNECTING/DISCONNECTED)
- `StompTransaction` -- transaction buffer for BEGIN/COMMIT/ABORT with buffered SEND/ACK/NACK
- `HeartbeatMonitor` -- heart-beat negotiation and monitoring

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `core` | Transport-agnostic STOMP protocol: commands, frames, codec, headers, session, broker, client, transactions, heart-beats |
| `core.transport` | SPI interface (`StompTransport`) for pluggable transport layers |
| `adapter.tcp` | TCP transport: `TcpStompTransport` (NULL-byte frame boundaries), `TcpStompServer`, `TcpStompClient` |
| `adapter.websocket` | WebSocket transport: `WebSocketStompTransport` (text frames), `WebSocketStompHandler` (HTTP upgrade, subprotocol `v12.stomp`) |
| `demo` | Demo applications and in-memory transport for testing |

## STOMP-Specific Coding Conventions

### Commands (16 total: 11 client + 4 server + 1 heartbeat)
- Client: STOMP, CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, ACK, NACK, BEGIN, COMMIT, ABORT, DISCONNECT
- Server: CONNECTED, MESSAGE, RECEIPT, ERROR
- Special: HEARTBEAT (empty EOL frame, not a real command per spec)

### Frame Wire Format
```
COMMAND\n
header1:value1\n
header2:value2\n
\n
body\0
```

### Header Escaping (STOMP 1.2)
- `\n` -- newline
- `\\` -- backslash
- `\c` -- colon
- `\r` -- carriage return
- First occurrence of a repeated header wins (per spec)

### Acknowledgment Modes
- **auto** -- messages acknowledged as soon as sent to subscriber (default)
- **client** -- cumulative ACK: acknowledging message N acknowledges all up to N
- **client-individual** -- per-message ACK: each message must be acknowledged individually

### Transaction Flow
- BEGIN(tx-id) -- start transaction, buffering begins
- SEND/ACK/NACK within transaction -- frames buffered, not applied
- COMMIT(tx-id) -- all buffered frames applied atomically
- ABORT(tx-id) -- all buffered frames discarded

### Heart-Beat Negotiation
- Header format: `cx,cy` (send capability ms, receive desire ms; 0 = disabled)
- Negotiated send = MAX(client-cx, server-cy) if both non-zero, else 0
- Negotiated receive = MAX(server-cx, client-cy) if both non-zero, else 0
- Receive timeout tolerance: 2x the negotiated interval

### Version Negotiation
- Client sends `accept-version:1.0,1.1,1.2`
- Server picks highest mutually supported version
- Prefers 1.2 > 1.1 > 1.0

## Transport SPI Pattern

The module follows the **invariant core + adapter** pattern. All protocol logic lives in `core/` and is transport-agnostic. Transport implementations (`StompTransport`) handle:
- Frame boundary detection (NULL byte for TCP, message boundaries for WebSocket)
- Encoding/decoding frames to/from their wire format
- Connection lifecycle (open/close)

Three transport implementations exist:
1. `TcpStompTransport` -- raw TCP sockets, byte-by-byte read with NULL-byte frame termination
2. `WebSocketStompTransport` -- WebSocket text frames, one STOMP frame per WebSocket message
3. `InMemoryStompTransport` -- blocking queue pairs for testing (no I/O)

## Testing Practices

- Codec tests: encode/decode round-trips for all commands, header escaping, binary body, edge cases
- Session tests: lifecycle state transitions, subscriptions, transactions, receipts
- Transaction tests: buffer, commit, abort, error states
- HeartbeatMonitor tests: parsing, negotiation, timer management
- Broker integration tests: pub/sub, ack modes, transactions, receipts, error handling
- Client tests: connect, subscribe, transactions, disconnect
- TCP adapter tests: full round-trip over real TCP sockets
- Demo tests: pub/sub, request-reply, transactional messaging
- All non-TCP tests use InMemoryStompTransport (no network I/O)
- Test count: 157

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
