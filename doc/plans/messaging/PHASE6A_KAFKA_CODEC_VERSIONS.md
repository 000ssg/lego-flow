# Phase 6a — Kafka Codec Version Accuracy + Structure (plan)

> **Branch:** `cleanup-messaging` · **Supersedes** the half-done "Phase 6 step 1" WIP of 2026-09-23
> (preserved verbatim in `kafka-wip-2026-09-23.patch`).
> **Non-negotiable invariants** (all later phases depend on them):
> 1. The overall plan goals in `PLAN.md` (compliance, ≥80% coverage, interop groups 1–3, guidelines)
>    are **unchanged**. Phase 6a is a correctness prerequisite for Phase 6 (Kafka interop), not a
>    replacement.
> 2. **Spec-first**: the only source of truth for any wire layout is
>    `doc/spec/message/*.json` (apache/kafka **3.6.1**, complete set: 37 APIs × req/resp = 74 files,
>    fetched 2026-09-23). Live-broker observation is a **check**, never a **source**: a test may fail
>    against the broker → investigate the spec interpretation; never "fix" a layout because the
>    broker accepted it.
> 3. **No version mixing**: exactly **one API version per sub-task**. A sub-task = "implement (or fix)
>    <API> v<N> encode+decode + its unit tests". It touches only that version's code path.
> 4. **Unit tests before interop**: every version sub-task lands with unit tests (encode→decode
>    round-trip on that version + spec-conformance checks on the encoded bytes). The interop test
>    (`KafkaKRaftInteropTest`) runs only after the sub-tasks it exercises are green. Interop is the
>    final verifier, not the development driver.
> 5. **Small files, small commits**: the codec is split by API sub-category into one class per
>    sub-category; each sub-task commits with its unit tests green; no sub-task reopens a finished
>    version.

## 0. Why this phase exists (findings, 2026-09-23)

- `KafkaCodec.java` is a 2,670-line monolith: 37 APIs × req/resp, all hand-written.
- The committed `v0`-only layouts were written from recollection, not the schema:
  e.g. `CreateTopicsRequest v0` omits the spec's `Assignments` array (and the WIP diff additionally
  changed `replicationFactor` int16→int32 and **deleted** `Configs` + `timeoutMs` after observing a
  broker rejection — guessing from behavior, in both directions).
- **WIP diff was red**: 4 unit-test errors (1 `BufferUnderflow` round-trip + 3 admin-client
  "Connection closed") were sitting uncommitted in the tree.
- `ApiKey.java` version ranges are stale for 5 APIs vs the live broker: Fetch 13→15, ListOffsets
  7→8, LeaderAndIsr 5→7, StopReplica 3→4, UpdateMetadata 7→8.
- The broker (cp-kafka 7.6.1 = Kafka 3.6.1, healthy on `localhost:9092`) advertises the full matrix;
  its client-visible ranges match the 3.6.1 schema exactly, with **one documented exception**
  (OffsetCommit: schema v0..v9, broker v0..v8).

## 1. Target codec structure (split by API sub-category)

`KafkaCodec` stays as a **static façade** (so existing ~416 tests compile unchanged) delegating to
per-sub-category classes; each class implements one sub-category's encode/decode, one version per
method set (internal `encode<Api>(v<N>, req)` + dispatch on the negotiated version):

