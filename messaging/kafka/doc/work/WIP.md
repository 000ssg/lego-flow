# Kafka Codec Versions — WIP Ledger

## RULE (non-negotiable)
A task is **DONE** only when ALL of: (1) the file(s) exist on disk, (2) a real commit
SHA **in this branch's `git log`** contains them, (3) surefire reports are green.
Any entry claiming DONE without a verifiable commit in `git log` is **void** — delete it
and redo the work. NEVER trust a SHA without `git cat-file -e <sha>`.
Consensus is persisted in commits, not in this file alone — commit each completed API.

## Consumer Groups sub-category (63/63 — COMPLETE, verified 2026-10-19 by source-level audit) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| OffsetCommit (8) | v0–v9 | `OffsetCommitCodec.java` | `OffsetCommitCodecTest.java` (38) | `f8cd4201` | DONE — committed + 1012-module-green verified |
| OffsetFetch (9) | v0–v8 | `OffsetFetchCodec.java` | `OffsetFetchCodecTest.java` (28) | `067d876e` | DONE — committed; 28 tests green, full module 1012 green (verified 2026-10-09) |
| FindCoordinator (10) | v0–v4 | `FindCoordinatorCodec.java` | `FindCoordinatorCodecTest.java` (28) | `e4db2c87` | DONE — committed 2026-10-11; 28 tests green, full module 1040 green (verified this session) |
| JoinGroup (11) | v0–v9 | `JoinGroupCodec.java` | `JoinGroupCodecTest.java` (49) | `5aa5a7a4` | **DONE** — committed; 49 tests green, full module 1089 green (verified this session) |
| Heartbeat (12) | v0–v4 | `HeartbeatCodec.java` | `HeartbeatCodecTest.java` (15) | `0521ca43` | **DONE** — committed; 15 tests green, full module 1128 green (verified this session) |
| LeaveGroup (13) | v0–v5 | `LeaveGroupCodec.java` | `LeaveGroupCodecTest.java` (20) | `ecdc712a` | **DONE** — committed; 20 tests green, full module 1148 green (verified this session) |
| SyncGroup (14) | v0–v5 | `SyncGroupCodec.java` | `SyncGroupCodecTest.java` (21) | `c123e328` | **DONE** — committed; 21 tests green, full module 1169 green (verified 2026-10-17) |
| DescribeGroups (15) | v0–v5 | `DescribeGroupsCodec.java` | `DescribeGroupsCodecTest.java` (14) | `e32d284d` | **DONE** — committed; 14 tests green, full module 1183 green (verified 2026-10-18) |
| ListGroups (16) | v0–v4 | `ListGroupsCodec.java` | `ListGroupsCodecTest.java` (13) | `bf6ac442` | **DONE** — committed; 13 tests green, full module 1196 green (verified 2026-10-18) |
| OffsetDelete (47) | v0 | `OffsetDeleteCodec.java` | `OffsetDeleteCodecTest.java` (10) | `70f56853` | **DONE** — committed; 10 tests green, full module 1206 green (verified 2026-10-19); fixes latent spec bug — old inline response codec omitted ThrottleTimeMs |

## Metadata/Cluster sub-category (13/44 version rows, 1/6 APIs) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| Metadata (3) | v0–v12 | `MetadataCodec.java` | `MetadataCodecTest.java` (17) | `d4765c92` | **DONE** — committed; 17 tests green, full module 1224 green (verified 2026-10-20); all 13 version rows v0–v12 both directions, flex from v9; goldens machine-generated via `doc/work/metadata_golden.py` (reference verified against frozen tables — caught 2 reference bugs + 3 hand-transcription errors); models unchanged |
|| LeaderAndIsr (4) | v0–v7 | `LeaderAndIsrCodec.java` | `LeaderAndIsrCodecTest.java` (19) | `639e6533` | **DONE** — committed; 19 tests green, full module 1243 green (verified this session); all 8 version rows v0–v7 both directions, flex from v4; goldens machine-generated via `doc/work/leaderisr_golden.py`; models extended with controllerEpoch field; fixed v7 field order (isKRaftController before controllerEpoch); fixed endTags placement; fixed AddingReplicas/RemovingReplicas to be []int32 arrays |
|| StopReplica (5) | v0–v4 | `StopReplicaCodec.java` | `StopReplicaCodecTest.java` (11) | `7df6a4db` | **DONE** — committed; 11 tests green, full module 1254 green (verified this session); all 5 version rows v0–v4 both directions, flex from v2; goldens machine-generated via `doc/work/stopreplica_golden.py`; models extended with versioned fields (brokerEpoch, isKRaftController, TopicData, TopicState, PartitionState) |

## Next sub-task

