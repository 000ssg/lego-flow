# Kafka Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 218
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: Apache Kafka Wire Protocol (v2 record batch format)

---

## Requirements

### Wire Protocol Codec
1. Encode and decode request/response frames with 4-byte length prefix
2. Encode/decode request headers: apiKey (int16), apiVersion (int16), correlationId (int32), clientId (nullable string)
3. Encode/decode response headers: correlationId (int32)
4. Support all 18 API types: Produce (0), Fetch (1), ListOffsets (2), Metadata (3), OffsetCommit (8), OffsetFetch (9), FindCoordinator (10), JoinGroup (11), Heartbeat (12), LeaveGroup (13), SyncGroup (14), DescribeGroups (15), ApiVersions (18), CreateTopics (19), DeleteTopics (20), InitProducerId (22), AddPartitionsToTxn (24), EndTxn (26)
5. Use Kafka string encoding: 2-byte length prefix, -1 for null
6. Use Kafka array encoding: 4-byte count prefix, -1 for null

### Record Batch v2
1. Implement Kafka v2 record batch format (magic byte = 2)
2. Encode/decode full batch header: baseOffset, batchLength, partitionLeaderEpoch, magic, CRC32C, attributes, lastOffsetDelta, baseTimestamp, maxTimestamp, producerId, producerEpoch, baseSequence, recordCount
3. Encode/decode individual records with varint (zigzag encoded) lengths and deltas
4. Support record keys, values, and headers (key-value pairs)
5. Compute and verify CRC32C checksums over attributes-through-records
6. Support GZIP compression (JDK-only, java.util.zip)
7. Define compression types: NONE, GZIP, SNAPPY (unsupported), LZ4 (unsupported), ZSTD (unsupported)

### Broker
1. Accept TCP connections using ServerSocketChannel with virtual thread per connection
2. Parse 4-byte length prefix, decode request header, dispatch to typed handler
3. Manage topics: create with configurable partition count, delete, list
4. Auto-create topics on first produce if they don't exist
5. Maintain per-partition append-only logs (PartitionLog) with offset assignment
6. Support configurable default partition count and broker ID
7. Route produce requests to partition logs, return baseOffset and timestamp
8. Serve fetch requests from partition logs with offset range and maxBytes
9. Serve list-offsets requests: earliest (-2), latest (-1), and timestamp-based
10. Serve metadata requests: broker list and topic/partition layout
11. Thread-safe concurrent access using ConcurrentHashMap and ReadWriteLock

### Consumer Group Coordinator
1. Implement full consumer group state machine: EMPTY, PREPARING_REBALANCE, COMPLETING_REBALANCE, STABLE, DEAD
2. Handle JoinGroup: assign member IDs, elect leader, increment generation, select protocol
3. Handle SyncGroup: leader provides assignments, all members receive their assignment
4. Handle Heartbeat: validate generation ID, detect rebalance-in-progress
5. Handle LeaveGroup: remove member, trigger rebalance if leader leaves
6. Store committed offsets per group per topic-partition
7. Fetch committed offsets for a group's assigned partitions
8. Detect expired members via heartbeat timeout and trigger rebalance
9. Encode/decode partition assignments and subscription metadata in binary format
10. Describe group: return state, protocol, members, assignments

### Transaction Manager
1. Allocate producer IDs with monotonically increasing counter
2. Support transactional producers: map transactionalId -> producerId + epoch
3. Epoch fencing: bump epoch on re-init, reject stale-epoch requests
4. Idempotent dedup: track per-producer per-partition last sequence number
5. Detect duplicates (same baseSequence range) and return success
6. Detect out-of-order sequences and return error
7. Manage transaction state: EMPTY, ONGOING, PREPARE_COMMIT, PREPARE_ABORT, COMPLETE_COMMIT, COMPLETE_ABORT, DEAD
8. AddPartitionsToTxn: register partitions, transition to ONGOING
9. EndTxn: commit or abort, clear partitions, return to EMPTY

### Producer Client
1. Establish TCP connection and optionally initialize producer ID (idempotent mode)
2. Send records with configurable partitioner (key hash, round-robin) or explicit partition
3. Build RecordBatch v2 with CRC32C, encode, compress, and frame
4. Cache topic partition counts from metadata responses
5. Support configurable acks (0, 1, -1/all)
6. Retry failed sends with configurable count and backoff
7. Handle duplicate sequence numbers as success (idempotent guarantee)
8. Transaction API: beginTransaction, addPartitionsToTransaction, commitTransaction, abortTransaction
9. String key/value convenience methods

### Consumer Client
1. Connect and subscribe to topic list
2. Join consumer group via JoinGroup/SyncGroup protocol
3. If elected leader, fetch metadata and perform range partition assignment
4. Start periodic heartbeat on virtual thread (sessionTimeout/3 interval)
5. Poll for records: build Fetch request per assigned partition, decode record batches
6. Track per-partition current position (next offset to fetch)
7. Auto-commit offsets at configurable interval
8. Manual commit via commitSync()
9. Seek to specific offset, seek to beginning
10. Rebalance listener callback (onPartitionsAssigned, onPartitionsRevoked)
11. Graceful close: auto-commit, leave group, close connection
12. Fetch committed offsets on group join to resume from last position

