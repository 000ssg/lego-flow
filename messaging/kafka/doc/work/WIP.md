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

## Activity: WriteTxnMarkers (API 27) v0–v1 — IN PROGRESS

In-flight (not committed): new `WriteTxnMarkersCodec` v0–v1 both directions (pinned v0;
request v0 fixed-width — `Markers[] { ProducerId:int64 + ProducerEpoch:int16 + TransactionResult:bool +
Topics[] { Name:string + PartitionIndexes[]int32 } + CoordinatorEpoch:int32 }`, coordinator epoch LAST,
partitions grouped by topic; v1 flexible KIP-482 — compact strings/arrays, varint N+1 counts, per-partition/per-topic/
per-marker/top-level endTags); response v0 fixed-width — `Markers[] { ProducerId:int64 + Topics[] { Name:string +
Partitions[] { PartitionIndex:int32 + ErrorCode:int16 } } }` (no leading ThrottleTimeMs); v1 flexible + endTags. Old
inline facade request wrote a flat non-grouped `partitions[]` with the coordinator epoch in the wrong position +
non-nullable `writeString` for topic names — malformed on the wire. In-house request carries a flat `partitions`
list; the wire groups by topic — encode groups by topic (insertion-ordered), decode re-flattens per-topic groups back
into the flat list (preserves every (topic, partition) pair + per-topic order, reorganizes cross-topic interleaving;
documented in the codec class javadoc). Models unchanged (`WriteTxnMarkersRequest(markers)` /
`WriteTxnMarkersResponse(markers)` arities preserved) + `WriteTxnMarkersCodecTest` (17 tests) + facade delegation in
`KafkaCodec` (inline v0-only bodies removed) + frozen order tables
`doc/spec/order/WriteTxnMarkers.{Request,Response}.txt` + matrix rows 392–393 ✓ (**Transactions sub-category 20/24
done**) + PROGRESS.md + module doc/REQUIREMENTS.md updated. Full module `:lego-flow-kafka` 908 → 925 green.

## Last completed (2026-10-06): EndTxn (API 26) v0–v3 — dedicated codec ✓

Committed `2e1e539a` (+ backfill `dbd68b52`): new `EndTxnCodec` v0–v3 both directions (pinned v0;
request v0–v2 byte-identical — nullable TransactionalId + ProducerId + ProducerEpoch + Committed:bool, v0–v2
fixed-width counts + plain strings; the bool is fixed-width in both encodings; v3 flexible KIP-482 — compact string +
top-level endTags sentinel; all four fields present from v0, so the model maps 1:1 onto every version); response
v0–v2 byte-identical (leading ThrottleTimeMs:int32 [dflt 0, written + discarded] + ErrorCode:int16), v3 flexible +
top-level endTags. Models unchanged (`EndTxnRequest(transactionalId, producerId, producerEpoch, committed)` /
`EndTxnResponse(errorCode)` arities preserved — the response ThrottleTimeMs is written with the spec
default + read/discarded, documented in the codec class javadoc). + `EndTxnCodecTest` (16 tests) + facade
delegation in `KafkaCodec` (inline v0-only bodies removed) + frozen order tables
`doc/spec/order/EndTxn.{Request,Response}.txt` + matrix rows 381–384 checked, PROGRESS.md + module
doc/REQUIREMENTS.md updated. **Transactions sub-category 18/24 done.** Full module `:lego-flow-kafka` 892 → 908
green. Next: WriteTxnMarkers (API 27) v0–v1; remaining Transactions: TxnOffsetCommit (4 rows).