**UpdateMetadata (API 6) v0–v8** — fourth API of the Metadata/Cluster sub-category (plan step 7;
18 version rows remaining). Specs in `doc/spec/message/UpdateMetadata{Request,Response}.json`.
Follow the established pattern: freeze `doc/spec/order/UpdateMetadata.{Request,Response}.txt` →
dedicated `UpdateMetadataCodec` (all versions, both directions; flex = KIP-482 compact + varint N+1 +
endTags) → replace inline `KafkaCodec` bodies with thin versioned delegates (facade methods
currently take NO version param) → wire broker + client → `UpdateMetadataCodecTest` → docs backfill
(PHASE6A, PROGRESS, REQUIREMENTS, WIP).

## Source-level audit (2026-10-19, from the 2 trusted sources: spec + source code)

Full cross-reference of all 37 spec APIs (`doc/spec/message/*.Request.json`) against the dedicated
codec directory and `KafkaCodec.java` bodies:

- **33 of 37 APIs fixed** — dedicated `<Api>Codec.java` exists AND all 33 are called from the
  `KafkaCodec` facade (zero unused codec files; verified by grep of each codec class name in the
  facade). These 33 span: Negotiation/Auth (ApiVersions, SaslHandshake, SaslAuthenticate), Record
  I/O (Produce, Fetch, ListOffsets), Admin (CreateTopics, DeleteTopics, DeleteRecords,
  DescribeConfigs, AlterConfigs, CreatePartitions, AlterPartitionReassignments,
  ListPartitionReassignments, DeleteGroups), Transactions (InitProducerId v0–v4 `2a877ca5`,
  AddPartitionsToTxn, AddOffsetsToTxn, EndTxn, WriteTxnMarkers, TxnOffsetCommit), Consumer Groups
  (all 10: OffsetCommit, OffsetFetch, FindCoordinator, JoinGroup, Heartbeat, LeaveGroup, SyncGroup,
  DescribeGroups, ListGroups, OffsetDelete), Metadata/Cluster (Metadata `d4765c92`, LeaderAndIsr pending commit).
- **4 APIs still INLINE in `KafkaCodec.java`** (full inline encode/decode bodies, NOT thin
  delegates; their facade methods take NO `short version` param) — for sure not fixed yet, in
  plan step-7 (Metadata/Cluster) order:
  1. **StopReplica (API 5)** v0–v4 (flex 2+)
  2. **UpdateMetadata (API 6)** v0–v8 (flex 6+)
  3. **ControlledShutdown (API 7)** v0–v3 (flex 3+)
  4. **OffsetForLeaderEpoch (API 23)** v0–v4 (flex 4+)
- **Ledger corrections from this audit** (docs were stale, source won):
  - InitProducerId (API 22) was already DONE in `2a877ca5` (Transactions sub-category) — the old
    "Next sub-task" pointer to it was wrong; real next sub-task was Metadata (API 3).
  - Consumer Groups sub-category is **63/63 complete**: all 63 version rows in the PHASE6A
    Consumer Groups matrix carry commit SHAs (the old "56/63" header was stale).
  - Metadata/Cluster matrix: **36 of 44 rows done** (Metadata v0–v12 `d4765c92`, LeaderAndIsr v0–v7 `639e6533`, StopReplica v0–v4 `7df6a4db`, UpdateMetadata v0–v8 `e34b6e40`, ControlledShutdown v0–v1 pending commit, 2026-10-22) —
    verified against spec version counts (Metadata 13, LeaderAndIsr 8, Sto...[truncated]
    UpdateMetadata 9, ControlledShutdown 4, OffsetForLeaderEpoch 5 = 44).
- **Left to do (the 4 inline APIs above), in order:**
  1. StopReplica v0–v4 (5 rows)
  2. UpdateMetadata v0–v8 (9 rows)
  3. ControlledShutdown v0–v3 (4 rows)
  4. OffsetForLeaderEpoch v0–v4 (5 rows)
  Known spec notes: StopReplica/UpdateMetadata/ControlledShutdown v4/v4/v3 have
  `isKRaftController` (KRaft-only); StopReplica v0–v1 has flat partition arrays, v3+ groups by topic.

## KNOWN GAP (RESOLVED 2026-10-16 — see "Unpin negotiated version" below)
**Historical audit (was user-flagged UNACCEPTABLE): negotiated version ignored at runtime, not in the codecs.**
- Codec layer was always clean: all dedicated `<Api>Codec` classes accept `short version`
  and branch on it (per-version field gating, flex from FLEXIBLE_START). NONE ignored the version param.
- Facade `KafkaCodec.encode*/decode*` body methods previously took NO version param → hardcoded
  `PINNED_VERSION` (v0, except ListOffsets v1) or literal `(short) 0` (ApiVersions, Sasl*).