### Admin Client
1. Connect to broker via TCP
2. Negotiate API versions (ApiVersions request)
3. Fetch cluster metadata (all topics or specific)
4. Create topics with partition count and replication factor
5. Delete topics by name
6. Describe consumer groups
7. List offsets for topic-partitions
8. Find coordinator for group or transactional ID

### Common Types
1. ApiKey enum: all 18 API keys with numeric key, name, min/max version
2. KafkaErrors enum: 50+ error codes with numeric code and message
3. TopicPartition record: validated topic + partition pair
4. Node record: broker ID + host + port
5. Partitioner functional interface with factory methods for key-hash and round-robin
6. KeyHashPartitioner: murmur2-style hash of key bytes modulo partition count
7. RoundRobinPartitioner: AtomicInteger counter modulo partition count

### Demo Applications
1. SimpleProducerConsumerDemo: start broker, produce N messages, consume and verify count
2. AdminClientDemo: API versions, create topics, metadata, delete topics
3. TransactionalProducerDemo: transactional producer with commit/abort parameter

---

---

## Commit: `(pending)` — Kafka Full Functional Support (2026-07-06)

### Original Request
> "try to provide full functional support for kafka (too many unimplemented in compliance)."

### Reformulated Requirements
1. Implement all remaining API keys identified as ❌ in COMPLIANCE.md (19 new API keys: 4,5,6,7,16,17,21,23,25,27,28,32,33,36,37,42,45,46,47)
2. Add SASL authentication with PLAIN (RFC 4616) and SCRAM-SHA-256 (RFC 7677)
3. Add multi-broker in-process simulation with leader election, reassignment, controlled shutdown
4. Complete transaction support: AddOffsetsToTxn, TxnOffsetCommit for consumer-in-transaction pattern
5. Add dynamic configuration management (DescribeConfigs, AlterConfigs)
6. Add admin APIs: ListGroups, DeleteGroups, CreatePartitions, DeleteRecords, OffsetDelete
7. Add pluggable partition assignment strategies: range (existing), sticky, cooperative-sticky (KIP-429)
8. Add log compaction (key-based deduplication with tombstone handling)
9. Do NOT implement Snappy/LZ4/ZStd compression (require native libraries, violates JDK-only policy)

### Final Design Decisions
- **7-phase implementation**: Admin APIs → Config APIs → Transaction completion → SASL Auth → Multi-broker → Consumer groups → Log compaction
- **SASL per-connection state**: Each TCP connection tracks auth progress via `ConnectionState` inner class in KafkaBroker
- **SCRAM-SHA-256 uses JDK crypto**: `PBKDF2WithHmacSHA256` for key derivation, `HmacSHA256` for proof verification — no external crypto libraries
- **Multi-broker is in-process simulation**: `BrokerCluster` manages N `KafkaBroker` instances without actual inter-broker network replication
- **Cooperative rebalance gated by protocol name**: `cooperative-sticky` protocol triggers diff-based revocation; existing `range` tests unaffected
- **ConfigManager integrated with topic creation**: default topic configs applied automatically on `createTopic()`
- **Transaction consumer-in-transaction**: `TransactionManager` stores pending offsets and flushes to `ConsumerGroupCoordinator` on commit

### Implementation Details

**Phase 1 — Admin APIs (5 API keys)**
- ListGroups (16), DeleteRecords (21), CreatePartitions (37), DeleteGroups (42), OffsetDelete (47)
- New error codes: NON_EMPTY_GROUP (68), GROUP_ID_NOT_FOUND (69)
- New: PartitionLog.truncateBefore(), ConsumerGroupCoordinator.listGroups()/deleteGroup()/deleteOffsets()
- KafkaAdminClient: 5 new client methods

**Phase 2 — Config APIs (2 API keys + ConfigManager)**
- DescribeConfigs (32), AlterConfigs (33)
- New: `broker/ConfigManager.java` — per-topic and broker-level config storage
- Mutable topic configs: retention.ms, cleanup.policy, max.message.bytes, segment.bytes, min.insync.replicas
- Read-only broker configs: num.partitions, log.retention.ms, message.max.bytes, default.replication.factor

**Phase 3 — Transaction Completion (2 API keys)**
- AddOffsetsToTxn (25), TxnOffsetCommit (28)
- TransactionManager: pending offset storage, group ID tracking, flush on commit/discard on abort
- CONSUMER_OFFSETS_PARTITIONS = 50

**Phase 4 — SASL Authentication (2 API keys + auth package)**
- SaslHandshake (17), SaslAuthenticate (36)
- New package: `auth/` with SaslMechanism interface, PlainSaslServer, ScramSha256Server, CredentialStore, AuthenticationException
- Per-connection ConnectionState in KafkaBroker for auth tracking

