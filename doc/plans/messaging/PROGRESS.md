# Messaging Compliance + Interop — PROGRESS

Live checklist. Keep straight: mark `[x]` when the sub-task is done and verified.
(See `PLAN.md` for what each task means, `ISSUES.md` for blockers, `DECISIONS.md` for trade-offs.)

## Phase 0 — Repo cleanup + planning
- [x] Recon: compliance map for all 7 messaging modules
- [x] Plan docs (`doc/plans/messaging/PLAN.md`) + tracking files
- [x] Delete `messaging/amqp-091/` build debris
- [x] Commit Phase 0 (`b0489992`)

## Phase 1 — Reference-pattern audit + baseline
- [x] `audit.md` full compliance map + reference-pattern definition
- [x] Verify baseline modules (mqtt/stomp/amqp/wamp) satisfy all 6 reference criteria
- [x] Commit Phase 1 (`6a588918`)

## Phase 2 — NATS
- [x] `NatsTransport` SPI (byte-level, `receiveWithTimeout`; timeout ≠ EOF)
- [x] `InMemoryNatsTransport` (`createPair()`)
- [x] `PipelineNatsTransport` (DataChannel ring + outbound queue, selector-thread driven)
- [x] `NatsClient` off raw Socket (transport-injected; handshake over `NatsTransport`)
- [x] `NatsServer` off ServerSocket (headless; `handleConnection(NatsTransport)` seam)
- [x] Service layer drives I/O (`NatsService`/`NatsServerService` + manager; zero sockets in core)
- [x] Codec reassembly verified/fixed (reassembly tests green in the 343-test run)
- [x] Loopback test files migrated to in-memory seam (5 files); service integration test added
- [x] Demos + interop compile against the refactored API (in-memory seam for demos; real-TCP service layer for interop)
- [x] Full NATS suite re-run green + coverage ≥80% (JaCoCo) — 343 tests, 0 failures; instruction coverage 85.1%
- [x] Commit Phase 2

## Phase 3 — XMPP
- [x] `XmppTransport` SPI
- [x] `InMemoryXmppTransport`
- [x] `PipelineXmppTransport`
- [x] `XmppServer` off ServerSocket
- [x] Service layer drives I/O
- [x] Codec reassembly verified/fixed
- [x] Unit tests ≥80%
- [ ] Commit Phase 3

## Phase 4 — KAFKA
- [ ] `KafkaTransport` SPI
- [ ] `InMemoryKafkaTransport`
- [ ] `PipelineKafkaTransport`
- [ ] `KafkaConnection` off SocketChannel
- [ ] `KafkaBroker` off accept loop
- [ ] Codec reassembly verified/fixed
- [ ] Unit tests ≥80% (client path, then broker path)
- [ ] Commit Phase 4

## Phase 5 — Interop structure: 4 groups
- [ ] JUnit tags for 4 groups
- [ ] Tag existing interop classes
- [ ] `interop-tests/pom.xml` tag selection
- [ ] CI: 4 concurrent jobs (rest disabled)
- [ ] docker-compose: add Kafka + WAMP brokers
- [ ] Commit Phase 5

## Phase 6 — Kafka + WAMP composite interop
- [ ] Kafka: trivial → produce/fetch → multi-partition streaming → transactions
- [ ] WAMP: trivial → multi-realm → distributed procedure executors → sharding
- [ ] Green vs real brokers
- [ ] Commit Phase 6

## Phase 7 — Guidelines + final docs
- [ ] Refine `doc/PROTOCOL-GUIDELINES.md` (messaging reference pattern section)
- [ ] Module docs for nats/xmpp/kafka
- [ ] Root `doc/REQUIREMENTS.md` append
- [ ] Full verification (unit + coverage + interop groups 1–3)
- [ ] Commit Phase 7

## Log
- 2026-09-18 — Plan written; compliance map done (kafka/nats/xmpp violate; mqtt/stomp/amqp/wamp baseline).
- 2026-09-19 — Phase 1 committed (`6a588918`). Phase 2 main-code refactor: headless core + `PipelineNatsTransport` + manager-driven services (no sockets in core/protocol; earlier raw-socket `SocketNatsTransport`/`NatsAcceptor` draft removed, D7 corrected). Test migration to in-memory seam + TCP integration test in progress.
- 2026-09-20 — Phase 2 test migration complete: all 5 legacy loopback test files on the in-memory seam; fixed an `InMemoryNatsTransport` race where queued `-ERR` was dropped on close (D8); full NATS module suite green (343 tests). Discovered the `demos` module + NATS interop test still referenced the removed socket API (a stale `~/.m2` jar had masked the break) — migrated demos to the in-memory seam and interop to the real-TCP service layer (D9).
- 2026-09-20 — Phase 2 complete + committed: demos/interop compile verified; full suite re-run 343/343 green; JaCoCo instruction coverage 85.1% (≥80% gate); module docs (README/ARCHITECTURE/COMPLIANCE/REQUIREMENTS) updated to the headless architecture; root `doc/REQUIREMENTS.md` entry appended. Next: Phase 3 (XMPP).
- 2026-09-21 — Phase 3 (XMPP) implementation complete, pending commit: `XmppTransport` SPI + `InMemoryXmppTransport`/`PipelineXmppTransport`; `XmppServer` off `ServerSocket` (per-connection `handleConnection(XmppTransport)` + non-blocking read loop); `XmppClient` transport-injected ctor + virtual-thread read loop + `flushOutbound` (stream stanza-listener registration fixed, outbound queue made concurrent); service layer (`XmppClientService`/`XmppServerService` + channel handlers) mirrors NATS via `SelectableChannelManager`; codec reassembly verified at transport level (partial reads requeued); full module suite **283 green** (was 268); JaCoCo instruction coverage **81.7%** (≥80% gate); demos + interop compile clean (legacy in-memory API preserved, demo suite 30/30); module docs updated to the headless architecture.
