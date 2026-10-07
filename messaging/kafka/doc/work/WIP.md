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

## Activity: EndTxn (API 26) v0–v3 — IN PROGRESS

In-flight (not committed): new `EndTxnCodec` v0–v3 both directions (pinned v0;
request v0–v2 byte-identical — nullable TransactionalId:string + ProducerId:int64 + ProducerEpoch:int16 +
Committed:bool, v0–v2 fixed-width counts + plain strings; the bool is fixed-width in both encodings; v3 flexible
KIP-482 — compact string + top-level endTags sentinel (no tagged fields declared); all four fields present from v0,
so the model maps 1:1 onto every version); response v0–v2 byte-identical (leading ThrottleTimeMs:int32 [dflt 0] +
ErrorCode:int16); v3 flexible + top-level endTags. Old inline facade `encodeEndTxnResponse` wrote only the int16
ErrorCode — omitted the mandatory leading ThrottleTimeMs — malformed on the wire. Models unchanged
(`EndTxnRequest(transactionalId, producerId, producerEpoch, committed)` / `EndTxnResponse(errorCode)` arities
preserved; the in-house response model exposes no ThrottleTimeMs, written with the spec default + read/discarded)
+ `EndTxnCodecTest` (16 tests) + facade delegation in `KafkaCodec` (inline v0-only bodies removed) + frozen order
tables `doc/spec/order/EndTxn.{Request,Response}.txt` + matrix rows 381–384 ✓ (**Transactions sub-category 18/24
done**) + PROGRESS.md + module doc/REQUIREMENTS.md updated. Full module `:lego-flow-kafka` 892 → 908 green.

## Last completed (2026-10-06): AddOffsetsToTxn (API 25) v0–v3 — dedicated codec ✓

Committed `76582a67` (+ backfill `bb9d2aaf`): new `AddOffsetsToTxnCodec` v0–v3 both directions (pinned v0;
request v0–v2 byte-identical — nullable TransactionalId + ProducerId + ProducerEpoch + nullable GroupId, v0–v2
fixed-width counts + plain strings; v3 flexible KIP-482 — compact strings + top-level endTags sentinel; all four
fields present from v0, so the model maps 1:1 onto every version); response v0–v2 byte-identical (leading
ThrottleTimeMs:int32 [dflt 0, written + discarded] + ErrorCode:int16), v3 flexible + top-level endTags. Models
unchanged (`AddOffsetsToTxnRequest(transactionalId, producerId, producerEpoch, groupId)` /
`AddOffsetsToTxnResponse(errorCode)` arities preserved — the response ThrottleTimeMs is written with the spec
default + read/discarded, documented in the codec class javadoc). + `AddOffsetsToTxnCodecTest` (16 tests) + facade
delegation in `KafkaCodec` (inline v0-only bodies removed) + frozen order tables
`doc/spec/order/AddOffsetsToTxn.{Request,Response}.txt` + matrix rows 370–373 checked, PROGRESS.md + module
doc/REQUIREMENTS.md updated. **Transactions sub-category 14/24 done.** Full module `:lego-flow-kafka` 876 → 892
green. Next: EndTxn (API 26) v0–v3; remaining Transactions: WriteTxnMarkers, TxnOffsetCommit (6 rows).