# AMQP 1.0 Compatibility Engineering Plan

> **Project:** lego-flow — messaging/amqp module
> **Created:** 2026-08-26
> **Status:** Draft
> **Goal:** Fix lego-flow's AMQP 1.0 client and server to interoperate with all major brokers (Artemis, RabbitMQ, Qpid Dispatch, Solace, IBM MQ) through least-restrictive defaults + vendor simulation + comprehensive test matrix.

---

## Vision

Legoflow's AMQP 1.0 implementation should work against **any compliant broker** out of the box,
and offer **vendor simulation modes** to behave like specific brokers for testing and migration.

The approach:

1. **Document** every variance between brokers (Phase 1)
2. **Capture** reference traffic for every operation with every client-server pair (Phase 2)
3. **Fix** legoflow server to least-restrictive defaults + vendor simulation (Phase 3)
4. **Fix** legoflow client for cross-broker compatibility (Phase 4)
5. **Test** own server × all clients exhaustively (Phase 5)
6. **Verify** own client × all servers exhaustively (Phase 6)

---

## Phase Tracking

Each phase has a tracking document in this directory. Sub-tasks are chained (not nested):
a sub-task is documented in its parent, executed, and the result updates the parent document.

| Phase | Status | Tracking Doc | Summary Doc |
|-------|--------|-------------|-------------|
| Phase 1: Protocol & Vendor Documentation | ☐ Not started | [phase1-protocol-variances.md](phase1-protocol-variances.md) | — |
| Phase 2: Reference Traffic Capture | ☐ Not started | [phase2-traffic-capture.md](phase2-traffic-capture.md) | — |
| Phase 3: Server Fixes + Vendor Simulation | ☐ Not started | [phase3-server-fixes.md](phase3-server-fixes.md) | — |
| Phase 4: Client Compatibility | ☐ Not started | [phase4-client-fixes.md](phase4-client-fixes.md) | — |
| Phase 5: Unit Test Matrix | ☐ Not started | [phase5-unit-tests.md](phase5-unit-tests.md) | — |
| Phase 6: Interop Test Matrix | ☐ Not started | [phase6-interop-tests.md](phase6-interop-tests.md) | — |

**How to read:** Before starting a phase, load this plan. Create the phase tracking doc.
Update it **before** each sub-task (what you plan) and **after** (what happened).
When a phase completes, write a summary doc and update this table.

---

## Reference Analysis (Pre-completed)

Before writing this plan, a full documentation analysis was completed:

- **Compatibility analysis:** [../../doc/AMQP10_COMPATIBILITY.md](../../doc/AMQP10_COMPATIBILITY.md)
- **RabbitMQ findings:** [RabbitMQ AMQP 1.0 Interop Findings](file:~/.hermes/skills/software-development/amqp-1.0-interop/references/rabbitmq-amqp10-findings.md) (in skill)

Key findings from analysis:

| Finding | Severity | Phase |
|---------|----------|-------|
| channel-max 65535 crashes Qpid Broker-J | High | Phase 3 |
| SASL PLAIN authzid handling differs | High | Phase 4 |
| OPEN frame ordering ambiguity | Medium | Phase 3 |
| Settle mode defaults too permissive | Medium | Phase 3 |
| RabbitMQ requires SASL proto-3 first | High | Phase 4 |
| RabbitMQ address format (exchange/queue) | High | Phase 4 |
| Auto-accept transfers is non-standard | Medium | Phase 3 |
| No transaction support | Low | Out of scope |

---

## Phase 1: Protocol & Vendor Documentation

**Goal:** Exhaustively document AMQP 1.0 spec requirements and every vendor's deviation.

**Input:** OASIS spec (Parts 0-5), broker documentation (Artemis, RabbitMQ, Solace, IBM MQ, Qpid), JIRA issues (QPID-*, PROTON-*).

**Deliverables:**
1. `phase1-protocol-variances.md` — Per-section spec analysis with broker comparison tables
2. `phase1-summary.md` — Condensed findings for implementation reference

