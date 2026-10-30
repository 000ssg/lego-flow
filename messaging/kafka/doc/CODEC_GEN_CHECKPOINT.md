# Kafka Codec Generation — Durable Checkpoint

Purpose: recovery anchor after session loss (compaction/IDE/LLM failure). Read this FIRST after
any interruption, then `doc/plans/messaging/PROGRESS.md`, then `CODEC_GENERATION_SPEC.md`.
Update at the end of every work session — do not let findings live only in conversation context.

Last updated: 2026-09-29 (post-incident recovery session)

---

## 1. STATUS

| Item | State |
|------|-------|
| Fetch v13 (row 128) — TopicId `Uuid` replaces topic `string` | DONE, committed `abb533be` |
| Fetch v14 (row 128) — request wire-identical to v13; response v13/v14/v15 all map to V13 methods | DONE, verified 2026-09-29 (commits `abb533be` + `5aed792b` docs/matrix) |
| Tests: FetchCodecTest 83 tests, module suite 590 green (per PROGRESS.md 2026-09-28) | GREEN — re-verify with `mvn test -pl messaging/kafka` before claiming |
| Generators: `gen_kafka.py` (spec-first, --list/--show/--stats) + `gen_kafka_structs.py` | DONE, in `messaging/kafka/` |
| Next: Fetch v15 (row 129) — tagged `ReplicaState` on request partitions + response-side additions | TODO |

## 2. FINDINGS (assurance: ABS=absolute/verified, VER=verified this session, IN=under investigation, REJ=rejected)

### Wire / protocol
- **ABS** Fetch v13+: topics use `Uuid` topicId, not string (spec row 128, v13).
- **ABS** Fetch v14: brokerId added (client sends 0); leaderEpochs.v13→v14 change; `maxBytes`→`maxBytesPerPartition` in v14 request.
- **ABS** Compact structs (all v4+, v4 has null prefix byte, v5+ no prefix): `byteArray = byte[]`, `string = utf8` — gen_kafka.py emits the legacy `byteArray→string` remap for these rows.
- **VER** Producer v12+ `log_append_time` is `int64` (KafkaProducerMessageBuilder is correct); v10/v11 stay int32 (KafkaProducerMessage was correct, no change needed).
- **VER** Commit messages (git log): `abb533be` = "Fetch v13 (fetch I/O row 128) — TopicId uuid replaces the Topic string"; `5aed792b` = "docs + matrix — record Fetch v13/v14 (row 128)".
- **IN** Fetch v15 (next row, 129): tagged `ReplicaState` in request partitions + response-side fields — read `messaging/kafka/doc/spec/message/FetchRequest.json` / `FetchResponse.json` before implementing; the vendored spec is the only layout source (per the 2026-09-23 ground rule, plan §3).

### Generation infra
- **ABS** Spec-first generator: `gen_kafka.py` parses `doc/spec/` markdown (field rows: name/type/broker-type/value), emits `Kafka*Message*.java` — no hand-written per-version classes.
- **VER** Build: `cd messaging/kafka && mvn install -DskipTests` then `mvn test -pl messaging/kafka -Dtest='Kafka*CodecTest'` from repo root works.
- **VER** Test pattern: `KafkaFetchCodecTest` variant tests (`fetchV*_wireRoundTrip`, `fetchV*_fieldsAndRouting`) are the template for new rows; `LegoVirtualExecutorService` cleanup needed in `@AfterEach` (test hangs otherwise).
- **VER** Raw-wire constants (e.g. `FETCH_V13_14_RAW_KAFKA`) encode producer v11 compact bytes: `01 00 08 12 00 02 00 01 00 01 00 06 00 00 00 23 00 01 01 00 02 00 01 00 00 00 02 14 45 4E 4F 57` — used by v11–v14 round-trip tests.

### Repo state
- **VER** Branch `cleanup-messaging` (tracks origin), clean except this checkpoint file.
- **VER** Matrix: `messaging/kafka/doc/KAFKA_CODEC_VERSION_MATRIX.md` row 128 = v13/v14 current Fetch. `gen_kafka.py --list` shows remaining: `fetch v15+ (row 129), ApiVersions v3 (row 118), Produce v8 (row 109), ...`.
- **VER** Generated output in `src/main/java/ssg/legoflow/messaging/kafka/api/request/` (KafkaFetchMessageBuilderRequest + row-typed Fetch classes) and `.../api/response/` (KafkaFetchMessageResponse, KafkaFetchMessageRaw + per-version `*ResponseV*`).
- **ABS** `CODEC_GENERATION_SPEC.md` + `CODEC_GEN_META_SPEC.txt` in `messaging/kafka/doc/` are the authoritative source-of-truth docs.

## 3. INCIDENT LOG (why this file exists)

- 2026-09-29: Previous session (id f4a73206-...) lost to context compaction. It had re-derived
  git status / file listings / spec content ~25 times instead of writing the Fetch v13/v14
  generator. All findings lived only in conversation context → lost. Recovered by dumping the
  session from `~/.hermes/state.db` (sqlite, `messages` table: id/role/content) into
  `/tmp/prev_session_dump.txt` (762 lines).
- **Anti-loop rules** (hard):
  1. One discovery pass MAX (git state + file list + generator subcommands in a single command
     batch redirected to /tmp, then read the file). Re-running discovery = loop; stop.
  2. After each finished item: update PROGRESS.md + this file IMMEDIATELY, then move on.
  3. If the same question is asked twice in one session → it is an answer-the-question problem,
     write the answer to a file, do not re-derive.
  4. No verification ping-pong: read a file at most once per session unless it changed on disk
     (check mtime first).

## 4. RECOVERY PROTOCOL (next session)

1. Read THIS file. Read `doc/plans/messaging/PROGRESS.md` tail. `git log --oneline -5`.
2. Run ONE combined discovery: `{ git status --short; git branch --show-current; python3 messaging/kafka/gen_kafka.py --list 2>&1 | head; } > /tmp/disc.txt 2>&1` then read /tmp/disc.txt.
3. Pick the next item from §1 STATUS. Implement. Update §1 + PROGRESS.md + commit at each done item.
4. If state is unclear: sqlite dump of the last session first (command in §5) — do not re-derive by hand.

## 5. REFERENCES

- Generator: `messaging/kafka/gen_kafka.py` (subcommands: --list, --show <kind>, --stats, <row> emit). Spec: `messaging/kafka/doc/CODEC_GENERATION_SPEC.md`.
- Row format: `<protocolFamily> <kind> <version>` e.g. `fetch v15`; row 129 = Fetch v15+ leader epoch.
- Plan detail: `doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md` (518 lines).
- Prior session dump (if still present): `/tmp/prev_session_dump.txt`; regenerate:
  `sqlite3 ~/.hermes/state.db "SELECT id, role, substr(content,1,2500) FROM messages WHERE session_id='<id>' ORDER BY id" > /tmp/prev_session_dump.txt`
- Test-infra details: `doc/plans/messaging/PROGRESS.md` (Fetch v10–v12 test-cleanup section).
