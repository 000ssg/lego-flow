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

## Activity: (none — idle)

## Last completed (2026-10-04): DeleteTopics (API 20) v0–v6 — dedicated codec ✓

Committed: frozen order tables + new `DeleteTopicsCodec` v0–v6 both directions
(pinned v0; request v0–v3 byte-identical, v4+ flexible, v6 reorganized into
Topics[]DeleteTopicState[Name?, TopicId] — names + all-zero TopicId; response v1+
ThrottleTimeMs, v5+ ErrorMessage, v6+ TopicId — spec defaults written, read + discarded;
models unchanged) + `DeleteTopicsCodecTest` (22 tests) + facade delegation + docs/matrix
(rows 175–181 checked). Full module 642 → 664 green, 0F/0E/0S.
Next: DeleteRecords (API 21) v0–v2 (matrix rows 182–184) or next Admin API per plan.