**Phase 5 — Multi-Broker Simulation (8 API keys + infrastructure)**
- LeaderAndIsr (4), StopReplica (5), UpdateMetadata (6), ControlledShutdown (7), OffsetForLeaderEpoch (23), WriteTxnMarkers (27), AlterPartitionReassignments (45), ListPartitionReassignments (46)
- New: `broker/ReplicaManager.java` — per-broker replica state (leader/ISR/epoch per partition)
- New: `broker/BrokerCluster.java` — multi-broker management, leader election, reassignment, controlled shutdown

**Phase 6 — Consumer Group Enhancements**
- New: `broker/PartitionAssigner.java` interface, `RangeAssigner.java`, `StickyAssigner.java`
- Cooperative rebalance (KIP-429): diff-based revocation, onPartitionsLost() callback
- KafkaConsumer: assignmentStrategy field, cooperative-sticky support

**Phase 7 — Log Compaction**
- PartitionLog.compact(): key-based deduplication, tombstone handling, null-key retention
- KafkaBroker.compactAll(): triggers compaction on cleanup.policy=compact topics

**Files created:** 49 new Java source files (38 protocol records, 5 auth classes, 3 broker classes, 3 assigner classes)
**Files modified:** 12 existing Java files (ApiKey, KafkaErrors, KafkaCodec, KafkaBroker, ConsumerGroupCoordinator, TransactionManager, PartitionLog, KafkaAdminClient, KafkaConsumer, RebalanceListener + test files)

### Test Coverage
- New tests: 146 (218 → 364)
- New test classes: ConfigManagerTest (8), ReplicaManagerTest (8), BrokerClusterTest (7), PartitionAssignerTest (6), CredentialStoreTest (5), PlainSaslServerTest (4), ScramSha256ServerTest (6), RebalanceListenerTest (2)
- Modified test classes: KafkaCodecTest (85 total), KafkaBrokerTest (27), ConsumerGroupCoordinatorTest (36), TransactionManagerTest (31), PartitionLogTest (20), KafkaAdminClientTest (26), KafkaConsumerTest (17)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 7 (one per phase) |
| Agent tokens | ~350,000 |
| Agent tool calls | ~280 |
| Agent wall time | ~35 min |
| Files created/modified | 61 |
| Lines added/removed | +6,000 / -200 |
| Tests added | 146 (total: 364) |

---

## Commit: `pending` — Pluggable Disk Persistence (2026-07-06)

### Original Request
> "add support for disk persistence to kafka broker (optional, by default - in memory). do it via interface(s) to allow alternative implementations, but provide guessed most effective one."

### Reformulated Requirements
1. Extract storage interface from PartitionLog for pluggable backends
2. Provide in-memory implementation preserving current behavior (default)
3. Provide disk-based implementation using the most effective approach
4. Make KafkaBroker configurable to select storage backend
5. All existing tests must pass unchanged

### Final Design Decisions
- **`LogStorage` interface** with `append`, `fetch`, `allBatches`, `replaceBatches`, `truncateBefore` — clean separation of storage from partition logic
- **`LogStorageFactory`** as `@FunctionalInterface` — enables lambda and custom implementations
- **Memory-mapped file storage** chosen as the "most effective" implementation because Kafka's access pattern (sequential append + sequential read) perfectly suits mmap: zero-copy reads through OS page cache, automatic dirty page writeback, no explicit fsync needed
- **Segment-based design** matching real Kafka: files split at configurable segment size (default 1 GB), initial mapping 16 MB with auto-grow
- **Sparse offset index** for fast binary-search seek on fetch (one entry per 4 KB)
- **Recovery on construction** — scan existing segment files to rebuild index and nextOffset
- **Thread safety remains in PartitionLog** — storage implementations are NOT thread-safe by design (simpler, avoids double-locking)

### Implementation Details
- New package: `broker/storage/` with 5 classes (StoredBatch, LogStorage, LogStorageFactory, InMemoryLogStorage, MappedFileLogStorage)
- Modified: PartitionLog (delegates to LogStorage), KafkaBroker (accepts LogStorageFactory, 5-arg constructor)
- 3 new test classes: InMemoryLogStorageTest (13 tests), MappedFileLogStorageTest (15 tests), LogStorageFactoryTest (6 tests)
- Backward compatible: existing 2-arg PartitionLog constructor defaults to InMemoryLogStorage

### Test Coverage
- 35 new tests across 3 test classes
- Total: 399 tests

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (disk persistence) |
| Agent tokens | ~81K |
| Agent tool calls | ~29 |
| Agent wall time | ~6 min |
| Files created/modified | 10 |
| Lines added/removed | +1200 / -30 |
| Tests added | 35 (total: 399) |

---

## Phase 4: Compliance migration — headless broker core, transport SPI, service-layer I/O (messaging plan)

