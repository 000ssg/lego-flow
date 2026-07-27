# Kafka Module — Development Guide

## Module Purpose

The `kafka` module implements the Apache Kafka wire protocol (binary protocol over TCP). It provides a single-node broker (with multi-broker simulation), producer, consumer, and admin client — all JDK-only with no external dependencies beyond SLF4J. The implementation covers 37 Kafka API types, v2 record batch format with CRC32C and GZIP compression, consumer group coordination with range/sticky/cooperative rebalance, idempotent production, full transaction support (including consumer-in-transaction), SASL authentication (PLAIN + SCRAM-SHA-256), dynamic configuration, log compaction, and pluggable storage (in-memory or memory-mapped file persistence).

## Key Interfaces

- `KafkaBroker` — single-node broker with topic management, partition logs, consumer group coordination, transaction manager, config manager, SASL auth, replica manager, virtual threads
- `KafkaProducer` — producer with configurable partitioner, acks, retries, idempotent mode, transactional mode
- `KafkaConsumer` — consumer with group membership (join/sync/heartbeat/leave), partition assignment (range/sticky/cooperative), offset management, rebalance listener
- `KafkaAdminClient` — admin operations: topic CRUD, metadata, API versions, group inspection, offset listing, config describe/alter, partition creation, record deletion
- `KafkaConnection` — low-level TCP connection with frame-level send/receive and correlation ID tracking
- `KafkaCodec` — binary codec for all 37 Kafka API request/response types
- `RecordBatch` — Kafka v2 record batch format (magic=2) with CRC32C, varint encoding, GZIP compression
- `ConsumerGroupCoordinator` — consumer group state machine (EMPTY, PREPARING_REBALANCE, COMPLETING_REBALANCE, STABLE, DEAD), pluggable partition assignment
- `TransactionManager` — producer ID allocation, idempotency dedup, transaction lifecycle (begin, addPartitions, addOffsets, txnOffsetCommit, commit, abort)
- `ConfigManager` — broker + per-topic dynamic configuration (describe, alter, defaults)
- `PartitionLog` — append-only log per partition with read-write locking, truncation, compaction; delegates to `LogStorage` backend
- `LogStorage` — interface for pluggable partition log storage (in-memory or disk-based)
- `LogStorageFactory` — functional factory interface for creating storage instances per partition
- `InMemoryLogStorage` — volatile `ArrayList`-backed storage (default)
- `MappedFileLogStorage` — durable memory-mapped file storage with segment rotation, sparse index, recovery
- `BrokerCluster` — multi-broker in-process simulation (leader election, reassignment, controlled shutdown)
- `ReplicaManager` — per-broker replica state (leader/ISR/epoch per partition)
- `PartitionAssigner` — interface for pluggable assignment strategies (RangeAssigner, StickyAssigner)
- `CredentialStore` — SASL credential storage (PLAIN passwords, SCRAM-SHA-256 derived keys)
- `Partitioner` — functional interface for partition selection (key hash, round-robin)
- `RebalanceListener` — callback for partition assignment/revocation/loss events

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `auth` | SASL authentication: `SaslMechanism` (interface), `PlainSaslServer` (RFC 4616), `ScramSha256Server` (RFC 7677), `CredentialStore` (passwords + SCRAM keys), `AuthenticationException` |
| `broker` | Broker implementation: `KafkaBroker` (TCP accept loop, request dispatch), `ConsumerGroupCoordinator` (group protocol), `PartitionLog` (append-only log + compaction), `TransactionManager` (idempotency + txn + consumer-in-txn), `ConfigManager` (dynamic config), `BrokerCluster` (multi-broker simulation), `ReplicaManager` (replica state), `PartitionAssigner`/`RangeAssigner`/`StickyAssigner` (assignment strategies) |
| `broker/storage` | Pluggable storage backend: `LogStorage` (interface), `LogStorageFactory` (factory), `InMemoryLogStorage` (volatile default), `MappedFileLogStorage` (durable, segment-based, memory-mapped), `StoredBatch` (batch record) |
| `client` | Client implementations: `KafkaProducer` (send, batching, idempotent, transactional), `KafkaConsumer` (subscribe, poll, commit, seek, heartbeat, cooperative rebalance), `KafkaAdminClient` (topic CRUD, metadata, config, groups), `KafkaConnection` (TCP framing), `ConsumerRecord`, `RebalanceListener` |
| `codec` | `KafkaCodec` — static encode/decode methods for all 37 API types, frame-level request/response encoding, string helpers |
| `common` | Shared types: `ApiKey` (37 API key enums with version ranges), `KafkaErrors` (50+ error codes), `TopicPartition`, `Node`, `Partitioner` (functional interface), `KeyHashPartitioner`, `RoundRobinPartitioner` |
| `protocol` | Request/response records for each API: 74 record classes (37 request + 37 response), `RequestHeader`, `ResponseHeader` |
| `record` | Record batch format: `RecordBatch` (v2/magic=2, CRC32C, varint, compression), `Record`, `Header`, `Compression` enum |
| `demo` | Demo applications: `SimpleProducerConsumerDemo`, `AdminClientDemo`, `TransactionalProducerDemo` |

