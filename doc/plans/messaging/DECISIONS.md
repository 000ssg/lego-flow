# Messaging Compliance + Interop — DECISIONS

Trade-offs and rationale. Straight, simple choices preferred over sophisticated ones.

## D16 — Kafka codec methodology: spec-first, one version per sub-task, unit tests before interop, sub-category split (2026-09-23)
**Decision.** Phase 6a inserts a dedicated phase before Kafka interop (Phase 6) with four hard rules,
recorded in `PHASE6A_KAFKA_CODEC_VERSIONS.md` (210-row version sub-task matrix):
1. **Spec-first** — `messaging/kafka/doc/spec/message/*.json` (apache/kafka 3.6.1, complete 74-file
   set) is the *only* source of truth for wire layouts. Live-broker observation is a **check**, never
   a source: when a broker rejects/accepts something unexpectedly, investigate the spec
   interpretation — never rewrite a layout to match observed behavior.
2. **One API version per sub-task** — exactly one version's encode/decode + unit tests per sub-task,
   v0 → latest in order; a sub-task never touches another version's code path.
3. **Unit tests before interop** — each sub-task commits with round-trip + spec-conformance unit
   tests; `KafkaKRaftInteropTest` runs only after the sub-tasks it exercises are green.
4. **Sub-category split** — the 2,670-line `KafkaCodec` monolith becomes `KafkaCodec` (static
   façade, so existing tests compile unchanged) delegating to per-sub-category classes
   (`ProduceCodec`, `FetchCodec`, `Admin`/`Group`/`Txn`/… grouped by API sub-category) +
   `KafkaCodecPrimitives`; mirror-image test classes per sub-category replace the monolithic
   `KafkaCodecTest`.
**Mechanism for implementing the next version** (chosen as part of each sub-task, recorded in its
commit message): (a) new dedicated methods when the layout diverges from the previous version, (b)
parameterize the previous version's methods when only nullability/optional fields changed. Version
dispatch is a per-API version→handler table filled as sub-tasks land; an unimplemented version
throws `CodecNotImplementedException` (never a silent fall-through).
**Why.** The 2026-09-23 WIP failed all three old implicit rules at once: it was red (4 unit-test
errors uncommitted), it "fixed" CreateTopics v0 from a live-broker rejection instead of the schema
(the schema says v0 = `name, numPartitions int32, replicationFactor int16, []assignments, []configs,
timeoutMs int32` — the WIP changed the type and *deleted* two fields), and it mixed the Produce v0
layout with a v3 version bump. Splitting into version-scoped sub-tasks with spec deltas as the
checklist makes "don't go back to finished work" structural, and keeps each step small enough to
verify independently.
**Consequence.** Phase 6 (Kafka interop) is gated on Phase 6a for the sub-tasks the interop test
exercises; its scope (trivial → produce/fetch → multi-partition streaming → transactions) and the
WAMP half are unchanged. The WIP diff is preserved verbatim in `kafka-wip-2026-09-23.patch` as the
reference for what *not* to do (its interop-test skeleton is kept and will be re-based on 6a).

## D15 — Preserve the Phase 6 WIP as a patch, revert broken sources to the green baseline
**Decision.** The uncommitted 2026-09-23 diff (`KafkaCodec.java` + `KafkaProducer.java` +
`interop-tests/pom.xml` + untracked interop test + spec JSONs) was split: the two broken source
files reverted to the last committed green state (416 tests, 0 failures); the whole diff preserved
as `doc/plans/messaging/kafka-wip-2026-09-23.patch`; the spec JSON set, the interop-test skeleton,
and the pom additions kept in-tree (untracked) for Phase 6a/6 to build on. `.specdump.py` (throwaway
scratch) deleted.
**Why.** The WIP contained two real findings (broker rejects the committed CreateTopics v0 layout;
Produce must be sent at a v3+ shape) that are now re-derived *from the spec* in 6a — but the
implementation was spec-wrong and red, so it cannot be built on. Nothing is lost: the patch is the
record, the spec set supersedes it.
**Consequence.** `git status` is clean of source modifications; only the plan docs, the spec set,
the interop skeleton, and the pom changes remain in the tree.

