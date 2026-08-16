# NATS Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 271
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: NATS Protocol (text-based), JetStream

---

## Requirements

### Protocol Codec
1. Encode and decode all 12 NATS protocol operations in text format
2. Parse line-oriented protocol with CRLF terminators
3. Handle operations with payloads: PUB, HPUB, MSG, HMSG (size-prefixed payload followed by CRLF)
4. Handle operations with JSON payloads: INFO (ServerInfo), CONNECT (ConnectOptions)
5. Handle simple operations: PING, PONG, +OK, -ERR
6. Support headers format (NATS/1.0) with status line, key-value pairs, and multi-value keys
7. Use sealed interface ParsedOp with record variants for type-safe protocol dispatch

### Subject Engine
1. Model subjects as dot-separated token sequences
2. Validate subjects: non-empty, no spaces
3. Match subscriptions against publish subjects with wildcard support
4. Single-token wildcard (`*`): matches exactly one token at its position
5. Multi-level wildcard (`>`): matches one or more trailing tokens, must be last token
6. Maintain thread-safe SubscriptionRegistry with separate exact and wildcard subscription maps
7. Support efficient matching: exact lookup in ConcurrentHashMap, wildcard scan over patterns

### Message Routing
1. Route published messages to all matching subscriptions
2. Support queue groups: deliver each message to exactly one group member (round-robin)
3. Echo suppression: skip publisher if client set echo=false in CONNECT
4. Handle queue group echo suppression (try next member if chosen member is publisher)
5. Thread-safe concurrent routing with ConcurrentHashMap and CopyOnWriteArrayList

### Server
1. Accept TCP connections with ServerSocket and virtual thread per client
2. Send INFO with server capabilities on client connect
3. Process CONNECT with authentication validation
4. Handle PUB/HPUB: route messages to matching subscribers, intercept for JetStream
5. Handle SUB: register subscription in client and router
6. Handle UNSUB: remove subscription (immediate or auto-unsub after N messages)
7. Handle PING/PONG: respond to client keep-alive
8. Verbose mode: send +OK after each successful operation when enabled
9. Track connected clients with ConcurrentHashMap, clean up subscriptions on disconnect
10. Support configurable port (0 for ephemeral)

### Client
1. Establish TCP connection with configurable host and port
2. Perform INFO/CONNECT handshake followed by PING/PONG confirmation
3. Publish messages with optional reply-to subject
4. Publish messages with headers (HPUB)
5. Subscribe to subject patterns with optional queue group
6. Auto-unsubscribe: subscription deactivates after receiving maxMessages
7. Request/reply: generate unique inbox, subscribe with auto-unsub, publish with reply-to, wait on CompletableFuture with timeout
8. Reader loop on virtual thread dispatching incoming operations via pattern matching
9. Thread-safe writes via synchronized BufferedWriter
10. Graceful close: set connected flag, cancel reader, close socket, shutdown executor

### Authentication
1. Pluggable Authenticator interface with authenticate(ConnectOptions) method
2. TokenAuthenticator: validates auth_token field against configured token
3. UserPassAuthenticator: validates user/pass against ConcurrentHashMap of credentials
4. Server advertises auth_required=true in INFO when authenticator is set
5. Failed auth sends -ERR 'Authorization Violation' and closes connection

### Request/Reply Pattern
1. InboxManager generates unique inbox subjects: `_INBOX.<uuid>.<counter>`
2. Each client has its own InboxManager instance (UUID-based prefix)
3. Request: subscribe to inbox (auto-unsub after 1), publish with reply-to, await CompletableFuture
4. Timeout: returns null, unsubscribes the inbox subscription

### Headers (NATS/1.0)
1. Serialize/parse header blocks: version line, key-value pairs, trailing CRLF
2. Support status line: `NATS/1.0 <code> <description>`
3. NatsStatus enum: 100 (Idle Heartbeat), 404 (No Messages), 408 (Request Timeout), 409 (Conflict), 503 (No Responders)
4. Multi-value headers: multiple values per key via add()
5. Case-insensitive key lookup preserving original case
6. HPUB/HMSG encode header size and total size separately

### JetStream — Streams
1. Stream CRUD: create, update, delete, get, list
2. StreamConfig: name, subjects (wildcard-capable), retention policy, max consumers, max messages, max bytes, max age, storage type, num replicas, discard policy, duplicate window
3. Retention policies: LIMITS (enforce maxMsgs/maxBytes/maxAge), INTEREST (consumer-based), WORKQUEUE (remove after ack)
4. Discard policies: OLD (evict oldest when limits reached), NEW (reject incoming)
5. StreamStore: in-memory with sequence numbers, timestamps, byte tracking
6. Stream matches published subjects using SubjectMatcher
7. Purge: remove all messages, reset byte counter