## Kafka-Specific Coding Conventions

### Wire Protocol Format
- 4-byte length prefix on every message frame
- Request header: apiKey(2) + apiVersion(2) + correlationId(4) + clientId(2+len)
- Response header: correlationId(4)
- Strings: 2-byte length prefix (short), -1 for null
- Arrays: 4-byte count prefix (int), -1 for null

### API Keys (37 supported)
| Key | API | Key | API |
|-----|-----|-----|-----|
| 0 | Produce | 17 | SaslHandshake |
| 1 | Fetch | 18 | ApiVersions |
| 2 | ListOffsets | 19 | CreateTopics |
| 3 | Metadata | 20 | DeleteTopics |
| 4 | LeaderAndIsr | 21 | DeleteRecords |
| 5 | StopReplica | 22 | InitProducerId |
| 6 | UpdateMetadata | 23 | OffsetForLeaderEpoch |
| 7 | ControlledShutdown | 24 | AddPartitionsToTxn |
| 8 | OffsetCommit | 25 | AddOffsetsToTxn |
| 9 | OffsetFetch | 26 | EndTxn |
| 10 | FindCoordinator | 27 | WriteTxnMarkers |
| 11 | JoinGroup | 28 | TxnOffsetCommit |
| 12 | Heartbeat | 32 | DescribeConfigs |
| 13 | LeaveGroup | 33 | AlterConfigs |
| 14 | SyncGroup | 36 | SaslAuthenticate |
| 15 | DescribeGroups | 37 | CreatePartitions |
| 16 | ListGroups | 42 | DeleteGroups |
| 45 | AlterPartitionReassignments | 46 | ListPartitionReassignments |
| 47 | OffsetDelete | | |

### Record Batch v2 (magic=2)
- Binary layout: baseOffset(8) + batchLength(4) + partitionLeaderEpoch(4) + magic(1) + CRC32C(4) + attributes(2) + lastOffsetDelta(4) + baseTimestamp(8) + maxTimestamp(8) + producerId(8) + producerEpoch(2) + baseSequence(4) + recordCount(4) + records
- Records use varint (zigzag encoded) for lengths and deltas
- Compression via attributes bits 0-2: 0=none, 1=gzip (only gzip supported, JDK-only policy)

### Consumer Group Protocol
- JoinGroup: member joins, leader elected, generation incremented
- SyncGroup: leader distributes partition assignments to all members
- Heartbeat: keep-alive, detect rebalance-in-progress
- LeaveGroup: clean departure, triggers rebalance
- ListGroups/DeleteGroups/OffsetDelete: admin group management
- Assignment strategies: range (default), sticky, cooperative-sticky (KIP-429)

### Transaction Lifecycle
- InitProducerId: allocate producer ID + epoch (fences old producers)
- BeginTransaction: set inTransaction flag
- AddPartitionsToTxn: register partitions in transaction
- AddOffsetsToTxn: register consumer group's __consumer_offsets partition
- TxnOffsetCommit: commit offsets within transaction scope
- Produce: send with transactional ID
- EndTxn(commit/abort): finalize transaction, flush/discard pending offsets

