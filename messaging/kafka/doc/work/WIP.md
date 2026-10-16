# Kafka Codec Versions — WIP Ledger

## RULE (non-negotiable)
A task is **DONE** only when ALL of: (1) the file(s) exist on disk, (2) a real commit
SHA **in this branch's `git log`** contains them, (3) surefire reports are green.
Any entry claiming DONE without a verifiable commit in `git log` is **void** — delete it
and redo the work. NEVER trust a SHA without `git cat-file -e <sha>`.
Consensus is persisted in commits, not in this file alone — commit each completed API.

## Consumer Groups sub-category (44/63) — ledger

| API | Versions | Codec file | Test file | Commit SHA (in git log) | Status |
|---|---|---|---|---|---|
| OffsetCommit (8) | v0–v9 | `OffsetCommitCodec.java` | `OffsetCommitCodecTest.java` (38) | `f8cd4201` | DONE — committed + 1012-module-green verified |
| OffsetFetch (9) | v0–v8 | `OffsetFetchCodec.java` | `OffsetFetchCodecTest.java` (28) | `067d876e` | DONE — committed; 28 tests green, full module 1012 green (verified 2026-10-09) |
| FindCoordinator (10) | v0–v4 | `FindCoordinatorCodec.java` | `FindCoordinatorCodecTest.java` (28) | `e4db2c87` | DONE — committed 2026-10-11; 28 tests green, full module 1040 green (verified this session) |
| JoinGroup (11) | v0–v9 | `JoinGroupCodec.java` | `JoinGroupCodecTest.java` (49) | `5aa5a7a4` | **DONE** — committed; 49 tests green, full module 1089 green (verified this session) |
| Heartbeat (12) | v0–v4 | `HeartbeatCodec.java` | `HeartbeatCodecTest.java` (15) | `0521ca43` | **DONE** — committed; 15 tests green, full module 1128 green (verified this session) |
| LeaveGroup (13) | v0–v5 | `LeaveGroupCodec.java` | `LeaveGroupCodecTest.java` (20) | `ecdc712a` | **DONE** — committed; 20 tests green, full module 1148 green (verified this session) |
| SyncGroup (14) | v0–v5 | — | — | — | NOT STARTED |
| DescribeGroups (15) | v0–v5 | — | — | — | NOT STARTED |
| ListGroups (16) | v0–v4 | — | — | — | NOT STARTED |
| OffsetDelete (47) | v0 | — | — | — | NOT STARTED |

## Next sub-task

SyncGroup (API 14) v0–v5 — spec in
`doc/spec/message/SyncGroupRequest.json` / `SyncGroupResponse.json` (JSON5: Apache license header + inline comments).
Request: v0 base (`GroupId:string + GenerationId:int32 + MemberId:string + Assignments[]Assignment{MemberId:string + Assignment:bytes}`); v3+ nullable `GroupInstanceId:string`; v5+ nullable `ProtocolType:string` + `ProtocolName:string` (KIP-559, ignorable); v4+ flexible.
Response: v0 base (`ErrorCode:int16 + Assignment:bytes`); leading `ThrottleTimeMs:int32` from v1+; v5+ nullable `ProtocolType:string` + `ProtocolName:string` (KIP-559, ignorable); v4+ flexible.

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

## Verified facts (2026-10-16, this branch cleanup-messaging)
- Full `:lego-flow-kafka` module: **1148 tests, 0 failures** (1128 after Heartbeat +20 LeaveGroup), verified via surefire reports after `mvn test`.
- `git log`: OffsetCommit code `f8cd4201`, docs backfill `eef4c02d`; OffsetFetch code `067d876e`, docs backfill `91f7fe78`; FindCoordinator code `e4db2c87` (28 new tests green), docs backfill `dbf5a371`; JoinGroup code `5aa5a7a4` (49 new tests green), docs backfill `c2c12030`; Heartbeat code `0521ca43` (15 new tests green; full module 1128); LeaveGroup code `ecdc712a` (20 new tests green; full module 1148), docs backfill follows.
- Pattern per API: freeze `doc/spec/order/<Api>.{Request,Response}.txt` → dedicated `<Api>Codec` (all versions,
  both directions; flex = KIP-482 compact + varint N+1 + endTags) → replace inline `KafkaCodec` bodies with thin
  delegates → `<Api>CodecTest` (pinned + hand-built goldens + per-version Δ + round-trips + truncation/guards/facade)
  → docs: PHASE6A matrix rows ✓ + commit SHA, PROGRESS bullet + history, kafka REQUIREMENTS entry.
