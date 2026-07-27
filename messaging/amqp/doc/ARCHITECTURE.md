# AMQP Module -- Architecture

This document describes the architectural decisions for the AMQP module.

---

## Protocol Overview

AMQP 1.0 (ISO 19464 / OASIS) is an open standard for business messaging. Unlike earlier AMQP versions (0-9-1), AMQP 1.0 is a peer-to-peer protocol with credit-based flow control, session multiplexing, and a rich type system. The Lego Flow implementation provides a full protocol stack from the self-describing type system up through connection management.

## Layered Architecture

```mermaid
graph TD
    L1["Container / Client<br/>(connection management, SASL, API surface)"]
    L2["Session Multiplexing<br/>(incoming/outgoing windows, transfer-id tracking,<br/>link registry by handle)"]
    L3["Link Layer<br/>(credit-based flow control, sender/receiver,<br/>attach/detach lifecycle)"]
    L4["Delivery Management<br/>(delivery-id/tag, settlement, outcomes:<br/>accepted/rejected/released/modified/transactional)"]
    L5["Performative Codec<br/>(9 performatives as described lists,<br/>field encoding/decoding with null trimming)"]
    L6["Type System Codec<br/>(22 primitive types + list/map/array/described,<br/>self-describing binary format, compact encoding)"]
    L7["Frame Codec<br/>(8-byte header: SIZE/DOFF/TYPE/CHANNEL,<br/>performative body + optional payload)"]
    L8["Transport SPI<br/>(AmqpTransport interface:<br/>TcpTransport, InMemoryTransport)"]
    L9["service module (TCP)<br/>(SocketChannel, virtual threads)"]
    L10["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7 --> L8 --> L9 --> L10
```

## Performatives

All 9 AMQP 1.0 transport performatives modeled as a sealed interface:

| Performative | Descriptor | Direction | Purpose |
|-------------|-----------|-----------|---------|
| Open | 0x10 | Both | Connection negotiation (container-id, max-frame-size, channel-max, idle-timeout) |
| Begin | 0x11 | Both | Session begin (remote-channel, next-outgoing-id, incoming/outgoing window) |
| Attach | 0x12 | Both | Link attachment (name, handle, role, source, target, settle modes) |
| Flow | 0x13 | Both | Flow control (session windows + link credit) |
| Transfer | 0x14 | Both | Message transfer (handle, delivery-id, delivery-tag, settled, payload) |
| Disposition | 0x15 | Both | Delivery state update (first, last, settled, state) |
| Detach | 0x16 | Both | Link detachment (handle, closed, error) |
| End | 0x17 | Both | Session end (error) |
| Close | 0x18 | Both | Connection close (error) |

## Connection Lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Container

    Note over C,S: SASL Phase (optional)
    C->>S: SASL Header (AMQP 3 1 0 0)
    S->>C: SASL Header
    S->>C: sasl-mechanisms
    C->>S: sasl-init (mechanism + response)
    S->>C: sasl-outcome (code)

    Note over C,S: AMQP Phase
    C->>S: AMQP Header (AMQP 0 1 0 0)
    S->>C: AMQP Header
    C->>S: open (container-id, max-frame-size)
    S->>C: open (container-id, max-frame-size)

    Note over C,S: Session & Link Setup
    C->>S: begin (next-outgoing-id, windows)
    S->>C: begin (remote-channel, windows)
    C->>S: attach (name, handle, role, source, target)
    S->>C: attach (name, handle, role, source, target)
    S->>C: flow (link-credit)

    Note over C,S: Message Transfer
    C->>S: transfer (handle, delivery-id, tag, payload)
    S->>C: disposition (first, settled, accepted)

    Note over C,S: Teardown
    C->>S: detach (handle)
    S->>C: detach (handle)
    C->>S: end
    S->>C: end
    C->>S: close
    S->>C: close
```

## Credit-Based Flow Control

AMQP uses credit-based flow control at two levels:

### Session-Level Windows

```mermaid
graph LR
    Sender["Sender<br/>next-outgoing-id<br/>outgoing-window"] -->|"transfer"| Receiver["Receiver<br/>next-incoming-id<br/>incoming-window"]
    Receiver -->|"flow (window update)"| Sender
```

- **incoming-window**: max transfers we can accept (default 2048)
- **outgoing-window**: max transfers we can send (default 2048)
- Windows replenish when consumed below 25% of default

### Link-Level Credit

```mermaid
graph LR
    R["Receiver"] -->|"flow (link-credit=100)"| S["Sender"]
    S -->|"transfer (credit--)"| R
    R -->|"flow (replenish)"| S
```

- Receiver grants credit via flow performative
- Sender decrements credit on each transfer
- Receiver auto-replenishes when credit drops below 25 (25% of default 100)
- Sender blocks (returns null) when credit is 0

## Delivery Semantics

### At-Most-Once (Pre-Settled)

```mermaid
sequenceDiagram
    Sender->>Container: transfer (settled=true)
    Container->>Receiver: transfer (settled=true)
```
No acknowledgement. Fire-and-forget.

### At-Least-Once (Settled on Accept)

```mermaid
sequenceDiagram
    Sender->>Container: transfer (settled=false)
    Container->>Sender: disposition (accepted, settled=true)
    Container->>Receiver: transfer (settled=false)
    Receiver->>Container: disposition (accepted, settled=true)
