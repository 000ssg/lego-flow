# WIP — kafka-codec fast-recovery ledger

Protocol (agreed 2026-10-01): register activity at start; on sub-task completion keep ONLY
a one-line "last completed" entry and re-register; everything else is filled live. Goal:
recover state in <2 min after any interruption (agent death, IDE restart, provider failure)
without re-deriving where/what is in flight.

## Environment (fixed for this module — do not re-derive)

- repo: `/Users/sergey.sidorov/work/projects/github/lego-flow` — branch `cleanup-messaging`
- module cwd: `messaging/kafka` (all paths below relative to it)
- JDK 25. Build: `mvn compile -DskipTests -pl '!benchmarks'` (from repo root).
  Tests: `./gradlew test` from repo root, or per-module. Maven root POM has EMPTY
  modules list — child modules build individually.
- Spec-first workflow: `python3 gen_kafka.py {delta|order|freeze} <Api> <Request|Response>`
  (script: `messaging/kafka/gen_kafka.py`). Spec JSON: `doc/spec/message/*.json`
  (== Apache Kafka 3.6.1, byte-equivalent — see LISTOFFSETS_SETTLED.md).
  FROZEN order tables: `doc/spec/order/<Api>.<Kind>.txt` — BINDING wire order; freeze
  materializes them, refuses to overwrite a differing table.
- Pattern references: `doc/work/LISTOFFSETS_SETTLED.md` (reusable evaluation ledger,
  reusable by all future sub-tasks), `doc/CODEC_VALIDATION_ListOffsets.md` (validation doc
  template), `src/test/java/.../codec/ListOffsetsCodecTest.java` (test template).
- Phase plan: `doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md`
  (⚠️ its per-version Δ rows are WRONG for some APIs — spec JSON + frozen order tables
  win; update matrix rows as part of each sub-task).

## Conventions (settled, do not re-litigate)

- One dedicated codec class per sub-category (`<Api>Codec`), version-gated encode/decode,
  `PINNED_VERSION` for client/broker wire, full spec range for frame produce/parse.
- Model arity preserved: version-gated fields the model does not expose are written with
  the spec default and read + discarded (documented in class javadoc).
- Facade `KafkaCodec` methods delegate to the dedicated codec (ListOffsets pattern,
  lines ~397–439); inline monolith implementations are deleted on wiring.
- Test per codec: pinned + byte-layout per version + full-range round-trips + flexible
  layout + version guard (`CodecNotImplementedException`).
- Commit: `git add <files>`, heredoc message + `Co-Authored-By: AI assistant`, update
  plan matrix + PROGRESS.md + module doc/REQUIREMENTS.md; NEVER push.

## Activity: OffsetCommit (API 8) v0–v9 — IN PROGRESS

In-flight (not committed): new `OffsetCommitCodec` v0–v9 both directions (pinned v0). Request layout (frozen
`doc/spec/order/OffsetCommit.Request.txt`): v0 base (7 fields) `GroupId:string + Topics[] { Name:string +
Partitions[] { PartitionIndex:int32 + CommittedOffset:int64 + CommittedMetadata:string(nullable) } }`; v1+ adds
`GenerationIdOrMemberEpoch:int32 + MemberId:string` and per-partition `CommitTimestamp:int64` (v1 only, written -1);
v2–v4 adds `RetentionTimeMs:int64` (written -1, dropped at v5); v6+ adds per-partition `CommittedLeaderEpoch:int32`
(written -1); v7+ adds nullable `GroupInstanceId:string` (written null; non-flexible v7 = legacy nullable string,
flexible v8+ = nullable compact). Response layout (frozen `doc/spec/order/OffsetCommit.Response.txt`):
`Topics[] { Name:string, Partitions[] { PartitionIndex:int32, ErrorCode:int16 } }`, leading `ThrottleTimeMs:int32`
from v3+ (written 0). v8–v9: KIP-482 flexible encoding (compact strings, N+1 varint counts, trailing endTags=0);
v9 wire-identical to v8 (KIP-848). Inline v0-only `KafkaCodec` methods replaced with thin delegates.
`OffsetCommitCodecTest` (38 tests) + frozen order tables + matrix rows 231–240 ✓ + PROGRESS.md + module
doc/REQUIREMENTS.md updated. Full module `:lego-flow-kafka` 946 → 984 green. Next: OffsetFetch (API 9) v0–v8
(Consumer Groups sub-category, 63 rows total).

