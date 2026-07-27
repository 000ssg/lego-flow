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

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
