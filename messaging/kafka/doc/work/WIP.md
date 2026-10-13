# Kafka Codec Versions — WIP Ledger

## RULE (non-negotiable)
A task is **DONE** only when ALL of: (1) the file(s) exist on disk, (2) a real commit
SHA **in this branch's `git log`** contains them, (3) surefire reports are green.
Any entry claiming DONE without a verifiable commit in `git log` is **void** — delete it
and redo the work. NEVER trust a SHA without `git cat-file -e <sha>`.
Consensus is persisted in commits, not in this file alone — commit each completed API.

## Consumer Groups sub-category (34/63) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| OffsetCommit (8) | v0–v9 | `OffsetCommitCodec.java` | `OffsetCommitCodecTest.java` (38) | `f8cd4201` | DONE — committed + 1012-module-green verified |
| OffsetFetch (9) | v0–v8 | `OffsetFetchCodec.java` | `OffsetFetchCodecTest.java` (28) | `067d876e` | DONE — committed; 28 tests green, full module 1012 green (verified 2026-10-09) |
| FindCoordinator (10) | v0–v4 | `FindCoordinatorCodec.java` | `FindCoordinatorCodecTest.java` (28) | `e4db2c87` | DONE — committed 2026-10-11; 28 tests green, full module 1040 green (verified this session) |
| JoinGroup (11) | v0–v9 | `JoinGroupCodec.java` | `JoinGroupCodecTest.java` (49) | `5aa5a7a4` | **DONE** — committed; 49 tests green, full module 1089 green (verified this session) |
| Heartbeat (12) | v0–v5 | — | — | — | NOT STARTED |
| LeaveGroup (13) | v0–v4 | — | — | — | NOT STARTED |
| SyncGroup (14) | v0–v5 | — | — | — | NOT STARTED |
| DescribeGroups (15) | v0–v5 | — | — | — | NOT STARTED |
| ListGroups (16) | v0–v4 | — | — | — | NOT STARTED |
| OffsetDelete (47) | v0 | — | — | — | NOT STARTED |

## Next sub-task

Heartbeat (API 12) v0–v4 — spec in
`doc/spec/message/HeartbeatRequest.json` / `HeartbeatResponse.json`.
Request: v0 base (`GroupId:string + GenerationId:int32 + MemberId:string`); v3+ `GroupInstanceId:string(nullable)`; v4 flexible.
Response: v0 base `ErrorCode:int16`; v1+ `ThrottleTimeMs:int32` leading (spec dflt 0).
In-house models are `HeartbeatRequest(groupId, generationId, memberId)` / `HeartbeatResponse(errorCode)` — facade pins v0; version-gated fields encoded/decoded with spec-default handling like sibling codecs. Models unchanged if possible.

## KNOWN GAP (user-flagged UNACCEPTABLE — fix AFTER the JoinGroup sub-task, 2026-10-12)
**Audit: negotiated version is ignored at runtime, not in the codecs.**
- Codec layer is clean: all 24 dedicated `<Api>Codec` classes accept `short version`
  and branch on it (verified per-file: `version >= FLEXIBLE_START`, per-version
  field gating; v0-only APIs pin via `checkVersion`). NONE ignore the version param.
- Facade `KafkaCodec.encode*/decode*` body methods take NO version param → hardcode
  `PINNED_VERSION` (v0, except ListOffsets v1) or literal `(short) 0` (ApiVersions,
  Sasl*). ~50 methods.
- `KafkaBroker.processRequest` decodes `RequestHeader` (incl. `apiVersion`) but
  decodes bodies via pinned facade and encodes responses at pinned version —
  the header version is never threaded into any codec. Broker advertises full
  min/max range in ApiVersionsResponse it cannot actually serve → a foreign
  client sending e.g. v5 Metadata / v8 Fetch gets misparsed as v0.
- Clients (`KafkaProducer`/`KafkaConsumer`/`KafkaAdminClient`) always send header
  version 0, never negotiate (AdminClient.apiVersions() exists but is not consumed
  to select per-API versions).
- In-house round-trip stays self-consistent (always v0/v1), which is why tests pass.
**Follow-up fix (sequenced after JoinGroup):** (1) broker: thread `header.apiVersion()`
into decode + encode response at that version, reject out-of-range; (2) facade: add
version overloads or have broker call dedicated codecs directly; (3) clients: perform
ApiVersions negotiation and use negotiated version in header + body.

## Verified facts (2026-10-11, this branch cleanup-messaging)
- Full `:lego-flow-kafka` module: **1089 tests, 0 failures** (1040 after FindCoordinator +49 JoinGroup), verified via surefire reports after `mvn test`.
- `git log`: OffsetCommit code `f8cd4201`, docs backfill `eef4c02d`; OffsetFetch code `067d876e`, docs backfill `91f7fe78`; FindCoordinator code `e4db2c87` (28 new tests green), docs backfill `dbf5a371`; JoinGroup code `5aa5a7a4` (49 new tests green), docs backfill follows.
- Pattern per API: freeze `doc/spec/order/<Api>.{Request,Response}.txt` → dedicated `<Api>Codec` (all versions,
  both directions; flex = KIP-482 compact + varint N+1 + endTags) → replace inline `KafkaCodec` bodies with thin
  delegates → `<Api>CodecTest` (pinned + hand-built goldens + per-version Δ + round-trips + truncation/guards/facade)
  → docs: PHASE6A matrix rows ✓ + commit SHA, PROGRESS bullet + history, kafka REQUIREMENTS entry.