Part of the messaging compliance series defined in `doc/plans/messaging/` — applies the Phase 2/3 (NATS/XMPP) headless pattern to the Kafka module.

### What Changed
- **Transport SPI**: new `KafkaTransport` byte-level SPI (`send`, `receiveWithTimeout`, `peek`, `isOpen`, `close` + `add`/`onWrite` for the pipeline). `InMemoryKafkaTransport.createPair()` for tests/demos (deterministic, no sockets); `PipelineKafkaTransport` for production (64 KB ring buffer over a `DataChannel`, selector-thread driven, never touches a socket directly).
- **Headless core**: `KafkaBroker` no longer owns a `ServerSocketChannel`/accept loop — connections arrive via `handleConnection(KafkaTransport)` on a virtual-thread read loop. `KafkaBrokerService` (service layer) owns the TCP listener through the `SelectableChannelManager` and feeds each inbound connection to the broker core. The client (`KafkaProducer`/`KafkaConsumer`/`KafkaAdminClient`) takes a `KafkaTransport` in its constructor; the package-private `KafkaConnection` is now a headless frame-level correlation-ID wrapper over an injected transport (never a socket). Zero raw sockets in the protocol packages (broker/codec/protocol/common/record/transport).
- **Bug found & fixed during reassembly testing**: the in-memory transport re-queued a partially-read buffer's tail at the *back* of the queue, rotating the byte stream when two sends were in flight — a frame split across reads no longer reassembled in order. Fixed by holding the partially-read buffer at the stream head (`headBuffer`); regression tests added (`KafkaTransportTest` two-send interleaving, `KafkaBrokerTest` partial-prefix / fragmented-body / coalesced-frames).
- **Service layer**: `KafkaBrokerService` + `KafkaClientService` with channel handlers are the only components touching NIO (non-blocking `ServerSocketChannel`/`SocketChannel` via `SelectableChannelManager`), wiring `PipelineKafkaTransport` into the headless cores.
- **Demos**: all five demos migrated to the dual-backend pattern (`KafkaDemoClient.inMemory` for the in-house broker, `KafkaDemoClient.tcp` via `KafkaClientService` for an external broker).

### Test Coverage
- 416 tests, 0 failures (was 399): transport trio (`KafkaTransportTest` 18, `KafkaServiceIntegrationTest` 3 real-TCP), broker wire-reassembly (3), service unit tests, migrated client/broker tests on the in-memory seam
- JaCoCo instruction coverage 91.9% (≥80% gate); weakest package `service` at 65.9% (integration tests exercise the DP/DF pipeline; builder/lifecycle branches remain)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files created | 5 (KafkaTransport, InMemoryKafkaTransport, PipelineKafkaTransport, KafkaBrokerService, KafkaClientService + 2 channel handlers) |
| Files modified | ~20 (broker, client, service, tests, demos, docs) |
| Tests added | ~17 (416 total vs 399 before) |

---

## Commit: `dbd46370` — Fetch v9/v10 (Record I/O rows 20–21) + hybrid builder approach

Part of the messaging compliance series (`doc/plans/messaging/`). Spec-verified from the 3.6.1 `FetchRequest.json` / `FetchResponse.json` schemas.

### What Changed
- **Fetch v9/v10 codec**: v9 is a STRUCTURAL BRANCH on the REQUEST ONLY — the per-partition layout gains `CurrentLeaderEpoch(int32)` after `Partition` (default -1) — dedicated `encode/decodeRequestV9`. v10 request is wire-identical to v9 (no field change) and dispatch falls through to the v9 request methods. The v9/v10 RESPONSE is wire-identical to v7/v8 and falls through to the v7 response methods in both directions. `FetchRequest.PartitionFetch` gains `currentLeaderEpoch` (v9+, -1 default) with a compatibility constructor for pre-v9 call sites.
- **Hybrid builder approach** (in-session decision, first application): records stay records — canonical + compat constructors untouched (free `equals`/`hashCode`/`toString`; every existing positional call site keeps compiling); a static nested `Builder` is added as the preferred entry point, one method per field defaulting to the spec absent-value, `build()` = pure delegation to the canonical constructor, no per-version validation (the codec enforces field presence on write and auto-fills absent fields on read). Scope rule: records with >=3 fields or version-growth get builders; 1–2-field records (SaslHandshake*, SaslAuthenticateRequest, ApiVersionsRequest, ApiVersion, AbortedTransaction, nested TopicResponse/TopicFetch/ForgottenTopic) stay plain. Applied to the 8 builder records: `FetchRequest`, `FetchRequest.PartitionFetch`, `FetchResponse`, `FetchResponse.PartitionResponse`, `ProduceRequest`, `ProduceResponse.PartitionResponse`, `ApiVersionsResponse`, `SaslAuthenticateResponse`.
- **New tests** (all builder-based): FetchCodecTest 53 -> 63 (+10): RequestV9 x4 (round-trip incl. CurrentLeaderEpoch; exact 72-byte walk; v9 = v7 + 4-byte CurrentLeaderEpoch structural byte-identity at offset 44; default -1 round-trip) + RequestV10 x2 (byte-identical to v9; round-trip) + ResponseV9 x2 + ResponseV10 x2 (byte-identical to v7; round-trip through the v7 methods). Dispatch re-pinned: v11 is now the next unimplemented version (both directions).