## Last completed (2026-10-07): TxnOffsetCommit (API 28) v0–v3 — dedicated codec ✓

Committed `ea8a78e8` (+ backfill `97f7a58f`): new `TxnOffsetCommitCodec` v0–v3 both directions (pinned v0;
request v0–v2 fixed-width — non-nullable TransactionalId:string + GroupId:string + ProducerId:int64 +
ProducerEpoch:int16 + Topics[] { Name:string + Partitions[] { PartitionIndex:int32 + CommittedOffset:int64 +
CommittedMetadata:string(nullable) } }, v2+ appends per-partition CommittedLeaderEpoch:int32 [dflt -1, written +
discarded — the model does not expose it]; v3 flexible KIP-482 — compact strings, varint N+1 counts, top-level
endTags sentinel (no tagged fields declared), and the v3-only triple GenerationId:int32 [dflt -1] + MemberId:string
[dflt ""] + GroupInstanceId:string [dflt null] written with spec defaults + read/discarded); response v0–v2
byte-identical (leading ThrottleTimeMs:int32 [dflt 0, written + discarded] + Topics[] { Name:string + Partitions[]
{ PartitionIndex:int32 + ErrorCode:int16 } }), v3 flexible + endTags. Old inline facade request omitted TransactionalId
+ GroupId entirely + called a non-existent 3-arg constructor; old inline response omitted the mandatory leading
ThrottleTimeMs — malformed on the wire + a compile error. Models unchanged (`TxnOffsetCommitRequest(transactionalId,
groupId, producerId, producerEpoch, topics)` / `TxnOffsetCommitResponse(topics)` arities preserved; version-gated
request fields + the response ThrottleTimeMs are written with the spec default + read/discarded, documented in the
codec class javadoc) + `TxnOffsetCommitCodecTest` (21 tests) + facade delegation in `KafkaCodec` (inline v0-only
bodies removed) + frozen order tables `doc/spec/order/TxnOffsetCommit.{Request,Response}.txt` + matrix rows 399–402
✓ (**Transactions sub-category 24/24 — complete**) + PROGRESS.md + module doc/REQUIREMENTS.md updated. Full module
`:lego-flow-kafka` 925 → 946 green. Next: Consumer Groups sub-category (63 rows).

## Superseded (2026-10-07): WriteTxnMarkers (API 27) v0–v1 — dedicated codec ✓

Committed `0aae7710` (+ backfill `dd4522f3`): new `WriteTxnMarkersCodec` v0–v1 both directions (pinned v0;
request v0 fixed-width — `Markers[] { ProducerId:int64 + ProducerEpoch:int16 + TransactionResult:bool +
Topics[] { Name:string + PartitionIndexes[]int32 } + CoordinatorEpoch:int32 }`, coordinator epoch LAST, partitions
grouped by topic; v1 flexible KIP-482 — compact strings/arrays, varint N+1 counts, per-partition/per-topic/per-marker/
top-level endTags (no tagged fields declared)); response v0 fixed-width — `Markers[] { ProducerId:int64 +
Topics[] { Name:string + Partitions[] { PartitionIndex:int32 + ErrorCode:int16 } } }` (no leading ThrottleTimeMs),
v1 flexible + endTags. In-house request carries a flat `partitions` list; the wire groups by topic — encode groups by
topic (insertion-ordered), decode re-flattens per-topic groups into the flat list (preserves every (topic, partition)
pair + per-topic order, reorganizes cross-topic interleaving; documented in the codec class javadoc). Models unchanged
(`WriteTxnMarkersRequest(markers)` / `WriteTxnMarkersResponse(markers)` arities preserved) + `WriteTxnMarkersCodecTest`
(17 tests) + facade delegation in `KafkaCodec` (inline bodies removed) + frozen order tables
`doc/spec/order/WriteTxnMarkers.{Request,Response}.txt` + matrix rows 392–393 checked, PROGRESS.md + module
doc/REQUIREMENTS.md updated. **Transactions sub-category 20/24 done.** Full module `:lego-flow-kafka` 908 → 925
green. Next: TxnOffsetCommit (API 28) v0–v3.