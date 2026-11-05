# ListOffsets Codec — Spec Validation & Codec Generation Notes

Authoritative source: `doc/spec/message/ListOffsetsRequest.json` + `ListOffsetsResponse.json`
(both byte-identical to Kafka 3.6.1 spec; extracted programmatically from the spec JSONs).
These results are LOCKED and reusable — do NOT re-derive them.

## Pin
- **v1** — the earliest version that returns per-partition `Timestamp` + `Offset` (v0 returns
  `OldStyleOffsets[]`). The in-house model exposes exactly the v1 field-set (no
  `MaxNumOffsets` / `OldStyleOffsets` / `IsolationLevel` / `CurrentLeaderEpoch` / `LeaderEpoch`),
  so v1 is the one version the model fully carries. Client/broker/admin pinned at v1.

## REQUEST — ListOffsetsRequest (API key 2), validVersions 0-8, flexibleVersions 6+
Top-level (ORDERED):
- `ReplicaId`     int32   v=0+   (default -1 = normal consumer)
- `IsolationLevel` int8   v=2+
- `Topics`        []      v=0+

Topics[] (ORDERED): `Name` string 0+, `Partitions` [] 0+

Topics[].Partitions[] (ORDERED):
- `PartitionIndex`     int32   v=0+
- `CurrentLeaderEpoch` int32   v=4+  default -1, ignorable   <-- BEFORE Timestamp
- `Timestamp`          int64   v=0+
- `MaxNumOffsets`      int32   v=0 (v0 ONLY)  default 1

## RESPONSE — ListOffsetsResponse (API key 2), validVersions 0-8, flexibleVersions 6+
Top-level (ORDERED):
- `ThrottleTimeMs`  int32   v=2+
- `Topics`          []      v=0+

Topics[] (ORDERED): `Name` string 0+, `Partitions` [] 0+

Topics[].Partitions[] (ORDERED):
- `PartitionIndex`   int32   v=0+
- `ErrorCode`        int16   v=0+
- `OldStyleOffsets`  []int64 v=0 (v0 ONLY)
- `Timestamp`        int64   v=1+  default -1
- `Offset`           int64   v=1+  default -1
- `LeaderEpoch`      int32   v=4+  default -1

## v1 WIRE (the pinned path)
- REQUEST:  int32 ReplicaId, int32 numTopics, [ string Name, int32 numPartitions,
            [ int32 PartitionIndex, int64 Timestamp ] ]
- RESPONSE: int32 numTopics, [ string Name, int32 numPartitions,
            [ int32 PartitionIndex, int16 ErrorCode, int64 Timestamp, int64 Offset ] ]

## BUG FIX (the reason for the codec)
Old inline `KafkaCodec.encodeListOffsetsRequest` (lines 399-412) started encoding at
`numTopics`, OMITTING the mandatory leading `int32 ReplicaId`. New codec writes it first
(default -1). Response encode in the old facade already matched v1.

## Codec design (mirrors FetchCodec)
- `PINNED_VERSION = 1`
- `encodeRequest(req)` / `decodeRequest(short, byte[])` / `encodeResponse(resp)` /
  `decodeResponse(short, byte[])`  (+ pinned no-version overloads)
- full v0-v8 dispatch; flexible path for v6+ (compact non-nullable strings, varint counts,
  tagged-field trailers via skipTaggedFields / writeVarint(0))
- Model has NO replicaId/isolationLevel/currentLeaderEpoch/leaderEpoch fields -> encode writes
  the spec default, decode reads+discards. Constructor arities preserved.
- The facade `KafkaCodec.decodeListOffsetsRequest(ByteBuffer)` takes a positioned ByteBuffer;
  `encodeListOffsetsRequest` returns byte[]. `KafkaCodec.encodeResponse(ResponseHeader, byte[])`
  wraps the body.

## Flexible size-calc notes (over-allocation is SAFE; flip()+toBytes drains only remaining())
- compact non-nullable string size = varintSize(len+1) + len
- varint counts: use KafkaCodecPrimitives.varintSize(n) in the estimate (never hardcode 1)
- end-of-tagged-fields trailer = varint(0) = 1 byte when empty