### Test Coverage
- FetchCodecTest 63 tests; full module 570 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 8 (FetchCodec, 7 protocol records, FetchCodecTest) |
| Lines added/removed | +735 / -35 |
| Tests added | 10 (570 total) |

---

## Commit: `5205b7f1` — retrofit the 4 dedicated-codec test classes onto the hybrid builders

Mechanically rewrite every canonical-arity positional constructor call in the Produce / Fetch / NegotiationAuth / KafkaCodec test classes onto the builder chains from `dbd46370`. Records stay records — no model changes.

### What Changed
- **62 canonical-arity call sites rewritten** onto fluent builder chains (one method per field, spec absent-value defaults, `build()` delegating to the canonical constructor): `ProduceCodecTest` 27, `FetchCodecTest` 22, `NegotiationAuthCodecTest` 9, `KafkaCodecTest` 4. Net −73 lines (positional argument lists replaced by named fluent calls — the builder makes each field's version semantics explicit at the call site).
- **Compatibility-constructor sites left positional** (fewer args than the record arity): they carry per-version absent-value semantics the builder defaults provide, and auto-mapping them would silently change which overload resolves.
- **Mechanical transform**: `messaging/kafka/.builder_transform.py` — one canonical `new X(...)` site rewritten per pass (re-scan after each, so a nested `new A.B(...)` inside a rewritten `new A(...)` becomes a nested builder chain on a later pass); a `new X(` match whose name is immediately followed by `.` is skipped (qualified type — its args belong to the nested record); arity gate = exact match of top-level arg count to the record's canonical component count (verified against each record's header); comments / string literals / `byte[]{...}` / generics handled by a single-pass state machine tracking `()[]{}` depth and literal state without mutating the text. Reusable verbatim for the next dedicated-codec rows (Admin / Transactions / Consumer Groups / Metadata) — extend the script's `RECORDS` table with the new record's qualified type + canonical component order first.

### Test Coverage
- Full module 570 green, 0 failures, 0 errors, 0 skipped (no test added or removed — pure call-site refactor)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 4 (test classes) |
| Lines added/removed | +98 / -171 |
| Tests added | 0 |

---

## Commit: `794a9417` — Fetch v11 (Record I/O row 22)

Part of the messaging compliance series (`doc/plans/messaging/`). Spec-verified from the 3.6.1 `FetchRequest.json` / `FetchResponse.json` schemas.

### What Changed
- **Fetch v11 codec**: v11 is a STRUCTURAL BRANCH in BOTH directions. REQUEST: appends `RackId(string)` as the last field after the `ForgottenTopicsData` array (spec: "Rack ID of the consumer making this request"; default "") — dedicated `encode/decodeRequestV11`. RESPONSE: the per-partition layout inserts `PreferredReadReplica(int32)` after the `AbortedTransactions` array and before `Records` (spec: "The preferred read replica for the consumer to use on its next fetch request"; default -1) — dedicated `encode/decodeResponseV11`.
- **Models**: `FetchRequest` gains `rackId` (v11+, "" default); `FetchResponse.PartitionResponse` gains `preferredReadReplica` (v11+, -1 default). The pre-v11 legacy methods that build the models (v7/v9 request decodes, v5/v7 response decodes) now pass the spec absent-values ("", -1) on the canonical-constructor calls, keeping their output byte-identical to the pre-v11 wire layout.
- **New tests** (all builder-based): FetchCodecTest 63 -> 71 (+8): RequestV11 x4 (round-trip incl. RackId + forgotten topics; exact 79-byte walk; v11 = v9 + 7-byte trailing RackId structural byte-identity; "" default round-trip) + ResponseV11 x4 (round-trip incl. PreferredReadReplica + aborted transactions; exact 83-byte walk; v11 = v7 + 4-byte per-partition PreferredReadReplica structural byte-identity at offset 75; -1 default round-trip). Dispatch re-pinned: v12 is now the next unimplemented version (both directions).

### Test Coverage
- FetchCodecTest 71 tests; full module 578 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 4 (FetchCodec, 2 protocol records, FetchCodecTest) |
| Lines added/removed | +510 / -45 |
| Tests added | 8 (578 total) |

---

## Commit: `97c82bb3` — Fetch v12 (Record I/O row 23)

Part of the messaging compliance series (`doc/plans/messaging/`). Spec-verified from the 3.6.1 `FetchRequest.json` / `FetchResponse.json` generated sources.