| Class (package `ssg.legoflow.messaging.kafka.codec`) | APIs | Version sub-tasks |
|---|---|---|
| `ProduceCodec`, `FetchCodec`, `ListOffsetsCodec` | 0, 1, 2 | 35 |
| `MetadataCodec`, `LeaderAndIsrCodec`, `StopReplicaCodec`, `UpdateMetadataCodec`, `ControlledShutdownCodec`, `OffsetForLeaderEpochCodec` | 3, 4, 5, 6, 7, 23 | 44 |
| `OffsetCommitCodec`, `OffsetFetchCodec`, `FindCoordinatorCodec`, `JoinGroupCodec`, `HeartbeatCodec`, `LeaveGroupCodec`, `SyncGroupCodec`, `DescribeGroupsCodec`, `ListGroupsCodec`, `OffsetDeleteCodec` | 8, 9, 10, 11, 12, 13, 14, 15, 16, 47 | 63 |
| `InitProducerIdCodec`, `AddPartitionsToTxnCodec`, `AddOffsetsToTxnCodec`, `EndTxnCodec`, `WriteTxnMarkersCodec`, `TxnOffsetCommitCodec` | 22, 24, 25, 26, 27, 28 | 24 |
| `CreateTopicsCodec`, `DeleteTopicsCodec`, `DeleteRecordsCodec`, `CreatePartitionsCodec`, `DeleteGroupsCodec`, `DescribeConfigsCodec`, `AlterConfigsCodec`, `AlterPartitionReassignmentsCodec`, `ListPartitionReassignmentsCodec` | 19, 20, 21, 37, 42, 32, 33, 45, 46 | 35 |
| `SaslHandshakeCodec`, `ApiVersionsCodec`, `SaslAuthenticateCodec` | 17, 18, 36 | 9 |

Shared primitives (`writeString`/`readString`/varint etc.) move to `KafkaCodecPrimitives`.
Mirror-image test classes per sub-category replace the single 1,600-line `KafkaCodecTest`.

**Version dispatch mechanism (chosen):** the client negotiates once via ApiVersions
(`KafkaConnection` caches the per-API `[min..max]` matrix from the response; default 0 until then).
Each façade method is `encodeXxxRequest(short version, XxxRequest)`: the sub-category class holds a
version→handler table (generated once per API, filled as sub-tasks land); an unimplemented version
throws `CodecNotImplementedException` (explicit, never a silent fall-through to another version —
"do not hide issues"). The in-memory broker keeps its own pinned per-API version constants.

## 2. Execution order (sub-categories, smallest first)

1. **Foundation** — commit the spec artifact set + this plan; fix `ApiKey.java` ranges from the live
   matrix (5 APIs); add `KafkaCodecPrimitives` + façade delegation skeleton (delegation-only commit,
   zero behavior change, 416 tests stay green); add the ApiVersions negotiation + version registry
   to `KafkaConnection`.
2. **Negotiation/Auth** (9 sub-tasks) — smallest group; establishes the dispatch pattern.
3. **Record I/O** (35) — the interop-critical path (Produce v3/v9 + Fetch are what Phase 6 exercises).
4. **Admin** (35) — CreateTopics et al. (where the WIP diff broke).
5. **Transactions** (24).
6. **Consumer Groups** (63) — largest; includes the v9/v8 OffsetCommit nuance (see below).
7. **Metadata/Cluster** (44) — broker-internal APIs (4, 5, 6, 7) have **no** live-broker check
   (not in the advertised matrix); their verification is unit tests only.

## 3. Per-sub-task contract (repeat for every row below)

- **Input:** `doc/spec/message/<Api>{Request,Response}.json` field list at the target version
  (the Δ table in §4 is the generated checklist).
- **Mechanism choice:** as part of implementing version vN, pick how vN's code path is written —
  (a) new dedicated methods if the layout diverges, (b) parameterize the previous version's methods
  if only nullability/optional fields changed. Record the choice in the commit message.
- **Output:** encode + decode for vN (request AND response), unit tests = round-trip at vN **and**
  byte-level conformance of the encoded frame against the spec field order, committed green.
- **Never** touch a different version's code in the same commit; if a shared primitive must change,
  that is its own foundation commit with the full suite re-run.

## 4. Version sub-task matrix (one row = one sub-task)

Status: ☐ pending · ▶ in progress · ✓ committed (commit hash) · ⊘ superseded (reason).

## Record I/O

