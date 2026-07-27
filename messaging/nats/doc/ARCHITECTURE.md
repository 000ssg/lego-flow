# NATS Module — Architecture

This document describes the architectural decisions for the NATS module.

---

## Protocol Overview

NATS is a cloud-native, high-performance messaging system using a simple text-based protocol over TCP. The Lego Flow implementation supports core NATS (pub/sub, request/reply, queue groups) and JetStream (persistent streaming with streams, consumers, and pull subscriptions).

## Layered Architecture

```mermaid
graph TD
    L1["Server / Client<br/>(connection management, API surface, configuration)"]
    L2["JetStream<br/>(streams, consumers, pull subscriptions,<br/>ack policies, retention enforcement)"]
    L3["Message Router<br/>(subscription matching, queue group round-robin,<br/>echo suppression)"]
    L4["Subject Engine<br/>(Subject model, SubjectMatcher wildcards * and >,<br/>SubscriptionRegistry)"]
    L5["Protocol Codec<br/>(12 operations, text-based line protocol,<br/>CRLF framing, JSON payloads for INFO/CONNECT)"]
    L6["TCP Transport<br/>(Socket, BufferedReader/Writer, virtual threads)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6
```

## Protocol Operations

All 12 NATS protocol operations:

| Operation | Direction | Format | Purpose |
|-----------|-----------|--------|---------|
| INFO | S->C | `INFO {json}\r\n` | Server sends capabilities on connect |
| CONNECT | C->S | `CONNECT {json}\r\n` | Client sends auth and options |
| PUB | C->S | `PUB subject [reply-to] size\r\npayload\r\n` | Publish a message |
| HPUB | C->S | `HPUB subject [reply-to] hdr_size total_size\r\nheaders+payload\r\n` | Publish with headers |
| SUB | C->S | `SUB subject [queue-group] sid\r\n` | Subscribe to a subject |
| UNSUB | C->S | `UNSUB sid [max_msgs]\r\n` | Unsubscribe |
| MSG | S->C | `MSG subject sid [reply-to] size\r\npayload\r\n` | Deliver a message |
| HMSG | S->C | `HMSG subject sid [reply-to] hdr_size total_size\r\nheaders+payload\r\n` | Deliver with headers |
| PING | Both | `PING\r\n` | Keep-alive request |
| PONG | Both | `PONG\r\n` | Keep-alive response |
| +OK | S->C | `+OK\r\n` | Verbose mode acknowledgement |
| -ERR | S->C | `-ERR 'message'\r\n` | Error notification |

## Sealed Protocol Model

The codec uses a sealed interface hierarchy for type-safe protocol dispatch:

```mermaid
graph TD
    PO["sealed interface ParsedOp"]
    PO --> Info["record Info(ServerInfo)"]
    PO --> Connect["record Connect(ConnectOptions)"]
    PO --> Pub["record Pub(subject, replyTo, payload)"]
    PO --> Hpub["record Hpub(subject, replyTo, headers, payload)"]
    PO --> Sub["record Sub(subject, queueGroup, sid)"]
    PO --> Unsub["record Unsub(sid, maxMsgs)"]
    PO --> Msg["record Msg(subject, sid, replyTo, payload)"]
    PO --> Hmsg["record Hmsg(subject, sid, replyTo, headers, payload)"]
    PO --> Ping["record Ping()"]
    PO --> Pong["record Pong()"]
    PO --> Ok["record Ok()"]
    PO --> Err["record Err(message)"]
```

Pattern matching switch statements dispatch on `ParsedOp` variants in both client (`handleOp`) and server (`processOp`) reader loops.

## Subject Matching

```mermaid
graph LR
    Pub["Published Subject<br/>orders.us.new"] --> SM["SubjectMatcher"]
    SM --> Exact["Exact: orders.us.new"]
    SM --> Single["Single wildcard: orders.*.new"]
    SM --> Multi["Multi wildcard: orders.>"]
```

- `.` is the level separator (tokens between dots)
- `*` matches exactly one token at its position
- `>` matches one or more trailing tokens (must be last token in pattern)
- SubscriptionRegistry separates exact and wildcard subscriptions into two ConcurrentHashMaps for efficient lookup

## Message Routing Architecture

```mermaid
graph TD
    PUB["Client PUB/HPUB"] --> Router["MessageRouter"]
    Router --> Registry["SubscriptionRegistry.match()"]
    Registry --> Direct["Non-queued subscribers<br/>(deliver to all)"]
    Registry --> QG["Queue group subscribers<br/>(deliver to one per group, round-robin)"]
    Router --> Echo["Echo suppression<br/>(skip publisher if echo=false)"]
    Router --> JS["JetStream intercept<br/>(store in matching streams)"]
```

- **MessageRouter** handles all message delivery on the server side
- Non-queued subscribers each receive a copy of every matching message
- Queue group members share load via round-robin (QueueGroup with AtomicInteger index)
- Echo suppression: if client sets `echo=false` in CONNECT, their own publishes are not delivered back

## Server Architecture

```mermaid
graph TD
    TCP["ServerSocket<br/>(TCP Listener)"] --> Accept["Accept Loop<br/>(virtual thread)"]
    Accept --> CC["ClientConnection<br/>(per-client virtual thread)"]
    CC --> Handshake["INFO/CONNECT<br/>Handshake"]
    Handshake --> Auth["Authenticator<br/>(token or user/pass)"]
    CC --> OpLoop["Operation Loop<br/>(PUB/SUB/UNSUB/PING)"]
    OpLoop --> Router["MessageRouter"]
    Router --> SubReg["SubscriptionRegistry"]
    Router --> QueueGroups["QueueGroup<br/>(round-robin)"]
    OpLoop --> JSMgr["JetStreamManager"]
    JSMgr --> Streams["Stream instances"]
```

