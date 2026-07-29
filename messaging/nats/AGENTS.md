# messaging / nats — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `nats` module implements the NATS protocol (core pub/sub + JetStream persistent streaming) for cloud-native messaging. It provides both server and client implementations, with subject-based routing, wildcard matching, queue groups, request/reply, headers, and JetStream streams/consumers.

## Key Interfaces

- `NatsServer` — server with TCP accept loop, virtual threads, client registry, authentication, message routing, JetStream
- `NatsClient` — client with connect, pub/sub, request/reply, headers, auto-inbox management
- `NatsCodec` — text protocol parser/serializer for all 12 operations (INFO, CONNECT, PUB, HPUB, SUB, UNSUB, MSG, HMSG, PING, PONG, +OK, -ERR)
- `SubjectMatcher` — subject matching with `*` (single-token) and `>` (multi-level) wildcards
- `SubscriptionRegistry` — thread-safe subscription store with exact and wildcard matching
- `MessageRouter` — routes published messages to matching subscriptions with queue group round-robin and echo suppression
- `JetStreamManager` — stream/consumer CRUD, message persistence, pull subscriptions
- `StreamStore` — in-memory message store with sequence numbers, retention policies, and purge

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Protocol constants, text codec (encode/decode all 12 ops), ConnectOptions, ServerInfo, NatsHeaders, NatsStatus |
| `client` | Client implementation: connect, publish, subscribe, request/reply, inbox management, auto-unsubscribe |
| `server` | Server implementation: TCP accept, client connections, message routing, queue groups, authentication |
| `server.auth` | Authentication: Authenticator interface, TokenAuthenticator, UserPassAuthenticator |
| `subject` | Subject engine: Subject model, SubjectMatcher (wildcards `*` and `>`), SubscriptionRegistry |
| `jetstream` | JetStream: streams, consumers, pull subscriptions, ack policies, retention, StreamStore |
| `demo` | Demo applications: pub/sub, request/reply, queue groups, JetStream |

## NATS-Specific Coding Conventions

### Protocol Operations (12 total)
- Client-to-server: CONNECT, PUB, HPUB, SUB, UNSUB
- Server-to-client: INFO, MSG, HMSG, +OK, -ERR
- Bidirectional: PING, PONG

### Subject Matching Rules
- `.` is the level separator
- `*` matches exactly one token: `foo.*.baz` matches `foo.bar.baz`
- `>` matches one or more trailing tokens (must be last): `foo.>` matches `foo.bar` and `foo.bar.baz`
- No special handling for `$` prefixed subjects (unlike MQTT `$SYS`)

### Queue Groups
- Multiple subscribers share the same queue group name on a subject
- Server delivers each message to exactly one member (round-robin)
- Non-queued subscribers still receive all messages independently

### Request/Reply Pattern
- Client generates unique inbox: `_INBOX.<uuid>.<counter>`
- Publishes with reply-to set to inbox
- Subscribes to inbox with auto-unsubscribe after 1 message
- CompletableFuture-based with configurable timeout

### JetStream Concepts
- **Stream**: captures messages from subjects, configurable retention (limits/interest/workqueue)
- **Consumer**: tracks delivery position, ack policy (none/all/explicit), pull-based fetch
- **StreamStore**: in-memory, enforces maxMsgs/maxBytes/maxAge limits
- **PullSubscription**: fetch batches, ack by sequence number
- **Deliver policies**: ALL, LAST, NEW, BY_START_SEQ, BY_START_TIME

### Sealed Interface for ParsedOp
NatsCodec uses a `sealed interface ParsedOp` with 12 record variants (Info, Connect, Pub, Hpub, Sub, Unsub, Msg, Hmsg, Ping, Pong, Ok, Err). Pattern matching switch is used throughout for dispatch.

### Headers Format (NATS/1.0)
```
NATS/1.0 [status_code] [description]\r\n
Key: Value\r\n
\r\n
```
- NatsHeaders supports multi-value keys, case-insensitive lookup
- NatsStatus enum: 100 (Idle Heartbeat), 404 (No Messages), 408 (Request Timeout), 409 (Conflict), 503 (No Responders)

## Testing Practices

- Unit tests for protocol codec: encode -> decode round-trip for all 12 operations
- Subject matching tests: wildcard correctness with edge cases (18 tests)
- SubscriptionRegistry tests: exact + wildcard registration and matching
- Server integration tests: multi-client pub/sub, queue groups, authentication
- JetStream tests: stream CRUD, consumer lifecycle, pull subscription fetch/ack, retention enforcement
- StreamStore tests: store/fetch/purge, retention policies (maxMsgs, maxBytes, maxAge), discard policies
- Authentication tests: token auth, user/pass auth, rejection on invalid credentials
- Queue group tests: round-robin distribution, member add/remove
- Demo functional tests: PubSub, RequestReply, QueueGroup, JetStream
- All tests use loopback transport (no external NATS server required)
- Test count: 271