### Produce (API 0) — v0..v9

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): Acks, TimeoutMs, TopicData, Name, PartitionData, Index, Records | ✓ | c7c24c8b |
| v1 | + ThrottleTimeMs:int32[1+] | ✓ | 3e6d9a3c |
| v2 | + LogAppendTimeMs:int64[2+] | ✓ | 6dc997a8 |
| v3 | + TransactionalId:string[3+](null:3+) | ✓ | 6fac46ae |
| v4 | unchanged | ✓ | 108852d8 |
| v5 | + LogStartOffset:int64[5+] | ✓ | 89200df9 |
| v6 | unchanged | ✓ | 73fcc9e1 |
| v7 | unchanged | ✓ | 0c3688dc |
| v8 | + RecordErrors:[]BatchIndexAndErrorMessage[8+], BatchIndex:int32[8+], BatchIndexErrorMessage:string[8+](null:8+), ErrorMessage:string[8+](null:8+) | ☐ | |
| v9 | → flexible encoding | ☐ | |

### Fetch (API 1) — v0..v15

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (9 fields): ReplicaId, MaxWaitMs, MinBytes, Topics, Topic, Partitions, Partition, FetchOffset, PartitionMaxBytes | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + MaxBytes:int32[3+] | ☐ | |
| v4 | + IsolationLevel:int8[4+], LastStableOffset:int64[4+], AbortedTransactions:[]AbortedTransaction[4+](null:4+), ProducerId:int64[4+], FirstOffset:int64[4+] | ☐ | |
| v5 | + LogStartOffset:int64[5+], LogStartOffset:int64[5+] | ☐ | |
| v6 | unchanged | ☐ | |
| v7 | + SessionId:int32[7+], SessionEpoch:int32[7+], ForgottenTopicsData:[]ForgottenTopic[7+], Topic:string[7-12], Partitions:[]int32[7+], ErrorCode:int16[7+], SessionId:int32[7+] | ☐ | |
| v8 | unchanged | ☐ | |
| v9 | + CurrentLeaderEpoch:int32[9+] | ☐ | |
| v10 | unchanged | ☐ | |
| v11 | + RackId:string[11+], PreferredReadReplica:int32[11+] | ☐ | |
| v12 | + ClusterId:string[12+](null:12+), LastFetchedEpoch:int32[12+], DivergingEpoch:EpochEndOffset[12+], Epoch:int32[12+], EndOffset:int64[12+], CurrentLeader:LeaderIdAndEpoch[12+], LeaderId:int32[12+], LeaderEpoch:int32[12+], SnapshotId:SnapshotId[12+], EndOffset:int64[0+], Epoch:int32[0+]<br>→ flexible encoding | ☐ | |
| v13 | + TopicId:uuid[13+], TopicId:uuid[13+], TopicId:uuid[13+]<br>− Topic:string[0-12], Topic:string[7-12], Topic:string[0-12] | ☐ | |
| v14 | unchanged | ☐ | |
| v15 | + ReplicaState:ReplicaState[15+], ReplicaId:int32[15+], ReplicaEpoch:int64[15+]<br>− ReplicaId:int32[0-14] | ☐ | |

### ListOffsets (API 2) — v0..v8

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): ReplicaId, Topics, Name, Partitions, PartitionIndex, Timestamp, MaxNumOffsets | ☐ | |
| v1 | + Timestamp:int64[1+], Offset:int64[1+]<br>− MaxNumOffsets:int32[0], OldStyleOffsets:[]int64[0] | ☐ | |
| v2 | + IsolationLevel:int8[2+], ThrottleTimeMs:int32[2+] | ☐ | |
| v3 | unchanged | ☐ | |
| v4 | + CurrentLeaderEpoch:int32[4+], LeaderEpoch:int32[4+] | ☐ | |
| v5 | unchanged | ☐ | |
| v6 | → flexible encoding | ☐ | |
| v7 | unchanged | ☐ | |
| v8 | unchanged | ☐ | |

## Metadata/Cluster

