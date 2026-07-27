# Kafka Module — Architecture

This document describes the architectural decisions for the Kafka module.

---

## Protocol Overview

Apache Kafka uses a custom binary protocol over TCP. Every message is length-prefixed (4-byte int32), followed by a request/response header and a type-specific body. The protocol uses correlation IDs to match requests with responses. This module implements 37 Kafka API types, covering produce/fetch, consumer groups (range/sticky/cooperative), topic management, idempotent production, transactions, SASL authentication, multi-broker simulation, dynamic configuration, and log compaction.

## Layered Architecture

```mermaid
graph TD
    L0["BrokerCluster<br/>(multi-broker simulation, leader election,<br/>reassignment, controlled shutdown)"]
    L1["Admin Client / Producer / Consumer<br/>(connection management, API surface, configuration)"]
    L2["Consumer Group Coordinator<br/>(join/sync/heartbeat/leave, rebalance,<br/>range/sticky/cooperative assignment, offset storage)"]
    L3["Transaction Manager<br/>(producer ID allocation, epoch fencing,<br/>idempotent dedup, txn lifecycle, consumer-in-txn offsets)"]
    L3a["Config Manager<br/>(broker + topic configs, describe/alter)"]
    L3b["SASL Auth<br/>(PLAIN, SCRAM-SHA-256,<br/>credential store, per-connection state)"]
    L4["Partition Log + LogStorage<br/>(pluggable storage: in-memory or mmap file,<br/>offset tracking, compaction, truncation)"]
    L4a["Replica Manager<br/>(leader/ISR/epoch state per partition)"]
    L5["Record Batch Codec<br/>(v2 format magic=2, CRC32C, varint,<br/>GZIP compression, record headers)"]
    L6["Wire Protocol Codec<br/>(37 API types, frame encoding,<br/>request/response header, string/array primitives)"]
    L7["service module (TCP)<br/>(ServerSocketChannel, virtual threads)"]
    L8["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L0 --> L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7 --> L8
    L1 --> L3a
    L1 --> L3b
    L0 --> L4a
```

## Supported API Types (37)

| API Key | Name | Category | Purpose |
|---------|------|----------|---------|
| 0 | Produce | Core | Publish records to topic partitions |
| 1 | Fetch | Core | Fetch records from topic partitions |
| 2 | ListOffsets | Core | Get offsets for timestamps or earliest/latest |
| 3 | Metadata | Core | Get topic/partition/broker metadata |
| 4 | LeaderAndIsr | Multi-broker | Assign partition leadership and ISR |
| 5 | StopReplica | Multi-broker | Stop replicating partitions |
| 6 | UpdateMetadata | Multi-broker | Update cluster metadata cache |
| 7 | ControlledShutdown | Multi-broker | Graceful shutdown with leadership migration |
| 8 | OffsetCommit | Consumer group | Commit consumer group offsets |
| 9 | OffsetFetch | Consumer group | Fetch committed consumer group offsets |
| 10 | FindCoordinator | Consumer group | Find coordinator for group or transaction |
| 11 | JoinGroup | Consumer group | Join a consumer group |
| 12 | Heartbeat | Consumer group | Keep consumer group membership alive |
| 13 | LeaveGroup | Consumer group | Leave a consumer group |
| 14 | SyncGroup | Consumer group | Synchronize partition assignments |
| 15 | DescribeGroups | Consumer group | Describe consumer group state/members |
| 16 | ListGroups | Admin | List all consumer groups |
| 17 | SaslHandshake | Auth | Negotiate SASL mechanism |
| 18 | ApiVersions | Core | Negotiate supported API versions |
| 19 | CreateTopics | Admin | Create new topics |
| 20 | DeleteTopics | Admin | Delete existing topics |
| 21 | DeleteRecords | Admin | Truncate partition log before offset |
| 22 | InitProducerId | Transaction | Allocate producer ID for idempotent/txn |
| 23 | OffsetForLeaderEpoch | Multi-broker | Find offset for leader epoch boundary |
| 24 | AddPartitionsToTxn | Transaction | Register partitions in transaction |
| 25 | AddOffsetsToTxn | Transaction | Register consumer group in transaction |
| 26 | EndTxn | Transaction | Commit or abort transaction |
| 27 | WriteTxnMarkers | Transaction | Write commit/abort markers to logs |
| 28 | TxnOffsetCommit | Transaction | Commit offsets within transaction |
| 32 | DescribeConfigs | Config | Describe broker/topic configurations |
| 33 | AlterConfigs | Config | Alter topic configurations |
| 36 | SaslAuthenticate | Auth | SASL authentication exchange |
| 37 | CreatePartitions | Admin | Expand topic partition count |
| 42 | DeleteGroups | Admin | Delete consumer groups |
| 45 | AlterPartitionReassignments | Multi-broker | Reassign partition replicas |
| 46 | ListPartitionReassignments | Multi-broker | List ongoing reassignments |
| 47 | OffsetDelete | Admin | Delete committed offsets |

