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

## Activity: DeleteTopics (API 20) v0–v6 — dedicated codec (NEXT, not started)

## Last completed (2026-10-03): CreateTopics (API 19) v0–v7 — dedicated codec ✓

Committed in 3 commits: specs `c0c82322` (frozen order tables
`doc/spec/order/CreateTopics.{Request,Response}.txt`), code `6a9bd355`
(new `CreateTopicsCodec` v0–v7 both directions, pinned v0; facade delegation;
model `CreateTopicsRequest` + TopicCreate/Assignment + assignments; the old
inline request omitted the mandatory Assignments array — fixed), docs/matrix
(next commit): all 8 rows checked (matrix rows 167–174), `CODEC_VALIDATION_CreateTopics.md`,
PROGRESS.md (Admin checklist row + Log), REQUIREMENTS.md commit section.
Note: 3 ListOffsets docs (`RULES.md`, `CODEC_VALIDATION_ListOffsets.md`,
`doc/spec/SPEC_VALIDATION_ListOffsets.md`) were NEVER committed — still untracked
as of this session; `doc/work/` (WIP.md + LISTOFFSETS_SETTLED.md) also untracked.
Tests: CreateTopicsCodecTest 25; full module 617 → 642 green, 0F/0E/0S.
Next: DeleteTopics v0–v6 (matrix rows 175–181) — freeze order tables first, then codec + test per the same pattern.