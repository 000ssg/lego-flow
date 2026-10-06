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

## Activity: InitProducerId (API 22) v0–v4 — IN PROGRESS

In-flight (not committed): new `InitProducerIdCodec` v0–v4 both directions (pinned v0;
v0/v1 byte-identical — nullable TransactionalId:string + TransactionTimeoutMs:int32, v2+
flexible, v3+ appends ProducerId:int64 + ProducerEpoch:int16; response — leading
ThrottleTimeMs int32 (spec default 0, written + discarded) + int16 errorCode +
int64 producerId + int16 producerEpoch, v2+ flexible; the old inline response omitted the
mandatory leading ThrottleTimeMs — malformed on the wire; models unchanged — version-gated
request ProducerId/ProducerEpoch + response ThrottleTimeMs written with spec defaults +
read/discarded) + `InitProducerIdCodecTest` (21 tests) + facade delegation in `KafkaCodec`
(inline v0-only bodies removed) + frozen order tables
`doc/spec/order/InitProducerId.{Request,Response}.txt` + matrix rows 348–352 ✓
(**Transactions sub-category STARTED, 5/24 done**) + PROGRESS.md + module doc/REQUIREMENTS.md
updated. Full module `:lego-flow-kafka` 836 → 857 green. Awaiting commit.

## Last completed (2026-10-06): ListPartitionReassignments (API 46) v0 — dedicated codec ✓

Committed `cf6d7812` (+ backfill): frozen order tables (`doc/spec/order/ListPartitionReassignments.{Request,Response}.txt`)
+ new `ListPartitionReassignmentsCodec` v0 both directions (pinned v0; v0 flexible KIP-482 —
request int32 timeoutMs + nullable compact topics [compact topic name, compact partitionIndexes int32] —
null topics = varint 0; response leading ThrottleTimeMs + int16 errorCode + nullable errorMessage +
compact topics [compact name, compact partitions [int32 partitionIndex, compact replicas,
compact addingReplicas, compact removingReplicas]] + tagged sections per partition + per topic;
models unchanged) + `ListPartitionReassignmentsCodecTest` (17 tests) + facade delegation (old inline
v0-only bodies removed — the inline request/response used fixed arrays + plain strings throughout
and the response omitted the mandatory leading ThrottleTimeMs + top-level nullable ErrorMessage,
malformed on the wire) + matrix row 478 checked, PROGRESS.md + module doc/REQUIREMENTS.md
updated. **Admin sub-category complete 35/35.** Next: Transactions sub-category started with
InitProducerId (API 22) v0–v4; remaining Transactions: AddPartitionsToTxn, AddOffsetsToTxn,
EndTxn, WriteTxnMarkers, TxnOffsetCommit (19 rows).