## D13 — Docker compose is split per interop group: one file per group, no shared instances
**Decision.** The single `interop-tests/docker-compose.yml` (all 5 services in one file,
subset selected at `docker compose up -d <services>`) is replaced by **one compose file per
interop group**: `docker-compose.core.yml` (artemis, rabbitmq, mosquitto),
`docker-compose.kafka.yml` (kafka), `docker-compose.wamp.yml` (crossbar). Each CI job does
`docker compose -f <its file> up -d / ps / down` and health-checks exactly the containers in
that file. No service appears in more than one group file; the service sets are disjoint and
their union is exactly the old 5 services (nothing moved, nothing gained).
**Why.** User requirement: groups must not intersect or mix — a job's runtime scope is
**only** its group's services, owned and torn down by that job. Service-subset selection from
a shared file still risks cross-group instances on a host (leftovers from another job,
port collisions, entangled state); a per-group file makes isolation structural, not
disciplined.
**Consequence.** The `interop-rest` group, when its CI job is enabled, gets its **own** file
(nginx, redis, postgres, nats, xmpp, openldap, smtp, ftp, sshd, telnet, dns — none of which
are in any active file today). `docker-compose.yml` is deleted; all docs and the `ci.yml`
matrix reference the per-group files (`matrix.file` replaces `matrix.services`).

## D12 — Pin interop broker images in the per-group compose files
**Decision.** The per-group compose files (D13) pin `rabbitmq:3.13-management` and
`apache/artemis:2.57.0-alpine` in `docker-compose.core.yml`.
**Why.** The interop jobs run these brokers on a fresh CI runner; a floating
`latest`/`4.x` tag changed plugin behavior between runs (RabbitMQ 4.x changed
the AMQP 1.0/STOMP plugin surface) and moved the brokers' wire behavior out
from under the tests. Pinning the exact versions keeps the interop jobs
reproducible. The original pinning rationale also covered the AMQP wire-capture
tests; those were removed (D14), but the pins stay — `AmqpInteropTest`
(artemis:5675) and `StompInteropTest` (rabbitmq:61613) run against these images.
**Consequence.** Bumping a broker image is a deliberate act: run the full
messaging-core group against the new image before changing the pin.

## D14 — AMQP wire-capture tests removed: diagnostic recorders, not tests
**Decision.** `Amqp10WireCaptureTest` (Artemis CLI) and `AmqpWireCaptureTest`
(aiormq), their scenario scripts, the captured `.txt` baselines, the
`artemis-cli` gitignore carve-out and the CI wire-capture setup step (docker cp
+ pip) are all removed (2026-09-22).
**Why.** Both classes contain **zero assertions**: they proxy an external
reference client and dump hex to a file — they never exercised lego-flow's AMQP
code. They were created to diagnose proto-3/SASL/flow-frame bugs; those fixes
are asserted by `AmqpInteropTest` (6 tests, artemis:5675) and the in-module
fragmentation tests (which inline the captured bytes as literals). Removing
them loses no coverage; the core group goes 19 → 15 tests.
**Consequence.** The wire-format reference bytes no longer live in the repo. If
a future interop bug needs byte-level diagnosis, re-capture ad hoc with
`PassThroughConnection` + `WireCaptureInterceptor` (both stay in
`service/.../passthrough` with their own unit test — general-purpose, reusable
for Kafka/WAMP interop work in Phase 6).

