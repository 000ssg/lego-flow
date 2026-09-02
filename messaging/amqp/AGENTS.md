# messaging / amqp — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `amqp` module implements AMQP 1.0 (ISO 19464 / OASIS) messaging protocol. It provides both container (server) and client implementations, built on the `service` module for TCP transport and `blocks` for data processing primitives. The architecture is transport-agnostic: an invariant core handles all protocol logic through the `AmqpTransport` SPI, with TCP and in-memory adapters.

## Key Interfaces

- `AmqpContainer` -- container (server) with connection handling, session management, link routing, SASL auth
- `AmqpClient` -- client with connect, create session, attach sender/receiver links, send/receive messages
- `AmqpTransport` -- SPI for byte-level I/O (TCP, in-memory implementations)
- `AmqpSession` -- session multiplexing with flow control windows (incoming/outgoing)
- `SenderLink` -- credit-based sender with delivery tracking
- `ReceiverLink` -- credit-based receiver with blocking/timed receive, message handler callback
- `Performative` -- sealed interface for all 9 transport performatives (Open, Begin, Attach, Flow, Transfer, Disposition, Detach, End, Close)
- `AmqpType` -- sealed interface for the complete AMQP type system (22 types)
- `DeliveryState` -- sealed interface for delivery outcomes (Accepted, Rejected, Released, Modified, Received, TransactionalState)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `client` | Client implementation: connect, session creation, sender/receiver link attachment, SASL negotiation, background frame reader |
| `container` | Container (server) implementation: accept connections, protocol header exchange, SASL negotiation, session/link/transfer handling, address-based message routing |
| `common` | Shared constants (ports, frame sizes, protocol headers), error conditions (23 standard errors), connection state machine, exception type |
| `delivery` | Delivery tracking (delivery-id, tag, settlement future), delivery state sealed hierarchy (6 states), delivery state codec |
| `link` | Sender and receiver link implementations with credit-based flow control, delivery tracking, attach/detach lifecycle |
| `message` | AMQP message model (7 sections: header, delivery-annotations, message-annotations, properties, application-properties, body, footer), message codec |
| `sasl` | SASL authentication: SaslMechanism SPI, 3 mechanisms (ANONYMOUS, PLAIN, EXTERNAL), server-side authenticator with credential store and custom auth function |
| `session` | Session multiplexing: incoming/outgoing windows, transfer-id allocation, link management by handle, frame sender callback |
| `transport` | Frame codec, performative codec (9 performatives), AmqpTransport SPI, TCP and in-memory transport implementations, AmqpFrame record |
| `types` | Complete AMQP type system (22 types as sealed records), self-describing binary codec with compact encoding, descriptor constants for all performatives/sections/states |
| `demo` | Demo applications: simple send/receive, pub/sub, request/reply, transactions |

## AMQP-Specific Coding Conventions

### Performatives (all 9)
- OPEN (0x10), BEGIN (0x11), ATTACH (0x12), FLOW (0x13), TRANSFER (0x14)
- DISPOSITION (0x15), DETACH (0x16), END (0x17), CLOSE (0x18)

### Delivery Semantics
- **At-most-once**: sender pre-settles (settled=true in transfer), no acknowledgement
- **At-least-once**: sender sends unsettled, receiver sends disposition(accepted, settled=true)
- **Exactly-once**: sender sends unsettled, receiver sends disposition(accepted, settled=false), sender sends disposition(settled=true)

### Credit-Based Flow Control
- Receiver issues credit to sender via flow performative
- Sender can only transfer when linkCredit > 0
- Auto-replenish: receiver re-issues credit when remaining drops below 25% of default (100)
- Session-level: incoming/outgoing windows track transfer-ids (default 2048)

### Connection State Machine
```
START -> HDR_SENT/HDR_RCVD -> HDR_EXCH -> OPEN_PIPE/OPEN_SENT/OPEN_RCVD -> OPENED
  -> CLOSE_PIPE/CLOSE_SENT/CLOSE_RCVD -> END
```

### SASL Negotiation
- SASL header exchange occurs before AMQP header exchange
- Container sends mechanisms list, client sends init with chosen mechanism + response
- Container sends outcome (0=ok, 1=auth, 2=sys, 3=sys-perm, 4=sys-temp)

### Type System Encoding
- Self-describing: each value prefixed with constructor byte (type code)
- Compact forms: uint0 (0x43), ulong0 (0x44), smalluint (0x52), smallulong (0x53), smallint (0x54), smalllong (0x55)
- Described types: 0x00 + descriptor + described-value (used for all performatives and message sections)
- Null trailing fields trimmed from performative field lists

## Transport-Agnostic Architecture

The invariant AMQP core communicates through the `AmqpTransport` interface (`send(ByteBuffer)`, `receive(ByteBuffer)`, `close()`, `isOpen()`). Two implementations exist:

- **TcpTransport**: wraps `SocketChannel` for production TCP connections
- **InMemoryTransport**: creates linked pairs via `createPair()` for testing without TCP

The container's `handleConnection(AmqpTransport)` method is public, allowing direct connection injection for testing.

## Testing Practices

- Unit tests for type codec: encode -> decode round-trip for all 22 primitive types plus composite types
- Frame codec tests: frame encode/decode, heartbeat frames, extended headers
- Performative codec tests: all 9 performatives encode/decode round-trip
- Message codec tests: all 7 message sections, body types (data, amqp-value, amqp-sequence)
- Session tests: flow control windows, transfer-id allocation, link management
- Link tests: credit-based flow, delivery tracking, settle/accept/reject/release
- Transport tests: InMemoryTransport pair connectivity
- Descriptor tests: all descriptor constants match spec values
- All tests use InMemoryTransport or loopback TCP (no external broker required)
- Test count: 282

### AMQP-Specific Testing

**`AmqpEventListener`** — fires at `CONNECTION_STARTED`, `CONNECTION_OPENED`, `SESSION_CREATED`, `LINK_ATTACHED`. Use `AmqpEventListener.latchOnFirst(EventType)` to synchronize tests with server-side protocol progress without `Thread.sleep`. (This is the generic protocol flow listener pattern — see [doc/AGENTS_protocol_accuracy.md](../doc/AGENTS_protocol_accuracy.md) §11.)

**In-memory transport** — `InMemoryTransport.createPair()` creates two linked transports. Use `AmqpContainer.handleConnection(transport)` to inject the server side. For in-process tests requiring two threads, run the server on a **platform thread** (`Thread.ofPlatform().start()`) to avoid carrier thread starvation on CI. (See [doc/AGENTS_test_patterns.md](../doc/AGENTS_test_patterns.md) §8-9.)

**Buffer clearing** — AMQP frame handlers reuse `ByteBuffer` instances. Always call `clear()` after reading before reusing the buffer for the next `readFully()` call. (See [doc/AGENTS_protocol_accuracy.md](../doc/AGENTS_protocol_accuracy.md) §10.)

> **General test patterns**: See [../doc/AGENTS_test_patterns.md](../doc/AGENTS_test_patterns.md)
> **Protocol accuracy rules**: See [../doc/AGENTS_protocol_accuracy.md](../doc/AGENTS_protocol_accuracy.md)
