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
- [ ] Commit Phase 1

## Phase 2 — NATS
- [ ] `NatsTransport` SPI
- [ ] `InMemoryNatsTransport`
- [ ] `PipelineNatsTransport`
- [ ] `NatsClient` off raw Socket
- [ ] `NatsServer` off ServerSocket
- [ ] Service layer drives I/O
- [ ] Codec reassembly verified/fixed
- [ ] Unit tests ≥80%
- [ ] Commit Phase 2

## Phase 3 — XMPP
- [ ] `XmppTransport` SPI
- [ ] `InMemoryXmppTransport`
- [ ] `PipelineXmppTransport`
- [ ] `XmppServer` off ServerSocket
- [ ] Service layer drives I/O
- [ ] Codec reassembly verified/fixed
- [ ] Unit tests ≥80%
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
