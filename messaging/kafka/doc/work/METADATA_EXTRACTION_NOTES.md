# Metadata API (key 3) — dedicated `MetadataCodec` extraction notes

Status: **GATHERING COMPLETE → VERIFIED FROM DISK 2026-10-20 → IMPLEMENTATION IN PROGRESS**.
Every fact below was re-verified directly against the vendored spec JSONs, the frozen order
tables, and the source on disk. An earlier draft of this file (same path, pre-2026-10-20)
contained **wrong facts** — it claimed a v1 baseline with fields `TopicNames`/`TopicUuids`
and flexible-from-v10, none of which exist in the vendored 3.6.1 schema or in
`ApiKey.METADATA(3, "Metadata", 0, 12, 9)`. That draft is superseded by this file; the frozen
order tables (written from the spec) are the binding source.

## 1. Spec facts (vendor: apache/kafka 3.6.1, `doc/spec/message/`)

`MetadataRequest.json`: apiKey 3, request, `validVersions 0-12`, `flexibleVersions 9+`.
- `Topics []MetadataRequestTopic`, versions `0+`, **nullableVersions `1+`** (v0: non-nullable;
  v0 "empty array = all topics", v1+ "null array = all topics, empty = no topics")
- per topic: `TopicId uuid` versions `10+` (ignorable); `Name string` versions `0+`
  (entityType topicName, **nullableVersions `10+`** — spec note: "Versions 10 and 11 should not
  use the topicId field or set topic name to null")
- `AllowAutoTopicCreation bool`, versions `4+`, **default `true`**, ignorable false
- `IncludeClusterAuthorizedOperations bool`, versions `8-10`
- `IncludeTopicAuthorizedOperations bool`, versions `8+`

`MetadataResponse.json`: apiKey 3, response, `validVersions 0-12`, `flexibleVersions 9+`.
- `ThrottleTimeMs int32`, versions `3+` (ignorable)
- `Brokers []MetadataResponseBroker`: `NodeId int32`, `Host string`, `Port int32`,
  `Rack string nullable 1+` (default null, ignorable)
- `ClusterId string nullable 2+` (default null, ignorable)
- `ControllerId int32 1+` (default -1, ignorable)
- `Topics []MetadataResponseTopic`: `ErrorCode int16`, `Name string 0+` (mapKey;
  **nullableVersions `12+`** only), `TopicId uuid 10+` (ignorable), `IsInternal bool 1+`
  (default false, ignorable), `Partitions []MetadataResponsePartition { ErrorCode int16,
  PartitionIndex int32, LeaderId int32, **LeaderEpoch int32 7+** (default -1, ignorable),
  ReplicaNodes []int32, IsrNodes []int32, **OfflineReplicas []int32 5+** (ignorable) }`,
  `TopicAuthorizedOperations int32 8+` (default Integer.MIN_VALUE)
- `ClusterAuthorizedOperations int32 8-10` (default Integer.MIN_VALUE)

**Flex layout (v9–v12):** KIP-482 — compact strings, varint array counts as **N+1**, and a
trailing tagged-fields sentinel (varint 0) after every struct: per-topic (request), per-partition,
per-topic, per-broker, top-level (response). No tagged fields are declared by us in any version.

## 2. Frozen binding order tables (already on disk, verified consistent with spec)

- `doc/spec/order/Metadata.Request.txt` — request wire order per version:
  - v0: `Topics { Name }`
  - v1–v3: same as v0 (layout-identical run)
  - v4–v7: + `AllowAutoTopicCreation`
  - v8: + `IncludeClusterAuthorizedOperations` + `IncludeTopicAuthorizedOperations`
  - v9: flex, same field set as v8
  - v10: flex, + per-topic `TopicId` (before Name)
  - v11–v12: flex, − `IncludeClusterAuthorizedOperations` (KIP-700 deprecation)
- `doc/spec/order/Metadata.Response.txt` — response wire order per version:
  - v0: `Brokers { NodeId, Host, Port }, Topics { ErrorCode, Name, Partitions { ErrorCode,
    PartitionIndex, LeaderId, ReplicaNodes, IsrNodes } }`
  - v1: + `Rack` (broker), + `ControllerId`, + `IsInternal` (topic)
  - v2: + `ClusterId`
  - v3–v4: + leading `ThrottleTimeMs`
  - v5–v6: + per-partition `OfflineReplicas` (after IsrNodes)
  - v7: + per-partition `LeaderEpoch` (after LeaderId)
  - v8: + per-topic `TopicAuthorizedOperations` (after Partitions) + trailing
    `ClusterAuthorizedOperations`
  - v9: flex, same field set as v8
  - v10: flex, + per-topic `TopicId` (after Name)
  - v11–v12: flex, − `ClusterAuthorizedOperations`

## 3. In-house models (UNCHANGED — verified on disk)

- `MetadataRequest(List<String> topics, boolean allowAutoTopicCreation)` — null topics = all;
  convenience ctors `()` and `(topics)` set allowAutoTopicCreation=false.
- `MetadataResponse(List<BrokerMetadata> brokers, List<TopicMetadata> topics)` with
  `BrokerMetadata(int nodeId, String host, int port)`, `TopicMetadata(short errorCode, String name,
  List<PartitionMetadata>)`, `PartitionMetadata(short errorCode, int partitionIndex, int leaderId,
  List<Integer> replicaIds, List<Integer> isrIds)`.

Field handling per the "models unchanged" rule (as in JoinGroup/OffsetDelete/EndTxn):
- **Request** — written: `AllowAutoTopicCreation` at v4+ (model value; v0–v3 absent, decode
  default false). Written with **spec defaults + read/discarded** at decode:
  `IncludeClusterAuthorizedOperations` (false, v8–v10), `IncludeTopicAuthorizedOperations` (false,
  v8+), per-topic `TopicId` (16 zero bytes, v10+).
- **Response** — written with **spec defaults + read/discarded**: `ThrottleTimeMs` (0, v3+),
  per-broker `Rack` (null, v1+), `ClusterId` (null, v2+), `ControllerId` (-1, v1+), per-topic
  `IsInternal` (false, v1+), per-partition `OfflineReplicas` (empty, v5+), per-partition
  `LeaderEpoch` (-1, v7+), per-topic `TopicId` (16 zero bytes, v10+), per-topic
  `TopicAuthorizedOperations` (Integer.MIN_VALUE, v8+), trailing `ClusterAuthorizedOperations`
  (Integer.MIN_VALUE, v8–v10).

## 4. Baseline/interop warning (latent, v0 only)

The old inline facade request codec wrote topic count **-1** to mean "all topics" (v1+ encoding)
inside a **v0** frame; the spec v0 wire for "all topics" is an **empty array** (count 0) — v0 has
no nullable Topics. Real brokers rely on v0's empty-array semantics. The new codec writes count 0
for null topics at v0 and count -1 only at v1+. The in-house broker reads null topics as "all" at
any version, so its behavior is preserved; the v0 wire is now spec-correct. (v1+ wire is
unchanged: -1 still means all.)

## 5. Version constants (per `ApiKey.METADATA(3, "Metadata", 0, 12, 9)`)

`MIN_VERSION = 0`, `MAX_VERSION = 12`, `FLEXIBLE_START = 9`. In-house client + broker pinned at
v0 end-to-end (same as every other migrated API — `KafkaApiVersions` pin stays v0; no
negotiation changes in this sub-task).

## 6. Implementation checklist (this sub-task)

1. `MetadataCodec` — `encodeRequest(short, MetadataRequest)`, `decodeRequest(short, ByteBuffer)`,
   `encodeResponse(short, MetadataResponse)`, `decodeResponse(short, ByteBuffer)`; version guard
   via `CodecNotImplementedException`; exact-size buffers for the request (like JoinGroupCodec),
   over-estimated buffer + flip for the response (like the old inline response codec).
2. Facade `KafkaCodec` — replace the four inline bodies (lines 193–291) with thin versioned
   delegates `encodeMetadataRequest(short, req)` / `decodeMetadataRequest(short, buf)` /
   `encodeMetadataResponse(short, resp)` / `decodeMetadataResponse(short, buf)`. **No no-version
   overloads** (project convention since JoinGroup: facade methods take an explicit version).
3. `KafkaBroker` — dispatch `case METADATA -> handleMetadata(body, version)`; handler threads the
   version into decode + response encode (response headers carry no version — body is encoded at
   the request's clamped version, per design decision 4).
4. Clients — three call sites (KafkaAdminClient.metadata, KafkaProducer.refreshMetadata,
   KafkaConsumer.assignPartitions): versioned facade calls with `(short) 0` pin, matching their
   header `(short) 0`.
5. `KafkaCodecTest` — four existing facade round-trips (lines ~82–105, ~1039–1044) updated to the
   versioned signatures.
6. `MetadataCodecTest` — round-trips all versions both directions, hand-built byte-exact goldens
   (verified against a Python reference encoder built from the spec JSON), per-version field
   diffs, defaults + null edges, truncation + version guards, facade delegation + version
   constants.
7. `KafkaCodecNegotiatedVersionTest` — `metadataRoundTrip()` at v0/v6/v12 (like joinGroupRoundTrip).
8. Docs backfill (second commit): PHASE6A matrix rows v0–v12 ✓ + DONE block, PROGRESS bullet +
   log, kafka REQUIREMENTS entry, WIP ledger (next sub-task → LeaderAndIsr, audit updated).
9. Build gates: `./gradlew :lego-flow-kafka:test` green; `mvn compile -DskipTests -pl '!benchmarks'`
   for the Maven side (module compiles under both builds).

## 7. Verified call-site inventory (all must move to the versioned facade)

- `KafkaAdminClient.metadata` — encode+decode (lines 70–72), header pin `(short) 0`
- `KafkaProducer.refreshMetadata` — encode+decode (lines 348–350), header pin `(short) 0`
- `KafkaConsumer.assignPartitions` — encode+decode (lines 416–418), header pin `(short) 0`
- `KafkaBroker.handleMetadata` — decode+encode (lines 450–473), dispatch line 397
- `KafkaCodecTest` — 4 round-trips (82–105, 1039–1044)
- No interop-test references (grep: none).

## 8. Open design decisions (recorded, decided)

1. **v0 "all topics"**: encode null topics as count 0 (spec-correct v0) instead of the old count
   -1 (a v1+ construct). See §4.
2. **Request AllowAutoTopicCreation**: model field → written as the model value at v4+; v0–v3
   decodes return false (field absent).
3. **Response Name at v12+**: spec makes it nullable at v12+; the model keeps `String name`
   non-null — encode with the non-nullable compact writer, decode with the nullable reader (a
   -1/0-encoded null would surface as null; accepted, model-unchanged).
4. **No tagged fields** written at any version (varint 0 sentinels only) — same as every other
   migrated API.

---

## Implementation log (2026-10-20)

1. **`MetadataCodec.java`** created — v0–v12, request + response, both directions.
   - Request: layout-identical runs v0–v3, v4–v7, v8, v9–v12; null-topics v0 → empty
     array (count 0), v1+ → int32(-1)/varint(0); v10+ TopicId (16 zero bytes); v11+ drops
     IncludeClusterAuthorizedOperations.
   - Response: v0 base; v1 Rack+ControllerId+IsInternal; v2 ClusterId; v3 leading
     ThrottleTimeMs; v5 OfflineReplicas; v7 LeaderEpoch; v8 TopicAuthorizedOperations +
     trailing ClusterAuthorizedOperations; v9+ KIP-482 flexible (compact strings, N+1
     varint counts, five endTags trailers per-struct/top-level); v10 TopicId.
   - Non-model fields written with spec defaults, read + discarded (models unchanged).
   - v0 request count-0 decodes to `null` topics (all-topics marker, per spec comment).
2. **`KafkaCodec` facade** — 4 inline bodies replaced with versioned delegates.
3. **`KafkaBroker`** — dispatch `handleMetadata(body, version)`; decode + encode at version.
4. **Clients** (Admin/Producer/Consumer) — pinned `MetadataCodec.MIN_VERSION`; imports added.
5. **`MetadataCodecTest`** — 17 tests: round-trips both directions every version; byte-for-byte
   goldens for every version (26 constants, machine-spliced from the Python reference
   `doc/work/metadata_golden.py` — hand-transcribed goldens had 3 byte errors, all caught);
   null-topics goldens per version run; per-version size deltas; truncation + version guards;
   facade constants.
6. **`KafkaCodecTest`** — 4 Metadata tests updated to versioned facade (v0); all-topics
   assertion: v0 null→count 0→decodes to null marker.
7. **`KafkaCodecNegotiatedVersionTest`** — new `metadataRoundTrip()` (v0/v5/v12 req + resp).
8. **Python reference** `metadata_golden.py` — two bugs found + fixed during verification:
   (a) `allow` default was True, model default is False; (b) per-broker endTags(0) missing
   at v9+. Regenerated goldens; RSP_V9/V10/V11 verified against frozen tables byte-by-byte.

**Test status (this session):** MetadataCodecTest 17/17 green; full module run in progress
(first full run: 1224 tests, 1 failure = the stale facade assertion, fixed).