```
Receiver explicitly accepts; message may be redelivered if not acknowledged.

### Delivery State Hierarchy

Six delivery states modeled as a sealed interface:
- **Received** -- partial reception (non-terminal)
- **Accepted** -- successfully processed (terminal)
- **Rejected** -- rejected with error condition (terminal)
- **Released** -- not processed, can be redelivered (terminal)
- **Modified** -- not processed, annotations modified (terminal)
- **TransactionalState** -- wraps an outcome within a transaction

## Container Architecture

```mermaid
graph TD
    TCP["TCP Listener<br/>(ServerSocketChannel)"] --> AH["Accept Handler<br/>(virtual thread per connection)"]
    AH --> SASL["SASL Negotiation<br/>(optional)"]
    SASL --> PH["Protocol Header<br/>Exchange"]
    PH --> OL["Open Lifecycle<br/>(container-id, max-frame-size)"]
    OL --> FL["Frame Loop"]
    FL --> PR["Performative Router<br/>(switch on type)"]
    PR --> BH["Begin Handler<br/>(session creation)"]
    PR --> ATH["Attach Handler<br/>(link creation + address routing)"]
    PR --> FH["Flow Handler<br/>(credit grants)"]
    PR --> TH["Transfer Handler<br/>(message decode + route)"]
    PR --> DH["Disposition Handler<br/>(delivery state updates)"]
    PR --> DTH["Detach Handler<br/>(link cleanup)"]
    PR --> EH["End Handler<br/>(session cleanup)"]
    PR --> CH["Close Handler<br/>(connection cleanup)"]

    TH --> MR["Message Router<br/>(address -> sender links)"]
```

- **Connection handling**: one virtual thread per connection via `Executors.newVirtualThreadPerTaskExecutor()`
- **Address-based routing**: `Map<String, List<SenderLink>>` routes messages from receivers to senders on the same address
- **Session management**: `Map<Integer, AmqpSession>` per connection, with remote-to-local channel mapping
- **Link management**: sender and receiver links registered in session by handle, and in container by address

## Client Architecture

- Connection lifecycle: SASL negotiation (optional) -> header exchange -> open -> ready -> close
- Background frame reader: virtual thread reads frames in a loop, dispatches to handleIncomingPerformative
- Session creation: send begin, wait for begin response
- Link attachment: send attach, wait for attach response, issue initial credit (receiver)
- Synchronous wait: spin-wait with deadline for session begin and link attach responses

## Type System Architecture

The AMQP type system is modeled as a sealed interface `AmqpType` with 22 record implementations:

```mermaid
graph TD
    AT["AmqpType (sealed)"]
    AT --> Primitives["Primitives<br/>Null, Bool, UByte, UShort, UInt, ULong,<br/>Byte, Short, Int, Long, Float, Double,<br/>Char, Timestamp, Uuid, Binary,<br/>AmqpString, Symbol"]
    AT --> Composites["Composites<br/>AmqpList, AmqpMap, AmqpArray"]
    AT --> Described["Described<br/>(descriptor + described-value)"]
```

- **Self-describing encoding**: each value preceded by a constructor byte (type code)
- **Compact encoding**: zero values (uint0, ulong0), small values (smalluint, smallint, smallulong, smalllong)
- **Described types**: constructor 0x00 + descriptor (usually ULong) + described value (usually AmqpList)
- **Null trimming**: trailing null fields removed from performative field lists

## SASL Authentication Architecture

```mermaid
graph TD
    SM["SaslMechanism (interface)"]
    SM --> AN["AnonymousMechanism"]
    SM --> PL["PlainMechanism<br/>(\\0username\\0password)"]
    SM --> EX["ExternalMechanism<br/>(TLS client cert)"]

    SA["SaslAuthenticator (server)"]
    SA --> CS["Credential Store<br/>(ConcurrentHashMap)"]
    SA --> CF["Custom AuthFunction<br/>(pluggable)"]
```

- Client-side: `SaslMechanism` interface with `name()`, `initialResponse()`, `respond(challenge)`
- Server-side: `SaslAuthenticator` with in-memory credential store, anonymous toggle, external support, custom auth function
- Outcome codes: 0=ok, 1=auth, 2=sys, 3=sys-perm, 4=sys-temp

## Transport Abstraction

```mermaid
graph TD
    AT["AmqpTransport (interface)<br/>send(ByteBuffer), receive(ByteBuffer),<br/>close(), isOpen()"]
    AT --> TCP["TcpTransport<br/>(SocketChannel wrapper)"]
    AT --> IM["InMemoryTransport<br/>(BlockingQueue pair)"]
```

- **TcpTransport**: thin wrapper over `SocketChannel`, blocking I/O
- **InMemoryTransport**: `createPair()` returns two connected transports using `LinkedBlockingQueue`
- Container's `handleConnection(AmqpTransport)` is public, allowing direct injection of in-memory transports for testing

## Integration with Lego Flow

| Lego Flow Module | Usage in AMQP |
|------------------|---------------|
| `blocks` | DP<I,O> for message processing pipeline, DF<T> for message filtering, Statistics for metrics |
| `service` | TCP channels for container/client connections, virtual thread pools, lifecycle management |

The AMQP module follows the framework's conventions: AutoCloseable resources, fluent builder APIs for configuration, virtual threads for concurrency.

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