### JetStream — Consumers
1. Consumer CRUD: create, delete, get on a stream
2. ConsumerConfig: durable name, deliver policy, ack policy, ack wait, max deliver, replay policy, filter subject, max ack pending, start sequence
3. Deliver policies: ALL, LAST, NEW, BY_START_SEQ, BY_START_TIME
4. Ack policies: NONE (no ack required), ALL (cumulative ack), EXPLICIT (per-message ack)
5. Track delivered sequence, acked sequence, pending acks (ConcurrentSkipListSet)
6. canDeliver(): enforce maxAckPending limit
7. Durable consumers: persistent name; ephemeral consumers: auto-generated name

### JetStream — Pull Subscriptions
1. Fetch batches from StreamStore starting at consumer's next sequence
2. Support optional subject filter on consumer config
3. Add JetStream metadata headers: Nats-Stream, Nats-Sequence, Nats-Timestamp
4. Merge original message headers with metadata headers
5. Ack by sequence number: update consumer state
6. Workqueue retention: remove message from store on ack

### JetStream Client (Protocol-Based)
1. JetStreamClient wraps NatsClient for JetStream operations
2. Create/delete/info stream via `$JS.API.STREAM.*` subjects
3. Create/delete consumer via `$JS.API.CONSUMER.*` subjects
4. Publish with ack via request/reply pattern

### Demo Applications
1. PubSubDemo: server + publisher + subscriber with subject patterns
2. RequestReplyDemo: service pattern with request/reply and timeout
3. QueueGroupDemo: queue group load balancing across multiple subscribers
4. JetStreamDemo: stream creation, publish, consumer, pull subscription with ack

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition

---

## Commit: (planned) — NATS Cluster Bus (Phase 5)

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality (sharing state, workload balancing, discovery, optimized processing). Cover generic networking as well as HTTP-related activities (supporting web servers cluster). Create plan with reasonable split into phases."

### Reformulated Requirements
1. Define `NatsClusterBus` — cluster-wide pub/sub over NATS for inter-node messaging
2. Define `NatsClusterConfig` — configuration for cluster bus (NATS connection, subjects, queue groups)
3. Define `NatsClusterHealthBus` — health check propagation via NATS
4. Define `NatsDistributedPubSub` — publish-subscribe with cluster-wide topic distribution
5. Support topic-based routing: publish to a subject, all cluster nodes subscribe
6. Support queue-based processing: only one node handles each message
7. Health bus must handle restart gracefully (recreate scheduler on close/restart)
8. All operations must be async (CompletableFuture) with proper error handling
9. Integration with `ClusterTransport` SPI from cluster core

### Final Design Decisions
- **Package:** `ssg.legoflow.messaging.nats.cluster` (extension to existing NATS module)
- **Dependencies:** `network/cluster/core` for `ClusterNode`, `ClusterEvent`
- **Strategy:** NATS as the backing transport for cluster messaging
- **Topic convention:** `cluster.<cluster-name>.<operation>` for routing
- **Health bus** uses `ScheduledExecutorService` for periodic health pings with graceful restart

### Implementation Details
- `NatsClusterBus.java` — cluster bus wrapping NATS connection for pub/sub
- `NatsClusterConfig.java` — NATS connection settings + cluster topic prefixes
- `NatsClusterHealthBus.java` — periodic health ping with recovery on restart
- `NatsDistributedPubSub.java` — distributed publish-subscribe with ack tracking

### Test Coverage
| Test Class | Coverage |
|-----------|----------|
| `NatsClusterBusTest` | Pub/sub lifecycle, topic routing, message delivery |
| `NatsClusterConfigTest` | Builder, defaults, validation |
| `NatsClusterHealthBusTest` | Health pings, restart after close, scheduler recreation |
| `NatsDistributedPubSubTest` | Distributed publish, subscriber notification, acks |
| **Tests added**: 4 (in `messaging/nats/src/test/java/.../cluster/`) |

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~8000 |
| Agent tool calls | ~15 |
| Agent wall time | ~20 min |
| Files created/modified | 12 |
| Lines added/removed | +500 / -3 |
| Tests added | 4 |

---
