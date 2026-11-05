# Messaging Compliance + Interop Plan

> **Branch:** `cleanup-messaging`
> **Reference pattern:** the DP/DF/service transport design proven in **MQTT, STOMP, AMQP**
> (and already compliant in **WAMP**). This plan brings **NATS, XMPP, KAFKA** to the same
> baseline, splits the interop job into 4 concurrent groups, and refines the protocol guidelines.
> **Approach:** straight, mechanical mirroring of the reference pattern — no deep redesigns.

---

## Goal

1. **Compliance** — every messaging protocol module uses the DP/DF/service pipeline with a
   byte-level **Transport SPI**; **no direct socket usage** in `src/main`; protocol core never
   imports NIO.
2. **Unit testing** — each module reaches **≥80% line coverage** and keeps its functionality
   (pub/sub, auth, transactions, ...) green.
3. **Interop** — reference-implementation tests from **trivial → complex composite** (Kafka
   multi-partition streaming + transactions; WAMP multi-realm + distributed procedure executors
   with sharding), split into **4 concurrent CI groups**.
4. **Guidelines** — fold the best findings back into `doc/PROTOCOL-GUIDELINES.md`.

## Reference pattern (what "compliant" means — from MQTT/STOMP/AMQP)

A module is **compliant** when it has **all** of:

1. **Byte-level Transport SPI** in `src/main` — e.g. `XxxTransport` with
   `send(ByteBuffer)`, `receive(ByteBuffer)`, `receiveWithTimeout(..., long, TimeUnit)`,
   `close()`, `isOpen()`. The protocol core talks **only** to this interface.
2. **`InMemoryXxxTransport`** — `createPair()` returns two connected byte-level transports
   (`BlockingQueue` per direction, no network). Drives every unit + in-process integration test.
3. **`PipelineXxxTransport`** — production transport backed by `DataChannel`, driven by
   `SelectableChannelManager`'s **selector thread**. Ring buffer on read, outbound queue on
   write; **never writes the socket from the calling thread** (the STOMP/AMQP/MQTT rule).
4. **Service layer drives I/O** — `XxxClientService` / `XxxServerService` extend
   `AbstractService`; a `ChannelHandler` bridges pipeline events → transport. **TCP lifecycle
   (open/connect/finishConnect/interestOps/close) lives in the manager, not the handler.**
5. **Codec stream reassembly** — the frame codec accumulates bytes and decodes complete frames;
   handles a frame split across N reads **and** multiple frames in one read (the STOMP lesson —
   `findFrameEnd()`-style boundary detection; never trust a single read).
6. **Test location** — unit + in-process tests in `<module>/src/test`; Docker interop only in
   `interop-tests/src/test`. No Docker-dependent test in a module's `src/test`.

## Compliance map (reconnaissance, verified against source)

| Module | Direct socket in `src/main`? | InMemory transport | Service drives I/O | Codec reassembly | Tests | Verdict |
|--------|------------------------------|--------------------|--------------------|------------------|-------|---------|
| **mqtt** | No | `InMemoryMqttTransport` | Yes (`MqttPipelineTransport`) | Yes (accumulator) | 355 | ✅ baseline |
| **stomp** | No | `InMemoryTransport` | Yes (`PipelineTransport`) | Yes (rewritten 2026-09-18) | 233 | ✅ baseline |
| **amqp** | No | `InMemoryTransport` | Yes (`PipelineTransport`) | Yes | ~640 | ✅ baseline |
| **wamp** | No (`WampTransport` SPI) | yes (demo base) | adapter (`WebSocketWampService`) | n/a (WS message boundaries) | 295 | ✅ baseline |
| **nats** | **Yes** — `NatsClient`/`NatsServer` blocking `Socket`/`ServerSocket` | **No** | **stub** (delegates to blocking socket) | check `NatsCodec` | 271 | ❌ migrate |
| **xmpp** | **Yes** — `XmppServer` `ServerSocket` | **No** | **stub** (delegates to blocking socket) | check `XmppCodec` | ~33 | ❌ migrate |
| **kafka** | **Yes** — `KafkaBroker` accept loop + `KafkaConnection` `SocketChannel` | **No** | **none** | check `KafkaCodec` (len-prefix) | 399 | ❌ migrate |