## D11 — Kafka client migrates to transport-injection (drop the host/port ctor), same as NATS (D7/D9)
**Decision.** The Phase 4 spec said both "keep legacy host/port `KafkaConnection` ctor" (§5) and
"no host/port constructor in `KafkaConnection`" (§6). Contradiction resolved in favour of the
compliance rule: the protocol core is **headless**, so `KafkaConnection` becomes
`KafkaConnection(KafkaTransport, clientId)` only — no `java.net`, no `SocketChannel`. The three
public clients (`KafkaProducer`/`KafkaConsumer`/`KafkaAdminClient`) take a `KafkaTransport` instead
of `(host, port)`. Real TCP moves to a new service layer (`KafkaService` client + `KafkaBrokerService`,
`SelectableChannelManager`-driven, mirroring `NatsService`/`NatsServerService`).
**Consequence.** The `demos` + client/broker unit tests that called the host/port ctors no longer
compile — they migrate to the **in-memory seam** (`InMemoryKafkaTransport.createPair()` +
`broker.handleConnection(...)` + client over the pair), exactly the NATS `InMemoryNats`/`InMemoryKafka`
fixture pattern (D9). ~36 client tests + 5 raw-socket broker tests migrate; the other ~350 (broker
in-process, codec, common, record, protocol) are untouched. The service integration test
(`KafkaServiceIntegrationTest`) proves the real-TCP manager path, mirroring `NatsServiceIntegrationTest`.
**Why.** "No direct socket in implementation" is the actual rule being enforced; a raw-socket legacy
ctor in the client core would leave the exact violation this phase removes. Follows the proven
NATS/XMPP shape so review stays simple. Public demo signatures are preserved via the in-memory seam.
**Note.** k1 (transport SPI trio) and the client-side `KafkaConnection` rewrite are done; broker
headless + clients + tests + service layer follow.

