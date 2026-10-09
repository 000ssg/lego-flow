# Kafka Codec Versions — WIP Ledger

## RULE (non-negotiable)
A task is **DONE** only when ALL of: (1) the file(s) exist on disk, (2) a real commit
SHA **in this branch's `git log`** contains them, (3) surefire reports are green.
Any entry claiming DONE without a verifiable commit in `git log` is **void** — delete it
and redo the work. NEVER trust a SHA without `git cat-file -e <sha>`.
Consensus is persisted in commits, not in this file alone — commit each completed API.

## Consumer Groups sub-category (10/63) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| OffsetCommit (8) | v0–v9 | `OffsetCommitCodec.java` | `OffsetCommitCodecTest.java` (38) | `f8cd4201` | DONE — committed + 1012-module-green verified |
| OffsetFetch (9) | v0–v8 | `OffsetFetchCodec.java` | `OffsetFetchCodecTest.java` (28) | (in flight — commit this session) | IN FLIGHT — files on disk, 28 tests green, facade delegates, 2 spec/order txt files, NOT YET COMMITTED |
| FindCoordinator (10) | v0–v4 | — | — | — | **NOT STARTED** (previous "DONE, commit 8720b451, 30 tests" claim was FALSE — commit does not exist, no files, no reports) |
| JoinGroup (11) | v0–v9 | — | — | — | NOT STARTED |
| Heartbeat (12) | v0–v5 | — | — | — | NOT STARTED |
| LeaveGroup (13) | v0–v4 | — | — | — | NOT STARTED |
| SyncGroup (14) | v0–v5 | — | — | — | NOT STARTED |
| DescribeGroups (15) | v0–v5 | — | — | — | NOT STARTED |
| ListGroups (16) | v0–v4 | — | — | — | NOT STARTED |
| OffsetDelete (47) | v0 | — | — | — | NOT STARTED |

Next up after OffsetFetch commit: **FindCoordinator (API 10) v0–v4** — spec in
`doc/spec/message/FindCoordinatorRequest.json` / `FindCoordinatorResponse.json`.
Request: `Key:string[0-3]`, `KeyType:int8[1+]`, `CoordinatorKeys:[]string[4+]` (KIP-699), flex 3+.
Response: `ThrottleTimeMs:int32[1+]`, `ErrorCode:int16[0-3]`, `ErrorMessage:string[1-3]`,
`NodeId:int32[0-3]`, `Host:string[0-3]`, `Port:int32[0-3]`, v4+ `Coordinators[]Coordinator{Key,NodeId,Host,Port,ErrorCode,ErrorMessage}`, flex 3+.
In-house models are single-coordinator `FindCoordinatorRequest(key, keyType)` /
`FindCoordinatorResponse(errorCode, nodeId, host, port)` — facade pins v0; v4 batch fields
encoded/decoded with spec-default handling like sibling codecs. Models unchanged if possible.

## Verified facts (2026-10-09, this branch cleanup-messaging)
- Full `:lego-flow-kafka` module: **1012 tests, 0 failures** (baseline at 984 after OffsetCommit +28 OffsetFetch).
- `git log`: OffsetCommit code `f8cd4201`, docs backfill `eef4c02d`. No FindCoordinator commit exists in any branch/reflog/fsck.
- Pattern per API: freeze `doc/spec/order/<Api>.{Request,Response}.txt` → dedicated `<Api>Codec` (all versions,
  both directions; flex = KIP-482 compact + varint N+1 + endTags) → replace inline `KafkaCodec` bodies with thin
  delegates → `<Api>CodecTest` (pinned + hand-built goldens + per-version Δ + round-trips + truncation/guards/facade)
  → docs: PHASE6A matrix rows ✓ (PENDING→SHA in follow-up docs commit), PROGRESS bullet + history, kafka REQUIREMENTS entry.