### What Changed
- **Fetch v12 codec**: v12 is a STRUCTURAL BRANCH in BOTH directions and the first FLEXIBLE-ENCODING version of the Fetch API. REQUEST: compact strings + varint array counts + per-element tagged sections throughout; the per-partition layout gains `LastFetchedEpoch(int32, default -1)` after `FetchOffset`; `ClusterId(string, null:12+)` rides in the trailing top-level tagged section as tag 0 (implicit compact nullable string, written only when non-null). RESPONSE: flexible encoding; the per-partition tagged section gains tag 0 `DivergingEpoch(EpochEndOffset)`, tag 1 `CurrentLeader(LeaderIdAndEpoch)` and tag 2 `SnapshotId(SnapshotId)` — each written only when non-null. Verified wire-layout traps: the partition tagged section sits AFTER `Records` (flexible nullable bytes), not before `AbortedTransactions`; each `AbortedTransaction` element carries its own trailing `varint(0)` section; `PreferredReadReplica(int32)` stays the fixed-width field between `AbortedTransactions` and `Records`. Mechanism per plan section 3: dedicated `encode/decodeRequestV12` + `encode/decodeResponseV12` in `FetchCodec` (reusing the `KafkaCodecPrimitives` varint / compact-string / tagged helpers); legacy v11-and-earlier paths untouched.
- **Models**: `FetchRequest` gains `clusterId` (v12+, null default) with a 10-arg pre-v12 compat constructor; `PartitionFetch` gains `lastFetchedEpoch` (v12+, -1 default) with a 5-arg compat constructor; `FetchResponse.PartitionResponse` gains `divergingEpoch` / `currentLeader` / `snapshotId` (v12+, null defaults) with a 5-arg pre-v12 compat constructor; new nested `DivergingEpoch` / `LeaderIdAndEpoch` / `SnapshotId` records; builders gain the v12 setters.
- **New tests** (all builder-based): FetchCodecTest 71 -> 77 (+6): RequestV12 x3 (round-trip incl. ClusterId + LastFetchedEpoch + forgotten topics; exact 87-byte walk; ClusterId null round-trip -> tag 0 absent, trailing section count 0) + ResponseV12 x3 (round-trip incl. all three partition tags; exact 118-byte walk; no-records/no-tags 57-byte walk). Dispatch re-pinned: v13 is now the next unimplemented version (both directions).

### Test Coverage
- FetchCodecTest 77 tests; full module 584 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 4 (FetchCodec, 2 protocol records, FetchCodecTest) |
| Lines added/removed | +830 / -58 |
| Tests added | 6 (584 total) |

---

## Commit: `abb533be` — Fetch v13 (Record I/O row 24)

Part of the messaging compliance series (`doc/plans/messaging/`). Spec-verified from the vendored `FetchRequest.json` / `FetchResponse.json` (cross-checked against the Apache trunk schemas and the 3.7.0 `kafka-clients` generated source).

### What Changed
- **Fetch v13 codec**: v13 is a STRUCTURAL BRANCH in BOTH directions: the `Topic(string)` name is replaced by a non-nullable fixed 16-byte `TopicId(uuid)` in all three topic-carrying structs — request `Topics`, request `ForgottenTopicsData`, response `Responses` (delta per plan row 128: `+ TopicId:uuid[13+]` x3, `- Topic:string` x3). The topic name is absent from the v13+ wire, so decoded models carry `name == null`. The uuid is non-nullable (spec: `TopicId:uuid`, versions 13+, no nullableVersions) — no varint prefix, just the raw UUID bytes; absent = all-zeros (spec default). Everything else (flexible encoding, `LastFetchedEpoch`, the per-partition tagged section, the tagged `ClusterId`) is wire-identical to v12. Dispatch (verified from the vendored spec): request v13 + v14 are wire-identical (v14 adds no field) and both map to the dedicated `encode/decodeRequestV13` methods; request v15 (a later, request-only change) introduced the tagged `ReplicaState`. Response v13/v14/v15 are wire-identical and all map to the dedicated `encode/decodeResponseV13` methods. Mechanism per plan section 3: dedicated `encode/decodeRequestV13` + `encode/decodeResponseV13` in `FetchCodec` (+ `writeFetchPartitionV13` shared partition writer; hand-sized buffers; `KafkaCodecPrimitives` compact-string / varint / tagged helpers; new `readUuid`/`writeUuid` fixed 16-byte uuid primitives); legacy v12-and-earlier paths untouched.
- **Models** (overloaded compatibility constructors only — all 114 existing call sites use the 2-arg canonical constructors): `FetchRequest.TopicFetch` + `topicId` (`byte[16]`, all-zeros default) + `topicUuid()`; `FetchRequest.ForgottenTopicData` + `topicId` + `topicUuid()`; `FetchResponse.TopicResponse` + `topicId` + `topicUuid()`; new `protocol/Uuid` helper for the raw 16-byte wire form (8-byte MSB + 8-byte LSB, big-endian) to/from `java.util.UUID` — JDK-portable, no preview APIs.
- **New tests** (all builder-based): FetchCodecTest 77 -> 83 (+6): RequestV13 x3 (round-trip incl. TopicId + LastFetchedEpoch + forgotten topics; exact 97-byte walk vs the v12 87-byte reference; forgotten-topic TopicId round-trip with exact 106-byte walk) + ResponseV13 x3 (round-trip incl. TopicId + all three partition tags; exact 128-byte walk vs the v12 118-byte reference; null-records / absent-tags round-trip). Dispatch re-pinned: v13/v14 now implemented (both directions fall through to the V13 methods), next unimplemented = request v15 (tagged ReplicaState) and response v16.