### Metadata (API 3) — v0..v12

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (2 fields): Topics, Name | ☐ | |
| v1 | + Rack:string[1+](null:1+), ControllerId:int32[1+], IsInternal:bool[1+] | ☐ | |
| v2 | + ClusterId:string[2+](null:2+) | ☐ | |
| v3 | + ThrottleTimeMs:int32[3+] | ☐ | |
| v4 | + AllowAutoTopicCreation:bool[4+] | ☐ | |
| v5 | + OfflineReplicas:[]int32[5+] | ☐ | |
| v6 | unchanged | ☐ | |
| v7 | + LeaderEpoch:int32[7+] | ☐ | |
| v8 | + IncludeClusterAuthorizedOperations:bool[8-10], IncludeTopicAuthorizedOperations:bool[8+], TopicAuthorizedOperations:int32[8+], ClusterAuthorizedOperations:int32[8-10] | ☐ | |
| v9 | → flexible encoding | ☐ | |
| v10 | + TopicId:uuid[10+], TopicId:uuid[10+] | ☐ | |
| v11 | − IncludeClusterAuthorizedOperations:bool[8-10], ClusterAuthorizedOperations:int32[8-10] | ☐ | |
| v12 | unchanged | ☐ | |

### LeaderAndIsr (API 4) — v0..v7

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): ControllerId, ControllerEpoch, UngroupedPartitionStates, LiveLeaders, BrokerId, HostName, Port | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | + BrokerEpoch:int64[2+], TopicStates:[]LeaderAndIsrTopicState[2+], TopicName:string[2+], PartitionStates:[]LeaderAndIsrPartitionState[2+]<br>− UngroupedPartitionStates:[]LeaderAndIsrPartitionState[0-1] | ☐ | |
| v3 | unchanged | ☐ | |
| v4 | → flexible encoding | ☐ | |
| v5 | + Type:int8[5+], TopicId:uuid[5+], Topics:[]LeaderAndIsrTopicError[5+], TopicId:uuid[5+](key), PartitionErrors:[]LeaderAndIsrPartitionError[5+]<br>− PartitionErrors:[]LeaderAndIsrPartitionError[0-4] | ☐ | |
| v6 | unchanged | ☐ | |
| v7 | + isKRaftController:bool[7+] | ☐ | |

### StopReplica (API 5) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (6 fields): ControllerId, ControllerEpoch, DeletePartitions, UngroupedPartitions, TopicName, PartitionIndex | ☐ | |
| v1 | + BrokerEpoch:int64[1+], Topics:[]StopReplicaTopicV1[1-2], Name:string[1-2], PartitionIndexes:[]int32[1-2]<br>− UngroupedPartitions:[]StopReplicaPartitionV0[0], TopicName:string[0], PartitionIndex:int32[0] | ☐ | |
| v2 | → flexible encoding | ☐ | |
| v3 | + TopicStates:[]StopReplicaTopicState[3+], TopicName:string[3+], PartitionStates:[]StopReplicaPartitionState[3+], PartitionIndex:int32[3+], LeaderEpoch:int32[3+], DeletePartition:bool[3+]<br>− DeletePartitions:bool[0-2], Topics:[]StopReplicaTopicV1[1-2], Name:string[1-2], PartitionIndexes:[]int32[1-2] | ☐ | |
| v4 | + isKRaftController:bool[4+] | ☐ | |

### UpdateMetadata (API 6) — v0..v8

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): ControllerId, ControllerEpoch, UngroupedPartitionStates, LiveBrokers, Id, V0Host, V0Port | ☐ | |
| v1 | + Endpoints:[]UpdateMetadataEndpoint[1+], Port:int32[1+], Host:string[1+], SecurityProtocol:int16[1+]<br>− V0Host:string[0], V0Port:int32[0] | ☐ | |
| v2 | + Rack:string[2+](null:0+) | ☐ | |
| v3 | + Listener:string[3+] | ☐ | |
| v4 | unchanged | ☐ | |
| v5 | + BrokerEpoch:int64[5+], TopicStates:[]UpdateMetadataTopicState[5+], TopicName:string[5+], PartitionStates:[]UpdateMetadataPartitionState[5+]<br>− UngroupedPartitionStates:[]UpdateMetadataPartitionState[0-4] | ☐ | |
| v6 | → flexible encoding | ☐ | |
| v7 | + TopicId:uuid[7+] | ☐ | |
| v8 | + isKRaftController:bool[8+], Type:int8[8+] | ☐ | |