## Consumer Group Protocol

### Group State Machine

```mermaid
stateDiagram-v2
    [*] --> EMPTY
    EMPTY --> COMPLETING_REBALANCE: JoinGroup (first member)
    COMPLETING_REBALANCE --> STABLE: SyncGroup
    STABLE --> PREPARING_REBALANCE: Member join/leave/timeout
    PREPARING_REBALANCE --> COMPLETING_REBALANCE: All members re-join
    STABLE --> EMPTY: All members leave
    PREPARING_REBALANCE --> EMPTY: All members leave
    EMPTY --> DEAD: DeleteGroups
    EMPTY --> [*]: Group removed
```

### Partition Assignment Strategies

Three pluggable strategies via `PartitionAssigner` interface:

| Strategy | Name | Behavior |
|----------|------|----------|
| **RangeAssigner** | `range` | Sort partitions by topic+partition, distribute evenly round-robin (default) |
| **StickyAssigner** | `sticky` / `cooperative-sticky` | Retain valid existing assignments, assign unassigned to least-loaded member |

### Cooperative Rebalance (KIP-429)

When protocol name is `cooperative-sticky`:
- Consumer includes current assignment in JoinGroup metadata
- After SyncGroup, computes diff between old and new assignments
- Only revokes actually-moved partitions (not all partitions)
- Calls `onPartitionsAssigned` only for newly-added partitions
- `RebalanceListener.onPartitionsLost()` for cooperative partition loss semantics

### Rebalance Sequence

```mermaid
sequenceDiagram
    participant C1 as Consumer 1
    participant C2 as Consumer 2
    participant B as Broker (Coordinator)

    C1->>B: JoinGroup (memberId="", protocol="range")
    B->>C1: JoinGroup Response (leader=C1, memberId=assigned)
    C2->>B: JoinGroup (memberId="")
    B->>C2: JoinGroup Response (leader=C1, memberId=assigned)

    Note over C1: Leader uses PartitionAssigner
    C1->>B: SyncGroup (with assignments for all members)
    C2->>B: SyncGroup (empty assignments)
    B->>C1: SyncGroup Response (C1's partitions)
    B->>C2: SyncGroup Response (C2's partitions)

    loop Every sessionTimeout/3
        C1->>B: Heartbeat
        B->>C1: Heartbeat Response
    end
```

## SASL Authentication

```mermaid
sequenceDiagram
    participant C as Client
    participant B as Broker

    C->>B: SaslHandshake(mechanism="PLAIN")
    B->>C: SaslHandshake Response(mechanisms=["PLAIN","SCRAM-SHA-256"])

    alt PLAIN
        C->>B: SaslAuthenticate(\0user\0password)
        B->>C: SaslAuthenticate Response(success)
    else SCRAM-SHA-256
        C->>B: SaslAuthenticate(client-first: n,,n=user,r=nonce)
        B->>C: SaslAuthenticate(server-first: r=nonce,s=salt,i=iter)
        C->>B: SaslAuthenticate(client-final: c=biws,r=nonce,p=proof)
        B->>C: SaslAuthenticate(server-final: v=signature)
    end
```

- `CredentialStore` holds PLAIN passwords and SCRAM credentials (salt, storedKey, serverKey, iterations)
- SCRAM uses `javax.crypto.SecretKeyFactory` with `PBKDF2WithHmacSHA256` and `javax.crypto.Mac` for HMAC-SHA-256
- Per-connection `ConnectionState` tracks auth progress in `KafkaBroker.handleConnection()`

## Multi-Broker Simulation