**Sub-tasks (chained):**
1.1 Transport layer (Sections 2.1-2.8) — header exchange, OPEN/BEGIN/ATTACH/FLOW/TRANSFER/DISPOSITION/DETACH/END/CLOSE
1.2 SASL security layer (Sections 5.1-5.4) — mechanisms, PLAIN authzid, frame size limits
1.3 Messaging layer (Sections 2.6, 3.2) — link credit, settlement modes, message format
1.4 Vendor-specific addressing — RabbitMQ exchange/queue, Solace topic/queue, IBM MQ queue manager
1.5 Cross-reference: legoflow implementation gaps vs spec vs each broker

**Success criteria:** Every legoflow code path maps to a spec section and known broker behavior.

---

## Phase 2: Reference Traffic Capture

**Goal:** Build a reusable traffic capture tool based on `PassThroughConnection` and capture
reference wire traffic for every protocol operation with every client-server pair.

**Design:**
- Generic TCP proxy using `PassThroughConnection` (or standalone variant)
- Captures bidirectional traffic as hex + decoded frames
- Stores per-operation captures in structured format
- Sample app in `messaging/amqp/src/samples/TrafficCapture`

**Reference pairs (Docker servers only):**

| Client | Server | Priority |
|--------|--------|----------|
| rhea.js (Apache Qpid Proton-JNI) | Artemis | ★★★★★ |
| rhea.js | Qpid Dispatch Router | ★★★★ |
| rhea.js | RabbitMQ | ★★★ |
| legoflow AmqpClient | Artemis | ★★★★ |
| legoflow AmqpClient | Qpid Dispatch Router | ★★★ |
| legoflow AmqpClient | RabbitMQ | ★★★ |

**Operations to capture (per pair):**
1. TCP connect → header exchange (proto-0 only)
2. TCP connect → SASL header exchange → sasl-mechanisms → sasl-init → sasl-outcome → AMQP header
3. OPEN frame exchange (client-first, server-first)
4. BEGIN frame exchange
5. ATTACH sender + credit grant
6. ATTACH receiver + credit request
7. TRANSFER (small message)
8. DISPOSITION (accept/reject)
9. FLOW credit update
10. DETACH → END → CLOSE graceful shutdown
11. TRANSFER (large message, >max-frame)
12. Error: close with error condition

**Deliverables:**
1. `TrafficCapture` sample app (reusable, generic)
2. Captured traffic archives: `captures/{client}_{server}_{operation}.json`
3. `phase2-summary.md` — Captures obtained, missing pairs, anomalies found

---

## Phase 3: Server Fixes + Vendor Simulation

**Goal:** Fix `AmqpContainer` to:
- Match least-restrictive interpretation of spec
- Support vendor simulation modes (behave like RabbitMQ, Artemis, etc.)

**Architecture:**
```
AmqpContainer
├── AmqpContainerConfig
│   ├── mode: STANDARD | RABBITMQ | ARTEMIS | QPID_DISPATCH | IBM_MQ
│   └── vendorOverrides: Map<String, Object>  // fine-grained per-setting
├── SaslNegotiator (vendor-aware)
├── ConnectionHandler (vendor-aware OPEN handling)
└── SessionManager (vendor-aware credit/settle)
```

**Vendor simulation behaviors:**

| Setting | STANDARD (least restrictive) | RABBITMQ | ARTEMIS | QPID_DISPATCH |
|---------|------------------------------|----------|---------|---------------|
| SASL required | No | Yes | No | No |
| SASL mechanisms | PLAIN, ANONYMOUS | PLAIN, ANONYMOUS | PLAIN, ANONYMOUS, GSSAPI | ANONYMOUS |
| Proto-0 accepted | Yes | No | Yes | Yes |
| Authzid accepted | Any | Empty only | Any | Any |
| OPEN order | Client or server | Client first | Client first | Client first |
| channel-max limit | 65535 | 65535 | 65535 | 65535 |
| Settle mode default | unsettled(0) | unsettled(0) | unsettled(0) | unsettled(0) |
| Auto-accept transfers | No | No | No | No |
| Address format | Plain string | Exchange/queue path | JMS queue | Prefix-based |
| Idle timeout | 0 (off) | 60s | Configurable | 8s |
| max-frame SASL limit | None | None | None | None |