### ControlledShutdown (API 7) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): BrokerId | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | + BrokerEpoch:int64[2+] | ☐ | |
| v3 | → flexible encoding | ☐ | |

### OffsetForLeaderEpoch (API 23) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (5 fields): Topics, Topic, Partitions, Partition, LeaderEpoch | ☐ | |
| v1 | + LeaderEpoch:int32[1+] | ☐ | |
| v2 | + CurrentLeaderEpoch:int32[2+], ThrottleTimeMs:int32[2+] | ☐ | |
| v3 | + ReplicaId:int32[3+] | ☐ | |
| v4 | → flexible encoding | ☐ | |

## Consumer Groups

### OffsetCommit (API 8) — v0..v9

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): GroupId, Topics, Name, Partitions, PartitionIndex, CommittedOffset, CommittedMetadata | ☐ | |
| v1 | + GenerationIdOrMemberEpoch:int32[1+], MemberId:string[1+], CommitTimestamp:int64[1] | ☐ | |
| v2 | + RetentionTimeMs:int64[2-4]<br>− CommitTimestamp:int64[1] | ☐ | |
| v3 | + ThrottleTimeMs:int32[3+] | ☐ | |
| v4 | unchanged | ☐ | |
| v5 | − RetentionTimeMs:int64[2-4] | ☐ | |
| v6 | + CommittedLeaderEpoch:int32[6+] | ☐ | |
| v7 | + GroupInstanceId:string[7+](null:7+) | ☐ | |
| v8 | → flexible encoding | ☐ | |
| v9 | unchanged | ☐ | |

### OffsetFetch (API 9) — v0..v8

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields): GroupId, Topics, Name, PartitionIndexes | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | + ErrorCode:int16[2-7] | ☐ | |
| v3 | + ThrottleTimeMs:int32[3+] | ☐ | |
| v4 | unchanged | ☐ | |
| v5 | + CommittedLeaderEpoch:int32[5-7] | ☐ | |
| v6 | → flexible encoding | ☐ | |
| v7 | + RequireStable:bool[7+] | ☐ | |
| v8 | + Groups:[]OffsetFetchRequestGroup[8+], groupId:string[8+], Topics:[]OffsetFetchRequestTopics[8+](null:8+), Name:string[8+], PartitionIndexes:[]int32[8+], Groups:[]OffsetFetchResponseGroup[8+], groupId:string[8+], Topics:[]OffsetFetchResponseTopics[8+], Name:string[8+], Partitions:[]OffsetFetchResponsePartitions[8+], PartitionIndex:int32[8+], CommittedOffset:int64[8+], CommittedLeaderEpoch:int32[8+], Metadata:string[8+](null:8+), ErrorCode:int16[8+], ErrorCode:int16[8+]<br>− GroupId:string[0-7], Topics:[]OffsetFetchRequestTopic[0-7](null:2-7), Name:string[0-7], PartitionIndexes:[]int32[0-7], Topics:[]OffsetFetchResponseTopic[0-7], Name:string[0-7], Partitions:[]OffsetFetchResponsePartition[0-7], PartitionIndex:int32[0-7], CommittedOffset:int64[0-7], CommittedLeaderEpoch:int32[5-7], Metadata:string[0-7](null:0-7), ErrorCode:int16[0-7], ErrorCode:int16[2-7] | ☐ | |