### Test Coverage
- FetchCodecTest 83 tests; full module 590 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 5 (FetchCodec, KafkaCodecPrimitives, 2 protocol records, FetchCodecTest) + 1 new (protocol/Uuid) |
| Lines added/removed | +846 / -30 |
| Tests added | 6 (590 total) |

---

## Commit: `051f61f0` — Fetch v15 (Record I/O row 26)

- **FetchRequest**: v15 is a STRUCTURAL BRANCH in the REQUEST only: the top-level `ReplicaId:int32` (present versions 0-14) is dropped, replaced by a tagged `ReplicaState` struct (versions 15+, tag 1) carrying `ReplicaId:int32` + `ReplicaEpoch:int64` (delta per plan row 129: `+ ReplicaState[15+]`, `- ReplicaId:int32[0-14]`). The consumer case (both -1) omits the tag entirely. The RESPONSE is wire-identical to v14 — `gen_kafka.py order` 14 14 vs 15 15 produce byte-identical field sequences and FetchResponse.json has no `15+` field at any nesting level. Model gains `replicaEpoch` (int64) with a compat-constructor default (-1) and a builder setter; the 7-arg and 8-arg compat constructors are preserved.
- **FetchCodec**: request `case 15` routes to dedicated `encode/decodeRequestV15` which writes/reads the tagged `ReplicaState` (tag 1) after the flexible-tagged `ClusterId` (tag 0); the consumer case (replicaId==replicaEpoch==-1) omits the tag. Response `case 15` routes to the V13 response methods (request-only change). Legacy v14-and-earlier paths untouched.
- **Dispatch (verified from the vendored spec)**: request v15 = `case 15` → `encode/decodeRequestV15`; response v14 and v15 both → `encode/decodeResponseV13` (response wire-identical). Next unimplemented request version: v16.
- **Tests**: `FetchCodecTest` 83 → 87 (+4 net: 6 new v15 tests — `v15ConsumerExactBytes`, `v15GroupRoundTrip`, `v15RequestExactBytes`, `v15ResponseUnchangedFromV14`, `v15DispatchAndFallthrough`, `v15ConsumerOmitsTag` — plus 2 old v15-stub `CodecNotImplemented` methods removed); full module 590 → 594 green.

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 3 (FetchRequest, FetchCodec, FetchCodecTest) |
| Lines added/removed | +416 / -31 |
| Tests added | 6 net +4 (594 total) |

---

## Commit: `ec1a6ea3` — ListOffsets v0–v8 (Record I/O rows 137–145, sub-category completion)

- **ListOffsets v0–v8 codec**: new dedicated `ListOffsetsCodec` — the first spec-correct ListOffsets implementation (all 9 rows at once, per the vendored ListOffsetsRequest.json / ListOffsetsResponse.json, spec-validated in `doc/spec/SPEC_VALIDATION_ListOffsets.md`). The old inline `KafkaCodec.encodeListOffsetsRequest` omitted the mandatory leading `ReplicaId` int32 (it started at `numTopics`), so every admin ListOffsets call was malformed on the wire; the new codec writes `ReplicaId` first. Client/broker interaction is pinned at v1 (the earliest version returning the per-partition `Timestamp` + `Offset` pair); the full range v0–v8 is encoded/decoded in both directions: v0 request `MaxNumOffsets` int32, v0 response `OldStyleOffsets` int64[1+], v2+ request `IsolationLevel` int8 / response `ThrottleTimeMs` int32, v4+ `CurrentLeaderEpoch` / `LeaderEpoch` int32, v6+ flexible encoding with compact arrays/strings + tagged fields.
- **Models** (constructor arities preserved — all call sites unchanged): `ListOffsetsRequest` / `ListOffsetsResponse` are kept at the compact v1 shape (`PartitionIndex` / `Timestamp` / `Offset`); version-gated fields are handled in the codec — written with their spec defaults and read + discarded on decode — so `PartitionOffsets(partitionIndex, timestamp)` and `PartitionResponse(partitionIndex, errorCode, timestamp, offset)` keep their arities and `KafkaBroker` / `KafkaAdminClient` callers are untouched.
- **KafkaCodec facade**: `encode/decodeListOffsetsRequest` and `encode/decodeListOffsetsResponse` now delegate to `ListOffsetsCodec`; the malformed inline `encodeListOffsetsRequest` is removed.
- **New tests**: `ListOffsetsCodecTest` (23 tests — byte-for-byte layout assertions vs the canonical spec JSONs, exact 13-byte v1 request + 18-byte v1 response walks, pin validation, full v0–v8 range both directions, version guards).