**Sub-tasks (chained):**
3.1 Refactor `AmqpContainer` to accept `ContainerConfig` with vendor mode
3.2 Fix SASL negotiation — allow empty authzid only when RabbitMQ mode; accept any otherwise
3.3 Fix OPEN frame handling — handle server-first OPEN for IBM MQ mode
3.4 Fix settle mode defaults — use unsettled(0) standard
3.5 Remove auto-accept — implement proper disposition dispatch
3.6 Add address format converters per vendor mode
3.7 Reduce default channel-max to 32767 for signed-short compatibility
3.8 Add idle timeout enforcement per vendor config
3.9 Verify each vendor mode against Phase 2 captures

---

## Phase 4: Client Compatibility

**Goal:** Fix `AmqpClient` to work against all major brokers with per-broker configuration.

**Architecture:**
```
AmqpClient
├── ClientConfig
│   ├── brokerType: AUTO | RABBITMQ | ARTEMIS | QPID_DISPATCH | SOLACE | IBM_MQ
│   └── saslConfig: SaslConfig
│       ├── mechanism: PLAIN | ANONYMOUS | EXTERNAL | AUTO
│       └── authzidPolicy: EMPTY | MATCH_AUTHCID | CUSTOM
├── ConnectionNegotiator (broker-aware)
└── SessionManager (broker-aware)
```

**Broker-specific behaviors:**

| Behavior | RABBITMQ mode | ARTEMIS mode | QPID_DISPATCH mode | AUTO mode |
|----------|-------------|-------------|-------------------|-----------|
| Header order | SASL proto-3 first | Auto-detect | ANONYMOUS SASL | Try proto-3, fallback proto-0 |
| SASL authzid | Empty | Match authcid | Empty | Empty |
| OPEN frame | Client-first | Client-first | Client-first | Client-first |
| Address format | `/queues/:queue` | `queueName` | `closest:queueName` | Plain |
| Settle mode | unsettled(0) | unsettled(0) | unsettled(0) | unsettled(0) |
| channel-max | 65535 | 32767 | 65535 | 32767 |
| Idle timeout | 0 | 0 | 0 | 0 |
| Credit model | Wait for broker | Wait for broker | Self-grant fallback | Wait, fallback |

**Sub-tasks (chained):**
4.1 Refactor `ClientConfig` with broker type detection (manual + auto)
4.2 Fix SASL header negotiation — proto-3 first for RabbitMQ, auto for others
4.3 Fix SASL authzid encoding — configurable per broker type
4.4 Fix OPEN frame ordering — handle server-first response
4.5 Fix address resolution — vendor-specific format converters
4.6 Fix settle mode negotiation — use unsettled(0) with mixed(2) fallback
4.7 Fix channel-max default — 32767 for safety
4.8 Add credit model: wait for broker FLOW, fallback to self-grant
4.9 Verify each broker mode against Phase 2 captures

---

## Phase 5: Unit Test Matrix

**Goal:** Exhaustive unit tests for own server with every client variant.

**Test matrix (own server × clients):**

| Server Mode | Client | Tests |
|------------|--------|-------|
| STANDARD | legoflow AmqpClient | Full suite |
| STANDARD | rhea.js | Full suite |
| RABBITMQ | legoflow AmqpClient (RABBITMQ mode) | Full suite |
| RABBITMQ | rhea.js | Full suite |
| ARTEMIS | legoflow AmqpClient (ARTEMIS mode) | Full suite |
| ARTEMIS | rhea.js | Full suite |
| QPID_DISPATCH | legoflow AmqpClient (QPID mode) | Full suite |
| QPID_DISPATCH | rhea.js | Full suite |

**Standard test suite (per combination):**
```
T01_Connect_Proto0
T02_Connect_SaslPlain
T03_Connect_SaslAnonymous
T04_Open_Exchange
T05_Begin_Session
T06_Attach_Sender_Credit
T07_Attach_Receiver_Credit
T08_Transfer_Small
T09_Transfer_Large
T10_Disposition_Accept
T11_Disposition_Reject
T12_Flow_CreditUpdate
T13_Detach_Link
T14_End_Session
T15_Close_Connection
T16_Close_WithError
T17_MultiMessage_Pipeline
T18_Reconnect_Resume
```