### FindCoordinator (API 10) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): Key | ☐ | |
| v1 | + KeyType:int8[1+], ThrottleTimeMs:int32[1+], ErrorMessage:string[1-3](null:1-3) | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | → flexible encoding | ☐ | |
| v4 | + CoordinatorKeys:[]string[4+], Coordinators:[]Coordinator[4+], Key:string[4+], NodeId:int32[4+], Host:string[4+], Port:int32[4+], ErrorCode:int16[4+], ErrorMessage:string[4+](null:4+)<br>− Key:string[0-3], ErrorCode:int16[0-3], ErrorMessage:string[1-3](null:1-3), NodeId:int32[0-3], Host:string[0-3], Port:int32[0-3] | ☐ | |

### JoinGroup (API 11) — v0..v9

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): GroupId, SessionTimeoutMs, MemberId, ProtocolType, Protocols, Name, Metadata | ☐ | |
| v1 | + RebalanceTimeoutMs:int32[1+] | ☐ | |
| v2 | + ThrottleTimeMs:int32[2+] | ☐ | |
| v3 | unchanged | ☐ | |
| v4 | unchanged | ☐ | |
| v5 | + GroupInstanceId:string[5+](null:5+), GroupInstanceId:string[5+](null:5+) | ☐ | |
| v6 | → flexible encoding | ☐ | |
| v7 | + ProtocolType:string[7+](null:7+) | ☐ | |
| v8 | + Reason:string[8+](null:8+) | ☐ | |
| v9 | + SkipAssignment:bool[9+] | ☐ | |

### Heartbeat (API 12) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (3 fields): GroupId, GenerationId, MemberId | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + GroupInstanceId:string[3+](null:3+) | ☐ | |
| v4 | → flexible encoding | ☐ | |

### LeaveGroup (API 13) — v0..v5

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (2 fields): GroupId, MemberId | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + Members:[]MemberIdentity[3+], MemberId:string[3+], GroupInstanceId:string[3+](null:3+), Members:[]MemberResponse[3+], MemberId:string[3+], GroupInstanceId:string[3+](null:3+), ErrorCode:int16[3+]<br>− MemberId:string[0-2] | ☐ | |
| v4 | → flexible encoding | ☐ | |
| v5 | + Reason:string[5+](null:5+) | ☐ | |

### SyncGroup (API 14) — v0..v5

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (6 fields): GroupId, GenerationId, MemberId, Assignments, MemberId, Assignment | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + GroupInstanceId:string[3+](null:3+) | ☐ | |
| v4 | → flexible encoding | ☐ | |
| v5 | + ProtocolType:string[5+](null:5+), ProtocolName:string[5+](null:5+), ProtocolType:string[5+](null:5+), ProtocolName:string[5+](null:5+) | ☐ | |

### DescribeGroups (API 15) — v0..v5

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): Groups | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + IncludeAuthorizedOperations:bool[3+], AuthorizedOperations:int32[3+] | ☐ | |
| v4 | + GroupInstanceId:string[4+](null:4+) | ☐ | |
| v5 | → flexible encoding | ☐ | |

### ListGroups (API 16) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (0 fields):  | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | → flexible encoding | ☐ | |
| v4 | + StatesFilter:[]string[4+], GroupState:string[4+] | ☐ | |

### OffsetDelete (API 47) — v0..v0

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (5 fields): GroupId, Topics, Name, Partitions, PartitionIndex | ☐ | |

## Transactions

### InitProducerId (API 22) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (2 fields): TransactionalId, TransactionTimeoutMs | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | → flexible encoding | ☐ | |
| v3 | + ProducerId:int64[3+], ProducerEpoch:int16[3+] | ☐ | |
| v4 | unchanged | ☐ | |