### Test Coverage
- ListOffsetsCodecTest 23 tests; full module 594 → 617 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 1 (KafkaCodec) + 2 new (ListOffsetsCodec, ListOffsetsCodecTest) |
| Lines added/removed | +863 / -51 |
| Tests added | 23 (617 total) |

- **Sub-category**: Record I/O is now complete (35 rows = Produce v0–9 + Fetch v0–15 + ListOffsets v0–8); remaining Admin (35), Transactions (24), Consumer Groups (63), Metadata/Cluster (44).

## Commit: `6a9bd355` — CreateTopics v0–v7 (Admin rows 167–174, sub-category start)

- **CreateTopics v0–v7 codec**: new dedicated `CreateTopicsCodec` — the first spec-correct CreateTopics implementation (all 8 rows at once, per the vendored CreateTopicsRequest.json / CreateTopicsResponse.json, frozen order tables in `doc/spec/order/CreateTopics.{Request,Response}.txt`). The old inline `KafkaCodec.encodeCreateTopicsRequest` omitted the mandatory Assignments array in each CreatableTopic (v0+), so every admin CreateTopics frame was desynced against spec-compliant peers; the new codec writes the full spec layout (Topics[] with the Assignments array). Client/broker interaction is pinned at v0; the full range v0–v7 is encoded/decoded in both directions (v0 request base: Topics[] with Name/NumPartitions/ReplicationFactor/Assignments/PartitionIndex/BrokerIds/Configs + timeoutMs; v1+ request validateOnly; v5+ flexible encoding with compact arrays/strings + tagged fields; response v0 Name/ErrorCode, v1+ ErrorMessage (nullable, read+discarded), v2+ ThrottleTimeMs, v5+ TopicConfigErrorCode (tag 0)/NumPartitions/ReplicationFactor/Configs (nullable, spec defaults -1), v7+ TopicId (non-nullable uuid, fixed 16 bytes on the wire)).
- **Models** (constructor arities preserved — all call sites unchanged): `CreateTopicsRequest` gains the nested `TopicCreate` + `Assignment` records + the `assignments` field (the Assignments array is mandatory on the wire v0+); version-gated response fields are handled in the codec — written with their spec defaults and read + discarded on decode.
- **KafkaCodec facade**: `encode/decodeCreateTopicsRequest` and `encode/decodeCreateTopicsResponse` now delegate to `CreateTopicsCodec`; the malformed inline bodies are removed.
- **New tests**: `CreateTopicsCodecTest` (25 tests — byte-for-byte layout assertions vs the canonical spec JSONs, exact pinned v0 request/response walks, full v0–v7 range both directions, flexible layout, version guards).

### Test Coverage
- CreateTopicsCodecTest 25 tests; full module 617 → 642 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files modified | 2 (KafkaCodec, CreateTopicsRequest) + 2 new (CreateTopicsCodec, CreateTopicsCodecTest) |
| Lines added/removed | +989 / -48 |
| Tests added | 25 (642 total) |

- **Sub-category**: Admin started (CreateTopics 8/35 rows); remaining Admin (27), Transactions (24), Consumer Groups (63), Metadata/Cluster (44).

## Commit: doc-only — ListOffsets validation docs + settled rules + work ledger (retroactive)

- **Docs committed** (no code change — these were written during the ListOffsets sub-task but left untracked):
  - `doc/RULES.md` — module-level settled rules (spec JSONs authoritative/frozen, `//` comments legal, field order = spec `fields` order, no re-litigating settled decisions).
  - `doc/CODEC_VALIDATION_ListOffsets.md` — ListOffsets v0–v8 field/byte-order validation notes (LOCKED): the v1 pin rationale, request/response field tables, the old inline facade bug (omitted leading `ReplicaId`), codec design, flexible size-calc notes.
  - `doc/spec/SPEC_VALIDATION_ListOffsets.md` — grounded spec validation: vendored `ListOffsetsRequest/Response.json` are byte-identical to Apache Kafka 3.6.1; all ListOffsets faults were in the Java models + old inline facade, not the spec; hallucinated fields removed, missing fields added; decoded-default semantics table.
  - `doc/work/LISTOFFSETS_SETTLED.md` — reusable evaluation ledger (settled per-version Δ for all APIs used downstream).
  - `doc/work/WIP.md` — fast-recovery ledger (environment, conventions, next activity = DeleteTopics v0–v6, matrix rows 175–181).
- **Effect**: single source of truth for the ListOffsets/CODEC_VALIDATION validation results is now under version control; the WIP ledger documents where the codec work is in flight for sub-2-minute recovery.

### Test Coverage
- No code changes; full module stays 642 green, 0 failures, 0 errors, 0 skipped

### Cost Estimate
| Metric | Value |
|--------|-------|
| Files added | 5 (doc-only) |
| Tests added | 0 |

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition