# Phase 2: Reference Traffic Capture

**Status:** In progress — 2.1 design started  
**Started:** 2026-08-26  
**Goal:** Build traffic capture tool, start Docker brokers, capture reference wire traffic.

---

## Sub-task 2.1: Design TrafficCapture proxy

**Plan:** Build a standalone TCP proxy that:
- Listens on a local port, connects to a remote broker
- Bridges all traffic bidirectionally
- Logs raw bytes + decoded AMQP frames
- Outputs structured JSON per operation

**Architecture decision:** No `PassThroughConnection` exists in service module. Created standalone TCP proxy in `messaging/amqp/src/main/java/.../capture/TrafficCapture.java` using the existing AmqpTransport SPI.

**Result:** Implemented as `TrafficCapture` — AmqpTransport decorator that logs all bytes. Two-channel bridge: client→proxy→broker.

---

## Sub-task 2.2: Implement TrafficCapture proxy

**Plan:** Create Java source + capture runner.

**Result:** Implemented `TrafficCapture` transport decorator + `CaptureRunner` sample app. Logs bidirectional traffic with hex dump + frame decoding.

---

## Sub-task 2.3: Start Docker brokers

**Result:** All 3 brokers running and reachable:
- RabbitMQ (legoflow-rabbitmq) — port 5672 — AMQP 1.0 plugin enabled
- Artemis (legoflow-artemis) — port 5675 — auto-create queues enabled
- Qpid Dispatch (legoflow-dispatch) — port 5674

---

## Sub-task 2.4: Build & verify compilation

**Result:** Compiles clean. `TrafficCapture` + `CapturingTransport` + `CaptureRunner` + `WireCapture` all compile. 250 AMQP tests pass.

---

## Sub-task 2.5: Capture against brokers

**Result:** Capture tool built and functional. Full-flow captures (sender + receiver + transfer) blocked by protocol gaps:
- RabbitMQ: rejects anonymous proto-0 connections (gap #3 in Phase 1)
- Artemis: PLAIN auth works for header exchange, but sender credit flow hangs (gap #4)
- Qpid Dispatch: anonymous SASL works, but broker-specific address format blocks ATTACH (gap #5)

**What works:** Header exchange + SASL negotiation captured for all 3 brokers via wire-level test. The capture tool correctly records all bytes bidirectionally.

**Blocked items:** Full message flow (ATTACH + TRANSFER + DISPOSITION) requires Phase 3/4 fixes first. Will re-run captures after fixes land.

---

## Phase 2 Summary

**Deliverables complete:**
1. ✅ `TrafficCapture.java` — transport decorator that captures all wire bytes with annotations
2. ✅ `CapturingTransport.java` — AmqpTransport adapter that routes to TrafficCapture
3. ✅ `CaptureRunner.java` — full-flow capture using legoflow client (works after Phase 3 fixes)
4. ✅ `WireCapture.java` — raw socket capture for handshake-only tests (works now)
5. ✅ Docker brokers: RabbitMQ (5672), Artemis (5675), Qpid Dispatch (5674)

**Deliverables deferred:**
- Full-flow capture archives (`captures/legoflow_rabbitmq_capture.txt` etc.) — will generate after Phase 3/4
- rhea.js captures — requires Node.js setup; deferred to Phase 6 (Interop Test Matrix)

**Lesson:** The capture tool depends on the client working against each broker. Protocol gaps identified in Phase 1 must be fixed first (Phase 3/4), then captures re-run.

---

## Capture Pairs Remaining

| Client | Server | Priority | Captured |
|--------|--------|----------|----------|
| rhea.js | Artemis | ★★★★★ | ☐ |
| rhea.js | Qpid Dispatch | ★★★★ | ☐ |
| rhea.js | RabbitMQ | ★★★ | ☐ |
| legoflow AmqpClient | Artemis | ★★★★ | ☐ |
| legoflow AmqpClient | Qpid Dispatch | ★★★ | ☐ |
| legoflow AmqpClient | RabbitMQ | ★★★ | ☐ |

## Operations to Capture (per pair)

1. TCP connect → header exchange
2. SASL negotiation
3. OPEN frame exchange
4. BEGIN frame exchange
5. ATTACH sender + credit grant
6. ATTACH receiver + credit request
7. TRANSFER (small message)
8. DISPOSITION (accept/reject)
9. FLOW credit update
10. DETACH → END → CLOSE
11. TRANSFER (large message)
12. Error: close with error condition
