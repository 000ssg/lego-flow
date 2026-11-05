# ListOffsets Spec Validation (grounded, final — DO NOT RE-EVALUATE)

Date: 2026-10-01. Source of truth: **Apache Kafka 3.6.1** metadata JSONs,
fetched verbatim from `apache/kafka` tag `3.6.1` at
`clients/src/main/resources/common/message/ListOffsets{Request,Response}.json`.

## Verdict
`doc/spec/message/ListOffsetsRequest.json` and `doc/spec/message/ListOffsetsResponse.json`
are **byte-identical** to the real Apache Kafka 3.6.1 files (`diff` clean, zero
differences). The spec JSONs are CORRECT. All ListOffsets faults were in the Java
**models** and the old inline `KafkaCodec` facade — NOT in the spec files.

## Canonical wire format (LOCKED)
- validVersions `0-8`, flexibleVersions `6+` for BOTH request and response.
- `flex(v)` = v >= 6 → compact-string (varint-UTF8) + signed varint arrays;
  `!flex` → 16-bit signed length string + 32-bit signed array length.

### REQUEST (apiKey 2)
Top-level: `ReplicaId int32 (0+)`, `IsolationLevel int8 (2+)`, `Topics [] (0+)`.
Topic: `Name string (0+)`, `Partitions [] (0+)`.
Partition: `PartitionIndex int32 (0+)`,
`CurrentLeaderEpoch int32 (4+, default -1, ignorable)`,
`Timestamp int64 (0+)`, `MaxNumOffsets int32 (v0-only, default 1)`.

### RESPONSE (apiKey 2)
Top-level: `ThrottleTimeMs int32 (2+, ignorable)`, `Topics [] (0+)`.
Topic: `Name string (0+)`, `Partitions [] (0+)`.
Partition: `PartitionIndex int32 (0+)`, `ErrorCode int16 (0+)`,
`OldStyleOffsets []int64 (v0-only)`, `Timestamp int64 (1+, default -1)`,
`Offset int64 (1+, default -1)`, `LeaderEpoch int32 (4+, default -1)`.

## Hallucinated fields REMOVED from Java models (not in spec):
- Request model had: `maxWaitMs`, `rackId`, `replicaEpoch`, `currentTimestamp`,
  top-level `maxNumOffsets`, top-level `leaderEpoch`, `topicId`. ALL GONE.
  (Request top-level is ONLY ReplicaId + IsolationLevel + Topics.)
- Response model had: `currentTimestamp`, `rackId`, `messages`, top-level
  `maxNumOffsets`, `replicaId`, `topicId`. ALL GONE.
  (Response top-level is ONLY ThrottleTimeMs + Topics.)

## Real fields the models were MISSING (now ADDED, per spec):
- Request partition: `CurrentLeaderEpoch (4+)`, `MaxNumOffsets (v0)`.
- Response partition: `OldStyleOffsets (v0)`, `LeaderEpoch (4+)`.

## Decoded-default semantics (ignorable / default)
- `CurrentLeaderEpoch` req: v<4 → -1 (field absent; ignorable so skip not read).
- `MaxNumOffsets` req: v>0 → 1 (v0-only field absent).
- `OldStyleOffsets` resp: v>0 → empty (v0-only field absent).
- `Timestamp`/`Offset` resp: v0 → -1 (both 1+ only).
- `LeaderEpoch` resp: v<4 → -1.
- `IsolationLevel` req: v<2 → 0.
- `ThrottleTimeMs` resp: v<2 → 0.

## Old inline KafkaCodec facade (lines ~399-460) was WRONG:
- Request: used fixed 16-bit string, omitted `ReplicaId`, omitted `IsolationLevel`,
  no `CurrentLeaderEpoch`, no `MaxNumOffsets` → v3+ shaped body under a v0 frame.
- Response: omitted `ThrottleTimeMs`, partition order written
  (partitionIndex, errorCode, timestamp, offset) which matched v1+ ordering but with
  NO version handling, no `OldStyleOffsets`, no `LeaderEpoch`, no compact strings.

## Facade delegation pattern (mirror Fetch):
KafkaCodec delegates to `ListOffsetsCodec.{encodeRequest,decodeRequest,encodeResponse,
decodeResponse}(ListOffsetsCodec.PINNED_VERSION, ...)` at **v0**.
- v0 request: ReplicaId + Topics[Name + Partitions[PartitionIndex + CurrentLeaderEpoch?no +
  Timestamp + MaxNumOffsets]] (no IsolationLevel at v0).
- v0 response: Topics[Name + Partitions[PartitionIndex + ErrorCode + OldStyleOffsets]]
  (no ThrottleTimeMs at v0).
