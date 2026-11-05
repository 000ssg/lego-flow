# CreateTopics Codec — Spec Validation & Codec Generation Notes

Authoritative source: `doc/spec/message/CreateTopicsRequest.json` + `CreateTopicsResponse.json`
(both byte-identical to Kafka 3.6.1 spec; extracted programmatically from the spec JSONs).
These results are LOCKED and reusable — do NOT re-derive them.
Frozen binding wire order: `doc/spec/order/CreateTopics.Request.txt` /
`doc/spec/order/CreateTopics.Response.txt` (generated via `gen_kafka.py freeze`).

## Pin
- **v0** — the version `KafkaAdminClient` sends (no `validateOnly`). The in-house model
  carries the v0 request field-set (topics + timeout); v1+ adds `validateOnly` (response-side
  `ErrorMessage` v1+, `ThrottleTimeMs` v2+ are read+discarded / not modeled).
  Client/broker/admin pinned at v0; the codec implements the full v0–v7 range for
  frame produce/parse.

## REQUEST — CreateTopicsRequest (API key 19), validVersions 0-7, flexibleVersions 5+
Top-level (ORDERED):
- `Topics`        []CreatableTopic  v=0+
- `timeoutMs`     int32             v=0+  default 60000
- `validateOnly`  bool              v=1+  default false

Topics[] (ORDERED): `Name` string 0+ (mapKey), `NumPartitions` int32 0+,
`ReplicationFactor` int16 0+, `Assignments` [] 0+, `Configs` [] 0+

Topics[].Assignments[] (ORDERED): `PartitionIndex` int32 0+ (mapKey),
`BrokerIds` []int32 0+

Topics[].Configs[] (ORDERED): `Name` string 0+ (mapKey), `Value` string 0+ (nullable 0+)

## RESPONSE — CreateTopicsResponse (API key 19), validVersions 0-7, flexibleVersions 5+
Top-level (ORDERED):
- `ThrottleTimeMs`  int32   v=2+   ignorable
- `Topics`          []CreatableTopicResult  v=0+

Topics[] (ORDERED): `Name` string 0+ (mapKey), `TopicId` uuid 7+, `ErrorCode` int16 0+,
`ErrorMessage` string 1+ (nullable), `NumPartitions` int32 5+ (default -1),
`ReplicationFactor` int16 5+ (default -1), `Configs` [] 5+ (nullable),
`TopicConfigErrorCode` int16 5+ (tag 0)

Topics[].Configs[] (ORDERED): `Name` string 5+, `Value` string 5+ (nullable),
`ReadOnly` bool 5+, `ConfigSource` int8 5+ (default -1), `IsSensitive` bool 5+

## Spec-JSON nullability verdicts (checked against raw Apache JSON, 3.6.1)
- Request `Configs.Value`: nullable 0+ (fixed: int16 len -1; flexible: varint 0).
- Response `ErrorMessage`: nullable (fixed: int16 len -1; flexible: varint 0).
- Response v5+ `NumPartitions` / `ReplicationFactor`: **NOT nullable** — default -1, so in
  flexible they stay FIXED int32/int16 (no null sentinel, no varint). This settles WIP-ledger
  guesses #3–#5 (they were predicated on nullable ints — they are not).
- Response v5+ `Configs` array: nullable — flexible null = varint 0, else varint(size+1).
- Response v7 `TopicId`: **NOT nullable** uuid (16 bytes); the model does not expose it →
  encode writes the all-zero uuid, decode reads+discards.
- `ThrottleTimeMs` / `TopicConfigErrorCode`: ignorable, not modeled → decode reads+discards.

## v0 WIRE (the pinned path)
- REQUEST:  int32 numTopics, [ string Name, int32 NumPartitions, int16 ReplicationFactor,
            int32 numAssignments, [ int32 PartitionIndex, int32 numBrokerIds, int32... ],
            int32 numConfigs, [ string Name, nullable-string Value ] ], int32 timeoutMs
- RESPONSE: int32 numTopics, [ string Name, int16 ErrorCode ]

## BUG FIX (the reason for the codec)
Old inline `KafkaCodec` CreateTopics methods (pre-`Cleanup`): request encode OMITTED the
mandatory `Assignments` array (v0+), and response encode wrote only Name+ErrorCode (no
`ErrorMessage` v1+ / `ThrottleTimeMs` v2+ handling). Model `TopicCreate` lacked the
`assignments` field entirely. New dedicated codec writes the spec layout; model gained
`Assignment` nested record + `assignments` with a compat constructor (empty list =
automatic assignment) so callers keep compiling.

## Codec design (mirrors ListOffsetsCodec / FetchCodec)
- `PINNED_VERSION = 0`
- `encodeRequest(req)` / `encodeRequest(short, req)` / `decodeRequest(short, ByteBuffer)`
  / `encodeResponse(resp)` / `encodeResponse(short, resp)` / `decodeResponse(short, ByteBuffer)`
- full v0–v7 dispatch; flexible path for v5+ (varint array counts `size+1`, compact
  non-nullable strings for `Name` mapKeys, nullable compact strings for `Value`/
  `ErrorMessage`, nullable compact array for v5+ response `Configs`, all-zero uuid for v7
  `TopicId`, empty tagged-fields trailer varint(0), discard-only parse for `ThrottleTimeMs` /
  `TopicConfigErrorCode`)
- Model does NOT expose validateOnly (request) nor response ErrorMessage/ThrottleTimeMs/
  NumPartitions/ReplicationFactor/Configs/TopicId/TopicConfigErrorCode → encode writes spec
  defaults / null, decode reads+discards. Constructor arities preserved (compat constructor
  for the new `assignments` parameter).
- Facade `KafkaCodec` CreateTopics methods delegate to `CreateTopicsCodec` at
  `PINNED_VERSION`; inline monolith bodies deleted.

## Test coverage (`CreateTopicsCodecTest`, 25 tests, all green)
- Pinned (5): v0 request/response round-trip, pinned encode pinning, `PINNED_VERSION = 0`.
- RequestLayout (3): byte-for-byte v0 walk (every field incl. assignments + nullable config
  values), v1 `validateOnly` byte, v4 = v0 + validateOnly.
- FixedResponse (3): v0 byte layout, v1 `ErrorMessage` (absent = int16 -1) + v2
  `ThrottleTimeMs`, v4 round-trip.
- FlexibleRequest (7): v5 byte-for-byte walk (compact strings, varint(size+1) counts,
  per-struct endTags=0), v5/v6/v7 round-trips, empty-assignments/empty-configs varint(1)
  cases, v5 counts verified against the Apache 3.6.1 generated code.
- FlexibleResponse (5): v5 layout walk (compact name + ErrorMessage varint(0) + fixed
  int32/int16 -1 + Configs varint(0) + endTags), v5/v6/v7 round-trips, v7 16-byte all-zero
  TopicId between Name and ErrorCode.
- Compat (2): 4-arg `TopicCreate` constructor defaults assignments to empty;
  out-of-range versions throw `CodecNotImplementedException` (both directions).
