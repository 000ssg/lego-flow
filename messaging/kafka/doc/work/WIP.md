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

## Activity: AlterConfigs (API 33) v0–v2 — IN PROGRESS

In-flight (not committed): new `AlterConfigsCodec` v0–v2 both directions (pinned v0; request
v0 base — int32 count + [int8 resourceType, string resourceName, int32-count configs
[string name, nullable string value]] + trailing bool validateOnly; v1 byte-identical;
v2 flexible KIP-482 varint N+1 counts + compact strings + per-struct tagged section;
response — leading ThrottleTimeMs int32 (spec default 0) + int32 count + [int16 errorCode,
nullable errorMessage, int8 resourceType, string resourceName], v1 byte-identical, v2 flexible;
models unchanged) + `AlterConfigsCodecTest` (21 tests) + facade delegation in `KafkaCodec`
(inline v0-only bodies removed — the inline response omitted the leading ThrottleTimeMs,
per-result ErrorMessage and per-result ResourceType, malformed on the wire) + frozen order
tables `doc/spec/order/AlterConfigs.{Request,Response}.txt` + matrix rows 464–466 ✓ (Admin 33/35)
+ PROGRESS.md + module doc/REQUIREMENTS.md updated. Full module `:lego-flow-kafka`
782 → 803 green, 0 failures/errors/skipped. Awaiting commit.

## Last completed (2026-10-05): CreatePartitions (API 37) v0–v3 — dedicated codec ✓

Committed: frozen order tables (`doc/spec/order/CreatePartitions.{Request,Response}.txt`) + new
`CreatePartitionsCodec` v0–v3 both directions (pinned v0; request v0/v1 byte-identical —
int32 count + [int16 name, int32 count, int32 assignmentsCount + [int32 brokerIds]] +
int32 timeoutMs + bool validateOnly; v2–v3 flexible; response — leading ThrottleTimeMs
int32 present from v0, spec default 0, written + discarded; int16 errorCode +
nullable ErrorMessage per result; v2–v3 flexible; models unchanged) +
`CreatePartitionsCodecTest` (29 tests) + facade delegation (old inline v0-only bodies
removed — the inline request omitted Assignments + ValidateOnly, the inline response
omitted the leading ThrottleTimeMs + nullable ErrorMessage, malformed on the wire) +
matrix rows 437–440 checked, PROGRESS.md + module doc/REQUIREMENTS.md updated.
Next: next Admin API per plan (remaining Admin 13/35; Transactions 24, Consumer Groups 63,
Metadata/Cluster 44).