## D8 — In-memory transport must drain queued data before EOF (auth-rejection race)
**Decision.** `InMemoryNatsTransport.receiveWithTimeout` returned -1 (EOF) immediately on
`!open`, **discarding bytes still queued** (e.g. the server's `-ERR` after an auth failure).
The client then saw EOF instead of `-ERR`, so a rejected `connect()` could silently "succeed" —
a nondeterministic race (the server may close before the client reads). Fixed by draining the
outbound queue when the peer closes, mirroring real-socket semantics (buffered data is delivered
before EOF). `NatsClient.connect()` already treats only `-ERR` as rejection; the transport now
guarantees the `-ERR` arrives.
**Why.** Auth rejection (token / user-pass) must be deterministic and must surface as
`IOException` to the caller, not a false "connected". Verified: `NatsServerTest` 17/17 across
repeated runs; `NatsTransportTest` 17/17 (no regression).

## D9 — Demos migrate to the in-memory seam; interop migrates to the real-TCP service layer
**Decision.** The headless refactor removed `NatsServer(port)`, `start(int)`, `server.port()`, and
`NatsClient(host, port, ...)` — which the `demos` module and the NATS interop test still called, so
they no longer compiled (the Phase 2 gate `mvn compile -pl '!benchmarks'` builds both). The 6 NATS
demos move to the **in-memory transport seam** (`InMemoryNatsTransport.createPair()` +
`server.handleConnection` + `NatsClient(pair)`) — the compliant MQTT reference-demo pattern; main-scoped,
deterministic, no sockets, and it preserves each demo's public signature + results. The NATS interop
test (whose purpose is a **real external broker**) moves to the **real-TCP service layer**
(`NatsService` + `SelectableChannelManager`, the verified `NatsServiceIntegrationTest` wiring).
**Why.** Demos should run anywhere without an external broker (in-memory), while interop is *specifically*
for external brokers and must keep real TCP. `JetStreamDemo` is server-side-only and needs only
`new NatsServer(); start()`. Public signatures are preserved so the demo tests stay unchanged.

## D1 — Mirror the STOMP/MQTT/AMQP files, don't redesign
**Decision.** For NATS/XMPP/KAFKA, create the same artifacts the compliant modules have —
`XxxTransport` (byte-level SPI), `InMemoryXxxTransport` (`createPair()`), a production transport,
and a service layer driven by `SelectableChannelManager` — then point the existing protocol core at the SPI.
**Why.** The user asked for *consistency with the guidelines/principles already elaborated in
MQTT/STOMP/AMQP*. Re-using the proven shape keeps review simple and diff small. Avoid inventing a
new abstraction.

## D7 — NATS is line/text-framed: route through the byte SPI with a stream adapter; production path is manager-driven (zero sockets in the core)
**Decision.** NATS frames are text lines, so instead of rewriting the proven line-based `NatsCodec`
(reassembly via `BufferedReader` is sound), route it through the new byte-level `NatsTransport` SPI:
a stream adapter (`TransportStreams`) adapts `NatsTransport` (byte) to the `InputStream`/`OutputStream`
the codec already uses. **The protocol core is headless** — `NatsServer` has no accept loop and no
`port()`; connections arrive via `NatsServer.handleConnection(NatsTransport)` (mirrors
`StompBroker.accept(StompTransport)`), and `NatsClient(NatsTransport, ...)` does the handshake over the
injected transport. The production transport is `PipelineNatsTransport` (a `DataChannel` ring + outbound
queue, driven by the `SelectableChannelManager` selector thread — the STOMP `PipelineTransport` reference
form). **All socket lifecycle lives in the service layer** (`NatsService`/`NatsServerService` open
non-blocking `SocketChannel`/`ServerSocketChannel`, register with the manager, wire the channel handler
to the transport) — so `src/main` has zero raw sockets and the protocol core never imports NIO.
(An earlier draft used a virtual-thread `SocketNatsTransport`/`NatsAcceptor` in `src/main`; that was
rejected — it left blocking sockets in the implementation. Superseded by this manager-driven form.)
**Why.** Satisfies "no direct socket in implementation" (the real rule), keeps NATS's 271 working tests +
protocol logic intact (low risk), and matches the proven STOMP/MQTT/AMQP shape the user asked for. Tests
drive the core over `InMemoryNatsTransport` (no sockets); a service integration test proves the real
manager/TCP path. Straight, not a deep rewrite.

## D2 — Interop "rest" group: re-tag + disable, do NOT implement
**Decision.** Group 4 (`interop-rest`) contains all **existing** non-messaging interop tests
(nats, xmpp, dns, ftp, http, ldap, postgresql, redis, smtp, ssh, telnet, terminal). This session
only assigns them the tag and **disables** the CI job. No new rest-protocol interop is written.
**Why.** User direction: work on proper non-messaging interop **later**, after messaging is done.
Keep them present (not dropped) so nothing is lost.

## D3 — WAMP interop uses a WAMP router, not a real broker cluster
**Decision.** For the WAMP composite interop (group 3) run against a **WAMP router** (e.g.
Crossbar) for multi-realm + distributed procedure executors. "Sharding" is expressed as multiple
realms/routers with procedure registration distribution by key.
**Why.** WAMP has no "broker/partition" concept like Kafka; the spec's equivalents are **realms**
(isolated routing domains) and **dealer** (RPC). Multi-realm + sharded procedure executors map
cleanly onto a router.

## D4 — Kafka interop: trivial → composite, in one broker
**Decision.** Kafka group 2 tests go from trivial (connect + ApiVersions + metadata) up to
**multi-partition streaming** and **transactions**, all against a single Kafka broker (KRaft,
no zookeeper).
**Why.** A single KRaft broker supports multi-partition topics and the full transaction API; no
cluster needed. Keeps docker-compose simple.

## D5 — Coverage gate = 80% line coverage per migrated module
**Decision.** NATS, XMPP, KAFKA must each reach ≥80% line coverage (JaCoCo `-P jacoco-coverage`)
with all functionality green.
**Why.** User requirement; matches the project's existing coverage-threshold convention.

## D6 — Order: NATS → XMPP → KAFKA
**Decision.** Migrate smallest first.
**Why.** NATS (271 tests, simple pub/sub) shakes out the pattern with low risk; KAFKA (399 tests,
37 API types, no service layer) is last so the established pattern is proven twice before the
biggest job.