### SASL Authentication
- SaslHandshake: negotiate mechanism (PLAIN, SCRAM-SHA-256)
- SaslAuthenticate: multi-step exchange (1 round for PLAIN, 3 for SCRAM)
- Per-connection auth state tracking in KafkaBroker
- SCRAM-SHA-256 uses PBKDF2WithHmacSHA256 + HMAC-SHA-256 from javax.crypto

### Dynamic Configuration
- ConfigManager: per-topic and broker-level config storage
- DescribeConfigs/AlterConfigs: describe and alter topic configs
- Mutable topic configs: retention.ms, cleanup.policy, max.message.bytes, segment.bytes, min.insync.replicas
- Default topic config applied on createTopic()

### Pluggable Storage
- `LogStorage` interface: `append`, `fetch`, `allBatches`, `replaceBatches`, `truncateBefore`, `isEmpty`, `earliestOffset`, `size`, `close`
- `LogStorageFactory`: functional factory, static `inMemory()` and `mappedFile(Path)` methods
- `InMemoryLogStorage`: default, volatile ArrayList-backed — zero setup
- `MappedFileLogStorage`: segment-based memory-mapped files, sparse offset index, auto-recovery on reopen
  - Segment files: `<logDir>/<topic>-<partition>/segment-<baseOffset>.log`
  - Default segment size: 1 GB (matching real Kafka), initial mapping 16 MB with auto-grow
  - Usage: `new KafkaBroker(host, port, id, partitions, LogStorageFactory.mappedFile(logDir))`
- Thread safety in `PartitionLog` (not in storage implementations)

### Multi-Broker Simulation
- BrokerCluster: manages N KafkaBroker instances in-process
- ReplicaManager: per-broker leader/ISR/epoch state
- Leader election, partition reassignment, controlled shutdown
- In-process simulation — no actual inter-broker network replication

### Log Compaction
- PartitionLog.compact(): key-based deduplication, tombstone removal
- KafkaBroker.compactAll(): triggers compaction on cleanup.policy=compact topics

### Idempotent Production
- Producer ID + epoch + per-partition sequence numbers
- Broker checks: duplicate detection, out-of-order rejection
- Duplicate produce returns success (idempotent guarantee)

## Thread Safety Model

- `KafkaBroker`: virtual thread per connection, `ConcurrentHashMap` for topic/partition state, per-connection `ConnectionState` for SASL auth
- `PartitionLog`: `ReentrantReadWriteLock` for append/fetch isolation, compact() for key deduplication
- `ConsumerGroupCoordinator`: `synchronized` blocks per group for state transitions, pluggable `PartitionAssigner`
- `TransactionManager`: `ConcurrentHashMap` for producer states, `AtomicLong` for ID generation, pending txn offsets
- `ConfigManager`: `ConcurrentHashMap` for topic/broker configs
- `BrokerCluster`: `ConcurrentHashMap` for leader/ISR tracking, synchronized leader election
- `ReplicaManager`: `ConcurrentHashMap` for per-partition replica state
- `KafkaConsumer`: heartbeat on dedicated virtual thread via `ScheduledExecutorService`

## Testing Practices

- Unit tests per package: auth (3 test classes), broker (7 test classes), client (4), codec (1), common (5), record (1), demo (3)
- Codec tests: encode -> decode round-trip for all 37 API types
- Broker tests: topic CRUD, produce/fetch, consumer group lifecycle, config management, replica management, multi-broker cluster
- Auth tests: credential store, PLAIN mechanism, SCRAM-SHA-256 multi-step exchange
- Consumer group tests: join, sync, heartbeat, leave, rebalance, offset commit/fetch, range/sticky/cooperative assignment
- Transaction tests: init producer ID, idempotent dedup, duplicate detection, transaction commit/abort, consumer-in-transaction offsets
- Partition log tests: append, fetch, high watermark, offset-for-timestamp, truncation, compaction
- Admin client tests: topic CRUD, group management, config describe/alter, partition creation, record deletion
- Demo tests: end-to-end scenarios (produce/consume, admin, transactional)
- All tests use ephemeral ports (port=0) and loopback transport (no external broker required)
- Test count: 399

## Dependencies

- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — TCP transport, lifecycle management, virtual threads
- `slf4j-api` — logging facade
- Test: JUnit 5, AssertJ, SLF4J Simple

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
