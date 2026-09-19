# Messaging Compliance + Interop — DECISIONS

Trade-offs and rationale. Straight, simple choices preferred over sophisticated ones.

## D1 — Mirror the STOMP/MQTT/AMQP files, don't redesign
**Decision.** For NATS/XMPP/KAFKA, create the same four artifacts the compliant modules have —
`XxxTransport` (byte-level SPI), `InMemoryXxxTransport` (`createPair()`), `PipelineXxxTransport`
(DataChannel + ring + outbound queue), and a service layer driven by `SelectableChannelManager` —
then point the existing protocol core at the SPI.
**Why.** The user asked for *consistency with the guidelines/principles already elaborated in
MQTT/STOMP/AMQP*. Re-using the proven shape keeps review simple and diff small. Avoid inventing a
new abstraction.

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
