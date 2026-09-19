# Messaging Compliance + Interop — ISSUES

Blockers and open questions. Each entry: **date**, **what**, **status**, **next step**.

- 2026-09-18 · **Kafka has no service layer at all** — `KafkaBroker` runs its own `ServerSocketChannel`
  accept loop; `KafkaConnection` opens a raw `SocketChannel`. No `KafkaTransport` SPI, no
  `InMemoryKafkaTransport`, no `PipelineKafkaTransport`. **Largest migration.** _status: open_ →
  Phase 4. Next: extract a byte-level `KafkaTransport` SPI first, then wire broker+client to it.
- 2026-09-18 · **NATS/XMPP "service" classes are stubs** — `NatsService`/`XmppServerService`
  delegate I/O to the old blocking `NatsClient`/`XmppServer` socket objects; they do not drive
  I/O through `SelectableChannelManager` yet. _status: open_ → Phases 2–3.
- 2026-09-18 · **No InMemory transport in nats/xmpp/kafka** — 80% coverage depends on adding one
  per module (mirrors `InMemoryTransport` from stomp/amqp). _status: open_ → Phases 2–4.
- 2026-09-18 · **Interop "rest" group** — user deferred implementing proper rest-protocol interop
  to a later session. This session only re-tags existing classes into `interop-rest` and **disables**
  the CI group. _status: resolved by scope decision_ (see DECISIONS.md D2).
- 2026-09-18 · **amqp-091 debris** — `messaging/amqp-091/` is 692K of build artifacts (0 `.java`,
  0 build file, unreferenced in any pom/settings). _status: resolved_ → delete in Phase 0.
- (pending) **Kafka/WAMP reference brokers for docker-compose** — need to pick images
  (e.g. `bitnami/kafka`, `crossbario/crossbar` / `wampserver`). _status: open_ → Phase 5.