```mermaid
graph TD
    BC["BrokerCluster<br/>(manages N brokers)"]
    BC --> B0["KafkaBroker 0<br/>+ ReplicaManager"]
    BC --> B1["KafkaBroker 1<br/>+ ReplicaManager"]
    BC --> B2["KafkaBroker 2<br/>+ ReplicaManager"]

    BC --> LE["Leader Election<br/>(per partition)"]
    BC --> RA["Reassignment<br/>(replica movement)"]
    BC --> CS["Controlled Shutdown<br/>(leadership migration)"]
```

- `BrokerCluster` — creates and manages N `KafkaBroker` instances in-process
- `ReplicaManager` — per-broker: tracks leader/ISR/epoch/offset per partition
- Leader election via `BrokerCluster.electLeader()`; partition reassignment via `AlterPartitionReassignments`
- `ControlledShutdown` migrates leadership away from a shutting-down broker
- In-process simulation — no actual inter-broker network replication

## Record Batch v2 Format

```mermaid
graph LR
    subgraph "Record Batch (magic=2)"
        BO["baseOffset<br/>int64"] --> BL["batchLength<br/>int32"]
        BL --> PLE["partitionLeader<br/>Epoch int32"]
        PLE --> M["magic<br/>int8 (=2)"]
        M --> CRC["CRC32C<br/>uint32"]
        CRC --> A["attributes<br/>int16"]
        A --> LOD["lastOffset<br/>Delta int32"]
        LOD --> BT["baseTimestamp<br/>int64"]
        BT --> MT["maxTimestamp<br/>int64"]
        MT --> PID["producerId<br/>int64"]
        PID --> PE["producer<br/>Epoch int16"]
        PE --> BS["baseSequence<br/>int32"]
        BS --> RC["recordCount<br/>int32"]
        RC --> R["records<br/>(varint encoded)"]
    end
```

### Record Format (within batch)
Each record uses varint encoding:
- `length` (varint) — total record size
- `attributes` (int8) — unused in v2
- `timestampDelta` (varlong) — delta from batch baseTimestamp
- `offsetDelta` (varint) — delta from batch baseOffset
- `key` (varint length + bytes)
- `value` (varint length + bytes)
- `headers` (varint count, then key-value pairs with varint lengths)

## Broker Architecture

```mermaid
graph TD
    TCP["TCP Listener<br/>(ServerSocketChannel)"] --> VT["Virtual Thread<br/>per Connection"]
    VT --> AUTH["SASL Auth<br/>(optional handshake)"]
    AUTH --> FR["Frame Reader<br/>(4-byte length prefix)"]
    FR --> HD["Header Decode<br/>(apiKey, apiVersion, correlationId, clientId)"]
    HD --> RD["Request Dispatcher<br/>(switch on 37 ApiKeys)"]

    RD --> P["Produce Handler"]
    RD --> F["Fetch Handler"]
    RD --> CG["Group Protocol<br/>(Join/Sync/HB/Leave/<br/>List/Delete/OffsetDelete)"]
    RD --> TM["Topic Management<br/>(Create/Delete/CreatePart/<br/>DeleteRecords/Metadata)"]
    RD --> TX["Transaction Handler<br/>(Init/AddPart/AddOffsets/<br/>TxnOffset/WriteMarkers/EndTxn)"]
    RD --> OC["Offset Handler<br/>(Commit/Fetch)"]
    RD --> SA["SASL Handler<br/>(Handshake/Authenticate)"]
    RD --> CF["Config Handler<br/>(Describe/Alter)"]
    RD --> MB["Multi-Broker Handler<br/>(LeaderAndIsr/StopReplica/<br/>UpdateMetadata/ControlledShutdown/<br/>OffsetForLeaderEpoch/<br/>AlterReassign/ListReassign)"]

    P --> PL["PartitionLog<br/>(append)"]
    P --> IDM["TransactionManager<br/>(idempotent check)"]
    F --> PL
    CG --> CGC["ConsumerGroup<br/>Coordinator"]
    OC --> CGC
    TX --> TXM["TransactionManager"]
    TXM --> CGC
    CF --> CM["ConfigManager"]
    MB --> RM["ReplicaManager"]
```

### Partition Log and Pluggable Storage

```mermaid
graph TD
    PL["PartitionLog<br/>(thread safety, offset tracking,<br/>compaction logic)"]
    LS["LogStorage<br/>(interface)"]
    IM["InMemoryLogStorage<br/>(ArrayList, volatile)"]
    MF["MappedFileLogStorage<br/>(mmap files, durable)"]
    LSF["LogStorageFactory<br/>(functional interface)"]

    PL --> LS
    LS --> IM
    LS --> MF
    LSF --> LS
```

