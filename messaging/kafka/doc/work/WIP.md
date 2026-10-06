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

## Activity: AddOffsetsToTxn (API 25) v0–v3 — IN PROGRESS

In-flight (not committed): new `AddOffsetsToTxnCodec` v0–v3 both directions (pinned v0;
request v0–v2 byte-identical — nullable TransactionalId:string + ProducerId:int64 + ProducerEpoch:int16
+ nullable GroupId:string, v0–v2 fixed-width counts + plain strings; v3 flexible KIP-482 — compact
strings + top-level endTags sentinel (no tagged fields declared); all four fields present from v0, so the
model maps 1:1 onto every version); response v0–v2 byte-identical (leading ThrottleTimeMs:int32 [dflt 0] +
ErrorCode:int16); v3 flexible + top-level endTags. Old inline facade `encodeAddOffsetsToTxnResponse` wrote
only the int16 ErrorCode — omitted the mandatory leading ThrottleTimeMs — malformed on the wire. Models
unchanged (`AddOffsetsToTxnRequest(transactionalId, producerId, producerEpoch, groupId)` /
`AddOffsetsToTxnResponse(errorCode)` arities preserved; the in-house response model exposes no ThrottleTimeMs,
written with the spec default + read/discarded) + `AddOffsetsToTxnCodecTest` (16 tests) + facade delegation in
`KafkaCodec` (inline v0-only bodies removed) + frozen order tables `doc/spec/order/AddOffsetsToTxn.{Request,Response}.txt`
+ matrix rows 370–373 ✓ (**Transactions sub-category 14/24 done**) + PROGRESS.md + module doc/REQUIREMENTS.md
updated. Full module `:lego-flow-kafka` 876 → 892 green. Awaiting commit.

## Last completed (2026-10-06): AddPartitionsToTxn (API 24) v0–v4 — dedicated codec ✓

Committed `3fa44b24` (+ backfill `4aa38ebe`): new `AddPartitionsToTxnCodec` v0–v4 both directions (pinned v0;
request v0–v2 byte-identical, v3 flexible, v4 structural per-transaction `Transactions` layout; response v0–v2
byte-identical, v3 flexible, v4 structural + leading ThrottleTimeMs + top-level ErrorCode + per-transaction
ResultsByTransaction; models unchanged — v4 per-transaction `VerifyOnly` written with the spec default `false` +
read/discarded) + `AddPartitionsToTxnCodecTest` (19 tests) + facade delegation + frozen order tables
`doc/spec/order/AddPartitionsToTxn.{Request,Response}.txt` + matrix rows 358–362 checked, PROGRESS.md + module
doc/REQUIREMENTS.md updated. **Transactions sub-category 10/24 done.** Full module `:lego-flow-kafka` 857 → 876
green. Next: AddOffsetsToTxn (API 25) v0–v3; remaining Transactions: EndTxn, WriteTxnMarkers, TxnOffsetCommit (10 rows).