### AddPartitionsToTxn (API 24) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields): V3AndBelowTransactionalId, V3AndBelowProducerId, V3AndBelowProducerEpoch, V3AndBelowTopics | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | → flexible encoding | ☐ | |
| v4 | + Transactions:[]AddPartitionsToTxnTransaction[4+], TransactionalId:string[4+](key), ProducerId:int64[4+], ProducerEpoch:int16[4+], VerifyOnly:bool[4+], Topics:[]AddPartitionsToTxnTopic[4+], ErrorCode:int16[4+], ResultsByTransaction:[]AddPartitionsToTxnResult[4+], TransactionalId:string[4+](key), TopicResults:[]AddPartitionsToTxnTopicResult[4+]<br>− V3AndBelowTransactionalId:string[0-3], V3AndBelowProducerId:int64[0-3], V3AndBelowProducerEpoch:int16[0-3], V3AndBelowTopics:[]AddPartitionsToTxnTopic[0-3], ResultsByTopicV3AndBelow:[]AddPartitionsToTxnTopicResult[0-3] | ☐ | |

### AddOffsetsToTxn (API 25) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields): TransactionalId, ProducerId, ProducerEpoch, GroupId | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | → flexible encoding | ☐ | |

### EndTxn (API 26) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields): TransactionalId, ProducerId, ProducerEpoch, Committed | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | → flexible encoding | ☐ | |

### WriteTxnMarkers (API 27) — v0..v1

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (8 fields): Markers, ProducerId, ProducerEpoch, TransactionResult, Topics, Name, PartitionIndexes, CoordinatorEpoch | ☐ | |
| v1 | → flexible encoding | ☐ | |

### TxnOffsetCommit (API 28) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (10 fields): TransactionalId, GroupId, ProducerId, ProducerEpoch, Topics, Name, Partitions, PartitionIndex, CommittedOffset, CommittedMetadata | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | + CommittedLeaderEpoch:int32[2+] | ☐ | |
| v3 | + GenerationId:int32[3+], MemberId:string[3+], GroupInstanceId:string[3+](null:3+)<br>→ flexible encoding | ☐ | |

## Admin

### CreateTopics (API 19) — v0..v7

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (11 fields): Topics, Name, NumPartitions, ReplicationFactor, Assignments, PartitionIndex, BrokerIds, Configs, Name, Value, timeoutMs | ☐ | |
| v1 | + validateOnly:bool[1+], ErrorMessage:string[1+](null:0+) | ☐ | |
| v2 | + ThrottleTimeMs:int32[2+] | ☐ | |
| v3 | unchanged | ☐ | |
| v4 | unchanged | ☐ | |
| v5 | + TopicConfigErrorCode:int16[5+], NumPartitions:int32[5+], ReplicationFactor:int16[5+], Configs:[]CreatableTopicConfigs[5+](null:5+), Name:string[5+], Value:string[5+](null:5+), ReadOnly:bool[5+], ConfigSource:int8[5+], IsSensitive:bool[5+]<br>→ flexible encoding | ☐ | |
| v6 | unchanged | ☐ | |
| v7 | + TopicId:uuid[7+] | ☐ | |

### DeleteTopics (API 20) — v0..v6

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (2 fields): TopicNames, TimeoutMs | ☐ | |
| v1 | + ThrottleTimeMs:int32[1+] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | unchanged | ☐ | |
| v4 | → flexible encoding | ☐ | |
| v5 | + ErrorMessage:string[5+](null:5+) | ☐ | |
| v6 | + Topics:[]DeleteTopicState[6+], Name:string[6+](null:6+), TopicId:uuid[6+], TopicId:uuid[6+]<br>− TopicNames:[]string[0-5] | ☐ | |

### DeleteRecords (API 21) — v0..v2

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (6 fields): Topics, Name, Partitions, PartitionIndex, Offset, TimeoutMs | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | → flexible encoding | ☐ | |

### CreatePartitions (API 37) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): Topics, Name, Count, Assignments, BrokerIds, TimeoutMs, ValidateOnly | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | → flexible encoding | ☐ | |
| v3 | unchanged | ☐ | |

