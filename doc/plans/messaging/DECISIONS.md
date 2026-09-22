# Messaging Compliance + Interop — DECISIONS

Trade-offs and rationale. Straight, simple choices preferred over sophisticated ones.

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

## D12 — Pin interop broker images; wire-capture reference clients are CI-provisioned, not committed
**Decision.** The pinned broker images live in the per-group compose files (D13):
`docker-compose.core.yml` pins `rabbitmq:3.13-management` and
`apache/artemis:2.57.0-alpine` (the AMQP 1.0 reference brokers). The AIoRMQ wire-capture
scenario (`amqp_capture_scenario.py`) is written for the aiormq 6.x API; the Artemis CLI is
copied out of the artemis container (`docker cp artemis-test:/opt/artemis ...`) — in CI the
messaging-core job does both (pip + docker cp) as a setup step. The capture `.txt` files
stay committed as the wire-format reference baseline.
**Why.** `rabbitmq:4-management` floats to 4.x, which rejects the `transient_nonexcl_queues`
feature aiormq's auto-delete queues need (`channel.close()` → INTERNAL_ERROR), and
`artemis:latest-alpine` moves the CLI/protocol features out from under the captures — both
broke the wire-capture tests on a fresh CI runner. Pinning the broker versions (the things
the captured bytes were taken against) makes the interop jobs reproducible; provisioning the
external clients in CI keeps the repo free of a ~200 MB CLI blob and version-locks the CLI
to the broker image. The `Artemis CLI` was already a documented manual pre-step
(`docker cp` in the test's error message); CI just automates it.
**Consequence.** aiormq is pinned to `>=6,<7` in the CI setup step; the scenario script must
be updated if the 7.x line ever becomes default. `guest`/`guest` is NOT a valid Artemis
credential — the entrypoint creates a single user from `ARTEMIS_USER=artemis`, so the
wire-capture test uses `artemis`/`guest` (matching `AmqpInteropTest`).

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