**Migrate:** nats, xmpp, kafka. **Baseline (verify, don't rebuild):** mqtt, stomp, amqp, wamp.

## Interop groups (4 concurrent CI jobs)

| Group | Tag | Contents | Reference broker (docker-compose) | Status |
|-------|-----|----------|-----------------------------------|--------|
| 1 | `interop-messaging-core` | mqtt, stomp, amqp | mosquitto, rabbitmq(STOMP), artemis | ✅ active |
| 2 | `interop-kafka` | kafka (composite tests, Phase 6) | **add** Kafka broker (KRaft, single node) | ✅ service added; tests pending Phase 6 |
| 3 | `interop-wamp` | wamp (composite tests, Phase 6) | **add** WAMP router (crossbar) | ✅ service added; tests pending Phase 6 |
| 4 | `interop-rest` | nats, xmpp, dns, ftp, http, ldap, postgresql, redis, smtp, ssh, telnet, terminal | (existing) | **disabled for now** — proper interop deferred to a later session |

Group 4 keeps all **existing** non-messaging interop tests untouched, just **re-tagged and
disabled** (no new implementation this session, per user direction). **Frozens** — the
protocol→group assignment is a frozen decision; only the infrastructure (tag, pom selection,
CI jobs, compose services) is built in Phase 5.

---

## Phases

Each phase ends in a commit (minimum one; larger work may add intermediate commits).
Sub-tasks are compact and checkable. Status lives in `PROGRESS.md`; blockers in `ISSUES.md`;
trade-offs in `DECISIONS.md`.

### Phase 0 — Repo cleanup + planning  (this commit)
- [ ] Delete `messaging/amqp-091/` — build debris only (0 `.java`, 0 build file, unreferenced).
- [ ] Add `doc/plans/messaging/` tracking: `PLAN.md`, `PROGRESS.md`, `ISSUES.md`,
      `DECISIONS.md`, `audit.md`.
- [ ] Commit `chore(messaging): drop amqp-091 debris; add compliance+interop plan`

### Phase 1 — Reference-pattern audit + baseline (docs)
- [ ] `audit.md`: full compliance map + reference-pattern definition (above).
- [ ] Verify baseline modules (mqtt/stomp/amqp/wamp) satisfy all 6 reference criteria;
      note any gap (do **not** rebuild — only record).
- [ ] Commit `docs(messaging): compliance audit + reference-pattern baseline`

### Phase 2 — NATS → reference pattern
- [ ] `NatsTransport` SPI (byte-level) in `src/main/.../nats/transport`.
- [ ] `InMemoryNatsTransport.createPair()`.
- [ ] `PipelineNatsTransport` (DataChannel + ring + outbound queue).
- [ ] Refactor `NatsClient` off raw `Socket` → `NatsTransport`.
- [ ] Refactor `NatsServer` off `ServerSocket` → manager-driven + `NatsTransport`.
- [ ] Service layer (`NatsService`, `NatsServerService`) drives I/O via `SelectableChannelManager`.
- [ ] Codec stream reassembly — verify/fix partial + multi-frame handling.
- [ ] Unit tests: transport SPI, InMemory pair, pub/sub, queue groups, jetstream; ≥80% coverage.
- [ ] Commit `refactor(nats): byte-level Transport SPI + service pipeline (no raw sockets)`

### Phase 3 — XMPP → reference pattern
- [ ] `XmppTransport` SPI (byte-level) in `src/main/.../xmpp/transport`.
- [ ] `InMemoryXmppTransport.createPair()`.
- [ ] `PipelineXmppTransport`.
- [ ] Refactor `XmppServer` off `ServerSocket` → manager-driven + `XmppTransport`.
- [ ] Service layer drives I/O via `SelectableChannelManager`.
- [ ] Codec stream reassembly — partial XML stanza / stream-header handling.
- [ ] Unit tests: transport SPI, InMemory pair, stanza routing, SASL, presence, MUC; ≥80% coverage.
- [ ] Commit `refactor(xmpp): byte-level Transport SPI + service pipeline (no raw sockets)`

### Phase 4 — KAFKA → reference pattern (largest)
- [ ] `KafkaTransport` SPI (byte-level, 4-byte length-prefix framing) in `.../kafka/transport`.
- [ ] `InMemoryKafkaTransport.createPair()`.
- [ ] `PipelineKafkaTransport`.
- [ ] Refactor `KafkaConnection` off `SocketChannel` → `KafkaTransport`.
- [ ] Refactor `KafkaBroker` accept loop → manager-driven + `KafkaTransport`.
- [ ] Codec stream reassembly — partial/fragmented length-prefixed frames.
- [ ] Unit tests: transport SPI, InMemory pair, produce/fetch, transactions, consumer groups,
      SASL, admin; ≥80% coverage. (May split into 2 commits: client path, then broker path.)
- [ ] Commit `refactor(kafka): byte-level Transport SPI + service pipeline (no raw sockets)`

### Phase 5 — Interop structure: 4 concurrent groups
- [x] JUnit `@Tag`s: `interop-messaging-core`, `interop-kafka`, `interop-wamp`, `interop-rest`.
- [x] Tag existing classes: mqtt/stomp/amqp + AMQP 1.0 wire-capture → core; nats/xmpp +
      dns/ftp/http/ldap/postgresql/redis/smtp/ssh/telnet/terminal → rest (frozens composition).
- [x] `interop-tests/pom.xml`: property-driven tag selection (`-Dinterop.group=<g>`;
      `failIfNoTests` guard via `interop-group` profile).
- [x] CI (`ci.yml`): 3 concurrent jobs (messaging-core, kafka, wamp); `interop-rest`
      **disabled** for now (no CI job).
- [x] `docker-compose.yml`: **Kafka broker** (cp-kafka 7.6.1, single-node KRaft, `CLUSTER_ID`,
      verified healthy) + **WAMP router** (crossbar, host 8081, verified healthy); AMQP
      reference brokers pinned (`rabbitmq:3.13-management`, `apache/artemis:2.57.0-alpine`, D12).
- [x] Wire-capture CI setup (core job): Artemis CLI via `docker cp` + aiormq via pip (D12);
      fixes: Artemis creds `artemis`/`guest`, scenario script on aiormq 6.x API.
- [x] Commit `test(interop): split into 4 concurrent groups (rest disabled)` (`e21cdeb1`)

### Phase 6a — Kafka codec version accuracy + structure (NEW 2026-09-23)
> **Full plan + 210-row version sub-task matrix:** [`PHASE6A_KAFKA_CODEC_VERSIONS.md`](PHASE6A_KAFKA_CODEC_VERSIONS.md)
- [ ] Spec artifact set committed (`messaging/kafka/doc/spec/` — 74 JSONs, apache/kafka 3.6.1) + plan doc.
- [ ] Foundation: `KafkaCodecPrimitives` + façade split by API sub-category (delegation-only, 416 tests stay green); `ApiKey.java` ranges corrected (5 APIs); ApiVersions negotiation in `KafkaConnection`.
- [ ] Per sub-category, **one version per sub-task**, unit tests before interop (matrix tracks each row).
- [ ] Gate: full kafka module suite green per commit; interop test stays disabled until the sub-tasks it exercises are ✓.

### Phase 6 — Kafka + WAMP composite interop tests
- [ ] **Kafka** (group 2): trivial (connect + ApiVersions + metadata) → produce/fetch →
      **multi-partition streaming** (keyed produce, per-partition offset tracking) →
      **transactions** (InitProducerId, AddPartitionsToTxn, Produce, EndTxn commit/abort).
      **Gated on Phase 6a** — the client versions the codec negotiates must be spec-accurate first.
- [ ] **WAMP** (group 3): trivial (HELLO/WELCOME + pub/sub) → **multi-realm** routing →
      **distributed procedure executors** (REGISTER/CALL across realms) → **sharding**
      (procedure distribution by key across multiple routers/realms).
- [ ] Run green against the real reference brokers; wire into groups 2 & 3.
- [ ] Commit `test(interop): kafka + wamp composite interop scenarios`

### Phase 7 — Guidelines refinement + final docs
- [ ] Refine `doc/PROTOCOL-GUIDELINES.md`: fold in the best findings — the exact Transport SPI
      shape, `createPair()` InMemory pattern, Pipeline transport rules (never write from the
      calling thread), codec stream reassembly (STOMP `findFrameEnd` lesson), service-driven
      I/O, test-location + coverage rules. Add a "messaging reference pattern" section.
- [ ] Update module docs for nats/xmpp/kafka (AGENTS.md, README, doc/ARCHITECTURE,
      doc/COMPLIANCE, doc/REQUIREMENTS).
- [ ] Append to root `doc/REQUIREMENTS.md` (append-only).
- [ ] **Full verification:** all messaging unit tests + ≥80% coverage + groups 1–3 interop green.
- [ ] Commit `docs(messaging): refine protocol guidelines + module docs`

---

## Verification gates (run before each phase commit)

- `mvn compile -DskipTests -pl '!benchmarks'` — compiles.
- `./gradlew test` (or targeted `mvn test -pl <module>`) — unit tests green.
- JaCoCo `-P jacoco-coverage` — migrated module ≥80% line coverage.
- (Phases 5–6) the affected interop group(s) run green against Docker brokers.

## Working notes

- **Straight approach:** mirror the STOMP/MQTT/AMQP files (`PipelineTransport`,
  `InMemoryTransport`, `XxxClientService`/`XxxServerService`, `XxxChannelHandler`) rather than
  inventing new structure.
- **Ordering:** smallest migration first (NATS) to shake out the pattern, then XMPP, then KAFKA.
- **Interop REST group:** re-tag only; do **not** implement new rest-protocol interop this session.
- **Never** run `git push`; commit locally only.
