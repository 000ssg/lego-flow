# Kafka Codec Versions — WIP Ledger

## RULE (non-negotiable)
A task is **DONE** only when ALL of: (1) the file(s) exist on disk, (2) a real commit
SHA **in this branch's `git log`** contains them, (3) surefire reports are green.
Any entry claiming DONE without a verifiable commit in `git log` is **void** — delete it
and redo the work. NEVER trust a SHA without `git cat-file -e <sha>`.
Consensus is persisted in commits, not in this file alone — commit each completed API.

## Consumer Groups sub-category (24/63) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| OffsetCommit (8) | v0–v9 | `OffsetCommitCodec.java` | `OffsetCommitCodecTest.java` (38) | `f8cd4201` | DONE — committed + 1012-module-green verified |
| OffsetFetch (9) | v0–v8 | `OffsetFetchCodec.java` | `OffsetFetchCodecTest.java` (28) | `067d876e` | DONE — committed; 28 tests green, full module 1012 green (verified 2026-10-09) |
| FindCoordinator (10) | v0–v4 | `FindCoordinatorCodec.java` | `FindCoordinatorCodecTest.java` (28) | `e4db2c87` | DONE — committed 2026-10-11; 28 tests green, full module 1040 green (verified this session) |
| JoinGroup (11) | v0–v9 | — | — | — | NOT STARTED |
| Heartbeat (12) | v0–v5 | — | — | — | NOT STARTED |
| LeaveGroup (13) | v0–v4 | — | — | — | NOT STARTED |
| SyncGroup (14) | v0–v5 | — | — | — | NOT STARTED |
| DescribeGroups (15) | v0–v5 | — | — | — | NOT STARTED |
| ListGroups (16) | v0–v4 | — | — | — | NOT STARTED |
| OffsetDelete (47) | v0 | — | — | — | NOT STARTED |

Next up after FindCoordinator commit: **JoinGroup (API 11) v0–v9** — spec in
`doc/spec/message/JoinGroupRequest.json` / `JoinGroupResponse.json`.
Request: v0–v5 base (`Group:string + ProtocolType:string + MemberId:string + GroupInstanceId:string(nullable) + RebalanceTimeoutMs:int32[1+] + SessionTimeoutMs:int32 + ProtocolData:Protocol[] + Reason:string[8+]`), flex 6+.
Response: `ThrottleTimeMs:int32[2+]`, `ErrorCode:int16`, `GenerationId:int32`, `Protocol:string[1-9]`, `Leader:string`, `MemberId:string[4-9]`, `Members[]Member{MemberId:string, GroupInstanceId:string[5-9](nullable), Metadata:string[1-9]}`.
In-house models are single-member `JoinGroupRequest(groupId, protocolType, memberId, protocolData)` /
`JoinGroupResponse(errorCode, generationId, protocol, leader, memberId, members)` — facade pins v0; flex/batch
fields encoded/decoded with spec-default handling like sibling codecs. Models unchanged if possible.

## Verified facts (2026-10-11, this branch cleanup-messaging)
- Full `:lego-flow-kafka` module: **1040 tests, 0 failures** (baseline at 1012 after OffsetFetch +28 FindCoordinator), verified via surefire reports after `mvn test`.
- `git log`: OffsetCommit code `f8cd4201`, docs backfill `eef4c02d`; OffsetFetch code `067d876e`, docs backfill `91f7fe78`; FindCoordinator code `e4db2c87` (28 new tests green); FindCoordinator docs backfill = this commit.
- Pattern per API: freeze `doc/spec/order/<Api>.{Request,Response}.txt` → dedicated `<Api>Codec` (all versions,
  both directions; flex = KIP-482 compact + varint N+1 + endTags) → replace inline `KafkaCodec` bodies with thin
  delegates → `<Api>CodecTest` (pinned + hand-built goldens + per-version Δ + round-trips + truncation/guards/facade)
  → docs: PHASE6A matrix rows ✓ + commit SHA, PROGRESS bullet + history, kafka REQUIREMENTS entry.