### DeleteGroups (API 42) — v0..v2

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): GroupsNames | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | → flexible encoding | ☐ | |

### DescribeConfigs (API 32) — v0..v4

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields): Resources, ResourceType, ResourceName, ConfigurationKeys | ☐ | |
| v1 | + IncludeSynonyms:bool[1+], ConfigSource:int8[1+], Synonyms:[]DescribeConfigsSynonym[1+], Name:string[1+], Value:string[1+](null:0+), Source:int8[1+]<br>− IsDefault:bool[0] | ☐ | |
| v2 | unchanged | ☐ | |
| v3 | + IncludeDocumentation:bool[3+], ConfigType:int8[3+], Documentation:string[3+](null:0+) | ☐ | |
| v4 | → flexible encoding | ☐ | |

### AlterConfigs (API 33) — v0..v2

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (7 fields): Resources, ResourceType, ResourceName, Configs, Name, Value, ValidateOnly | ☐ | |
| v1 | unchanged | ☐ | |
| v2 | → flexible encoding | ☐ | |

### AlterPartitionReassignments (API 45) — v0..v0

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (6 fields) (flexible): TimeoutMs, Topics, Name, Partitions, PartitionIndex, Replicas | ☐ | |

### ListPartitionReassignments (API 46) — v0..v0

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (4 fields) (flexible): TimeoutMs, Topics, Name, PartitionIndexes | ☐ | |

## Negotiation/Auth

### SaslHandshake (API 17) — v0..v1

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): Mechanism | ✓ | v0 commit (see PROGRESS.md) |
| v1 | unchanged | ✓ | v1 commit (see PROGRESS.md) |

### ApiVersions (API 18) — v0..v3

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (0 fields):  | ✓ | v0 commit (see PROGRESS.md) |
| v1 | + ThrottleTimeMs:int32[1+] | ✓ | v1 commit (see PROGRESS.md) |
| v2 | unchanged | ✓ | v2 commit (see PROGRESS.md) |
| v3 | + ClientSoftwareName:string[3+], ClientSoftwareVersion:string[3+], SupportedFeatures:[]SupportedFeatureKey[3+], Name:string[3+](key), MinVersion:int16[3+], MaxVersion:int16[3+], FinalizedFeaturesEpoch:int64[3+], FinalizedFeatures:[]FinalizedFeatureKey[3+], Name:string[3+](key), MaxVersionLevel:int16[3+], MinVersionLevel:int16[3+], ZkMigrationReady:bool[3+]<br>→ flexible encoding | ✓ | v3 commit (see PROGRESS.md) |

### SaslAuthenticate (API 36) — v0..v2

| Version | Δ vs previous (name:type, per schema) | Status | Commit |
|---------|----------------------------------------|--------|--------|
| v0 | base (1 fields): AuthBytes | ✓ | v0 commit (see PROGRESS.md) |
| v1 | + SessionLifetimeMs:int64[1+] | ✓ | v1 commit (see PROGRESS.md) |
| v2 | → flexible encoding | ✓ | v2 commit (see PROGRESS.md) |

## 5. Interop (Phase 6 proper) — unchanged scope, gated

`KafkaKRaftInteropTest` (untracked, WIP skeleton kept) runs only after Record I/O + Admin +
Negotiation sub-tasks it uses are ✓. Its assertions must match spec, not observed broker behavior.
WAMP group 3 and the Phase 6 "multi-partition streaming → transactions" steps proceed per `PLAN.md`
unchanged.

## 6. Commit cadence

- Every sub-task (or small batch of rows in the **same** sub-category, each row independently green)
  commits with `feat(kafka-codec): <Api> v<N> per 3.6.1 spec` + `Co-Authored-By: AI assistant`.
- No commit lands with the module suite red; the 416-test suite is the standing gate plus the new
  per-version tests.
- `PROGRESS.md` Log gets one line per commit; the matrix above is updated in the same commit.