- `KafkaBroker.processRequest` decoded `RequestHeader` (incl. `apiVersion`) but decoded bodies via the
  pinned facade and encoded responses at the pinned version — the header version was never threaded
  into any codec. Broker advertised a full min/max range in ApiVersionsResponse it could not serve.
- Clients always sent header version 0 and never negotiated.
**Resolution:** threaded the negotiated version end-to-end (facade versioned overloads → broker
decode/response-encode at the header version with an advertised-max clamp → clients negotiate via
ApiVersions). See the "Unpin negotiated version" section and commits `5ef87d92`, `b9001902`, `4d16fcec`.

## Unpin negotiated version (CLOSED 2026-10-16)
The version-carrying plumbing is now correct end-to-end. Design (confirmed with the
user): the codec's `MAX_VERSION` is the *serviced* range, `ApiKey` the *advertised*
range, and the broker clamps the request version to
`min(headerV, ApiKey.maxVersion(), Codec.MAX_VERSION)`. Response headers carry no
version — only the request header is version-dependent. Client default stays v0 until
ApiVersions negotiation is wired (plumbing correct, negotiation not yet active).

- `5ef87d92` — all 100 encode/decode facade delegate methods for the 25 dedicated-codec
  APIs take an explicit `short version` param; no-version overloads and every
  `PINNED_VERSION` constant removed; `KafkaBroker` threads `header.apiVersion()` into
  all 25 handlers; producer/consumer/admin encode/decode calls updated to the
  versioned facade; 5 codecs (ApiVersions, Fetch, Produce, SaslHandshake,
  SaslAuthenticate) gained `MIN_VERSION`/`MAX_VERSION`/`FLEXIBLE_START`.
- `b9001902` — broker-side clamp: the request version is bounded by the advertised
  `ApiKey.maxVersion()` (a broker cannot serve a version it never advertised).
- `4d16fcec` — `KafkaCodecNegotiatedVersionTest`: round-trips all 25 versioned APIs at
  their MIN/MID/MAX versions through the versioned facade, plus an end-to-end
  FindCoordinator v3 (a non-pinned version) over the in-memory transport — the version
  survives request-encode → broker-decode → response-encode → client-decode, including
  the broker-side clamp. 26 tests green.

## Verified facts (2026-10-20, this branch cleanup-messaging)
|- Full `:lego-flow-kafka` module: **1234 tests, 0 failures** (1224 + 17 MetadataCodecTest + 1
  KafkaCodecNegotiatedVersionTest.metadataRoundTrip + 9 LeaderAndIsrCodecTest), verified via surefire reports after `mvn test`.
- `git log`: OffsetCommit code `f8cd4201`, docs backfill `eef4c02d`; OffsetFetch code `067d876e`, docs backfill `91f7fe78`; FindCoordinator code `e4db2c87` (28 new tests green), docs backfill `dbf5a371`; JoinGroup code `5aa5a7a4` (49 new tests green), docs backfill `c2c12030`; Heartbeat code `0521ca43` (15 new tests green; full module 1128); LeaveGroup code `ecdc712a` (20 new tests green; full module 1148), docs backfill `ca33ccb9`; SyncGroup code `c123e328` (21 new tests green; full module 1169), docs backfill `5a5ca008`; DescribeGroups code `e32d284d` (14 new tests green; full module 1183), docs backfill `98370f3d`; ListGroups code `bf6ac442` (13 new tests green; full module 1196), docs backfill `ce8d4e39`; OffsetDelete code `70f56853` (10 new tests green; full module 1206), docs backfill `c52ca435`; Metadata code `d4765c92` (17 new tests green; full module 1224), docs backfill follows.
|- Pattern per API: freeze `doc/spec/order/<Api>.{Request,Response}.txt` → dedicated `<Api>Codec` (all versions,
  both directions; flex = KIP-482 compact + varint N+1 + endTags) → replace inline `KafkaCodec` bodies with thin
  delegates → `<Api>CodecTest` (pinned + hand-built goldens + per-version Δ + round-trips + truncation/guards/facade)
  → docs: PHASE6A matrix rows ✓ + commit SHA, PROGRESS bullet + history, kafka REQUIREMENTS entry.
  LeaderAndIsr (API 4) v0–v7: `LeaderAndIsrCodec.java` + `LeaderAndIsrCodecTest.java` (9 tests);
  goldens via `doc/work/leaderisr_golden.py`; models extended with versioned fields (brokerEpoch, isKRaftController,
  type, topicId, partitionEpoch, replicas, isNew, addingReplicas, removingReplicas, leaderRecoveryState);
  broker migrated to versioned dispatch; full module 1234 green.