- **NatsServer**: manages ServerSocket, client registry (ConcurrentHashMap), JetStreamManager
- **ClientConnection**: per-client handler on a virtual thread; manages INFO/CONNECT handshake, authentication, protocol operation dispatch, cleanup on disconnect
- **Authenticator**: pluggable interface with TokenAuthenticator and UserPassAuthenticator implementations
- Each client connection maintains its own subscription map; cleanup removes all subscriptions on disconnect

## Client Architecture

```mermaid
graph TD
    App["Application Code"] --> Client["NatsClient"]
    Client --> Connect["connect()<br/>Socket + INFO/CONNECT + PING/PONG"]
    Client --> Pub["publish()<br/>PUB/HPUB"]
    Client --> Sub["subscribe()<br/>SUB + handler callback"]
    Client --> Req["request()<br/>inbox + SUB + PUB + CompletableFuture"]
    Client --> Reader["Reader Loop<br/>(virtual thread)"]
    Reader --> Dispatch["handleOp()<br/>MSG/HMSG -> deliver to handler<br/>PING -> PONG<br/>ERR -> log"]
    Client --> Inbox["InboxManager<br/>_INBOX.uuid.counter"]
```

- Connection lifecycle: socket connect -> read INFO -> send CONNECT -> send PING -> read PONG -> ready
- Reader loop runs on a virtual thread, dispatching incoming operations via pattern matching switch
- Subscriptions stored in ConcurrentHashMap keyed by string SID
- Auto-unsubscribe: subscription deactivates after receiving maxMessages
- Request/reply: creates temp inbox subscription (auto-unsub after 1), publishes with reply-to, waits on CompletableFuture with timeout
- Thread-safe writes via synchronized block on BufferedWriter

## JetStream Architecture

```mermaid
graph TD
    JSMgr["JetStreamManager"] --> Streams["Stream instances<br/>(ConcurrentHashMap)"]
    Streams --> Config["StreamConfig<br/>(subjects, retention, limits)"]
    Streams --> Store["StreamStore<br/>(in-memory, sequence numbers)"]
    Streams --> Consumers["Consumer instances<br/>(ConcurrentHashMap)"]
    Store --> Retention["Retention enforcement<br/>(maxMsgs, maxBytes, maxAge)"]
    Consumers --> ConsConfig["ConsumerConfig<br/>(deliver/ack/replay policy)"]
    Consumers --> Tracking["Delivery tracking<br/>(sequence, pending acks)"]
    JSMgr --> PullSub["PullSubscription<br/>(fetch + ack)"]
    PullSub --> Store
    PullSub --> Consumers
```

### Stream
- Captures messages from configured subjects (wildcard-matched via SubjectMatcher)
- StreamStore: in-memory CopyOnWriteArrayList with AtomicLong sequence counter
- Retention policies: LIMITS (maxMsgs/maxBytes/maxAge), INTEREST (active consumers), WORKQUEUE (remove after ack)
- Discard policies: OLD (evict oldest), NEW (reject incoming)

### Consumer
- Tracks delivery position (deliveredSequence) and acknowledgement state (ackedSequence, pendingAcks)
- Ack policies: NONE (fire-and-forget), ALL (cumulative ack), EXPLICIT (per-message ack)
- Deliver policies: ALL, LAST, NEW, BY_START_SEQ, BY_START_TIME
- canDeliver() checks maxAckPending limit before allowing more fetches

### PullSubscription
- Fetches batches from StreamStore starting at consumer's nextFetchSequence
- Adds JetStream metadata headers (Nats-Stream, Nats-Sequence, Nats-Timestamp)
- ack() acknowledges by sequence; for WORKQUEUE retention, also removes the message from the store

## JetStream Client (via NATS protocol)

JetStreamClient wraps NatsClient to provide JetStream operations over the standard NATS request/reply pattern using `$JS.API.*` subjects:

| Operation | Subject |
|-----------|---------|
| Create stream | `$JS.API.STREAM.CREATE.<name>` |
| Delete stream | `$JS.API.STREAM.DELETE.<name>` |
| Stream info | `$JS.API.STREAM.INFO.<name>` |
| Create consumer | `$JS.API.CONSUMER.CREATE.<stream>` |
| Delete consumer | `$JS.API.CONSUMER.DELETE.<stream>.<consumer>` |

## Authentication

```mermaid
graph TD
    CC["ClientConnection"] --> Auth["Authenticator interface"]
    Auth --> Token["TokenAuthenticator<br/>(single token comparison)"]
    Auth --> UserPass["UserPassAuthenticator<br/>(ConcurrentHashMap credentials)"]
    Auth --> Custom["Custom implementation"]
```

- Server sets an Authenticator before start; if set, ServerInfo advertises `auth_required=true`
- ClientConnection calls `authenticator.authenticate(connectOptions)` during CONNECT handling
- Failed authentication sends `-ERR 'Authorization Violation'` and closes the connection

## Integration with Lego Flow

| Lego Flow Module | Usage in NATS |
|------------------|---------------|
| `blocks` | DP<I,O> for message processing pipeline, DF<T> for filtering |
| `service` | TCP channels for server/client connections, virtual thread pools |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