**Sub-tasks (chained):**
5.1 Design test suite framework — single test class, parameterized by server mode + client type
5.2 Implement T01-T05 (connection lifecycle)
5.3 Implement T06-T12 (message flow)
5.4 Implement T13-T18 (error/recovery)
5.5 Run matrix — own server × legoflow client
5.6 Run matrix — own server × rhea.js client
5.7 Document pass/fail matrix

---

## Phase 6: Interop Test Matrix

**Goal:** Verify legoflow client against real reference servers.

**Test matrix (reference servers × clients):**

| Server (Docker) | Client | Tests |
|-----------------|--------|-------|
| Artemis | legoflow AmqpClient (AUTO) | T01-T18 |
| Artemis | legoflow AmqpClient (ARTEMIS) | T01-T18 |
| Artemis | rhea.js | T01-T18 |
| Qpid Dispatch | legoflow AmqpClient (AUTO) | T01-T18 |
| Qpid Dispatch | legoflow AmqpClient (QPID) | T01-T18 |
| Qpid Dispatch | rhea.js | T01-T18 |
| RabbitMQ | legoflow AmqpClient (AUTO) | T01-T18 |
| RabbitMQ | legoflow AmqpClient (RABBITMQ) | T01-T18 |
| RabbitMQ | rhea.js | T01-T18 |

**Sub-tasks (chained):**
6.1 Set up Docker servers (Artemis arm64, Qpid Dispatch amd64/qemu, RabbitMQ arm64)
6.2 Run Artemis matrix
6.3 Run Qpid Dispatch matrix
6.4 Run RabbitMQ matrix
6.5 Document final interop matrix
6.6 Update COMPATIBILITY.md with verified results

---

## Artifacts

| Artifact | Location | Phase |
|----------|----------|-------|
| Master plan | `docs/plan/PLAN.md` | Now |
| Protocol variances | `docs/plan/phase1-protocol-variances.md` | Phase 1 |
| Phase 1 summary | `docs/plan/phase1-summary.md` | Phase 1 |
| Traffic capture app | `messaging/amqp/src/samples/TrafficCapture/` | Phase 2 |
| Captured traffic | `messaging/amqp/captures/` | Phase 2 |
| Phase 2 summary | `docs/plan/phase2-summary.md` | Phase 2 |
| Server vendor modes | `messaging/amqp/src/main/java/.../server/` | Phase 3 |
| Phase 3 summary | `docs/plan/phase3-summary.md` | Phase 3 |
| Client broker modes | `messaging/amqp/src/main/java/.../client/` | Phase 4 |
| Phase 4 summary | `docs/plan/phase4-summary.md` | Phase 4 |
| Unit test matrix | `messaging/amqp/src/test/java/.../matrix/` | Phase 5 |
| Phase 5 summary | `docs/plan/phase5-summary.md` | Phase 5 |
| Interop test results | `interop-tests/src/test/java/.../` | Phase 6 |
| Phase 6 summary | `docs/plan/phase6-summary.md` | Phase 6 |
| Final compatibility doc | `doc/AMQP10_COMPATIBILITY.md` | Ongoing |

---

## Workflow Rules

1. **Track before and after:** Each sub-task updates its phase doc with plan (before) and result (after)
2. **Chain, don't nest:** Sub-tasks are sequential entries in one phase doc. When a sub-task needs a new sub-sub-task, it's added as the next entry after the current one completes
3. **Phase boundary:** A phase is not complete until its summary doc is written
4. **Captures are authoritative:** When implementation disagrees with a capture, the capture wins
5. **Docker only for servers:** No local server installations
6. **Least restrictive defaults:** Standard mode accepts the widest range of inputs; vendor modes add restrictions

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Qpid Dispatch Router has no arm64 image | Phase 2, 6 blocked on Apple Silicon | Use Artemis as primary; Qpid Dispatch via CI on amd64 |
| RabbitMQ address format is proprietary | Phase 4 may need ongoing updates | Document format, abstract converter |
| Solace/IBM MQ are cloud-only | Cannot capture traffic easily | Use documentation + spec compliance |
| Legoflow code changes break existing tests | All phases | Incremental commits per sub-task |
| Capture tool produces incorrect hex | Phase 2 unreliable | Validate captures against WireShark manually |