- `PartitionLog` delegates all batch storage to `LogStorage` interface
- `ReentrantReadWriteLock` for concurrent read (fetch) / exclusive write (append) — thread safety is in `PartitionLog`, not storage
- Offset assignment: sequential, batch-aware (baseOffset + recordCount)
- `truncateBefore(offset)` — removes batches before given offset (DeleteRecords)
- `compact()` — key-based deduplication: keeps latest record per key, tombstones remove key, null-key records always retained
- Broker-level `compactAll()` triggers compaction on topics with `cleanup.policy=compact`

**InMemoryLogStorage** (default):
- `ArrayList<StoredBatch>` per partition — volatile, zero configuration
- Best for testing, development, and ephemeral workloads

**MappedFileLogStorage** (durable):
- Segment files: `segment-<baseOffset>.log` in `<logDir>/<topic>-<partition>/`
- Memory-mapped via `FileChannel.map()` — zero-copy reads through OS page cache
- Initial segment mapping: 16 MB, auto-grows up to configurable max (default 1 GB)
- Sparse offset index (one entry per 4 KB) for binary-search seek on fetch
- Recovery on construction: scans existing segment files, rebuilds index
- Usage: `new KafkaBroker(host, port, id, partitions, LogStorageFactory.mappedFile(logDir))`

### Dynamic Configuration
- `ConfigManager` stores per-topic and broker-level configs
- Topic configs (mutable): `retention.ms`, `cleanup.policy`, `max.message.bytes`, `segment.bytes`, `min.insync.replicas`
- Broker configs (read-only): `num.partitions`, `log.retention.ms`, `message.max.bytes`, `default.replication.factor`
- Default topic config applied automatically on `createTopic()`

## Transaction Architecture

```mermaid
sequenceDiagram
    participant P as Producer
    participant TM as TransactionManager
    participant CGC as GroupCoordinator
    participant B as Broker

    P->>B: InitProducerId(txnId)
    B->>TM: allocate/fence producer
    TM->>B: producerId + epoch
    B->>P: InitProducerIdResponse

    P->>P: beginTransaction()
    P->>B: AddPartitionsToTxn(partitions)
    B->>TM: register partitions, state=ONGOING
    P->>B: Produce (with txnId)
    B->>TM: idempotent check
    B->>B: append to PartitionLog

    opt Consumer-in-Transaction
        P->>B: AddOffsetsToTxn(groupId)
        B->>TM: register __consumer_offsets partition
        P->>B: TxnOffsetCommit(groupId, offsets)
        B->>TM: store pending offsets
    end

    P->>B: EndTxn(commit=true)
    B->>TM: flush pending offsets to coordinator
    TM->>CGC: commitOffsets(groupId, offsets)
    B->>TM: state -> COMPLETE_COMMIT -> EMPTY
    B->>P: EndTxnResponse
```

### Idempotency Tracking
- Per producer ID: map of TopicPartition → last sequence number
- On produce: check baseSequence == lastSequence + 1 (accept), == duplicate range (return success), else reject
- Epoch fencing: reject produce with stale epoch

## Wire Protocol Frame Format

```mermaid
graph LR
    subgraph "Request Frame"
        RL["length<br/>int32"] --> AK["apiKey<br/>int16"]
        AK --> AV["apiVersion<br/>int16"]
        AV --> CI["correlationId<br/>int32"]
        CI --> CID["clientId<br/>int16+bytes"]
        CID --> BODY["request body<br/>(API-specific)"]
    end
```

```mermaid
graph LR
    subgraph "Response Frame"
        RL2["length<br/>int32"] --> CI2["correlationId<br/>int32"]
        CI2 --> BODY2["response body<br/>(API-specific)"]
    end
```

## Integration with Lego Flow

| Lego Flow Module | Usage in Kafka |
|------------------|----------------|
| `blocks` | DP<I,O> for data processing pipeline building blocks, DF<T> for filtering, Statistics for metrics |
| `service` | TCP server/client channels, virtual thread pools, lifecycle management |

The Kafka module follows the framework's conventions: virtual threads for concurrency, ConcurrentHashMap for thread-safe state, AutoCloseable for resource management, and record types for immutable data carriers.

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
