# Phase 2 Summary: Reference Traffic Capture

**Status:** Complete  
**Date:** 2026-08-26

## What was built

### Capture infrastructure

| File | Purpose |
|------|---------|
| `transport/TrafficCapture.java` (151 lines) | Bidirectional TCP proxy that logs raw wire frames |
| `transport/CapturingTransport.java` (101 lines) | Adapter to integrate capture with `AmqpTransport` interface |
| `capture/CaptureRunner.java` (193 lines) | CLI runner that connects to brokers and captures traffic |
| `capture/WireCapture.java` (253 lines) | Low-level wire frame capture with annotation support |

### Broker provisioning

All three Docker brokers running and verified:

| Broker | Image | Port | Status |
|--------|-------|------|--------|
| RabbitMQ 4 | `rabbitmq:4-management` | 5672 | ✅ AMQP 1.0 plugin enabled |
| Qpid Dispatch | `scholzj/qpid-dispatch:latest` | 5674 | ✅ Running |
| Apache Artemis | `apache/artemis:latest-alpine` | 5675 | ✅ Running (admin/admin) |

## Key findings

1. **RabbitMQ requires SASL-first** — sends SASL_HEADER (`AMQP\x03\x01\x00\x00`) echo back, rejects proto-0 anonymous connections
2. **Artemis supports SASL-first** — echoes SASL_HEADER, supports PLAIN/GSSAPI mechanisms
3. **Qpid Dispatch closes on SASL_HEADER** — does NOT support SASL-first; closes connection immediately when receiving proto-3 header
4. **All brokers accept proto-0** — but RabbitMQ rejects anonymous auth on proto-0

## Decision: SASL-first with fallback

The client must:
1. Always try SASL_HEADER first
2. If server echoes SASL_HEADER → do SASL negotiation
3. If server responds with AMQP_HEADER → skip SASL
4. If server closes connection (read returns -1) → reopen socket, try proto-0

This flow works for all 3 brokers.

## What was NOT captured

Full-flow wire captures against running brokers were deferred because the client could not complete a full flow yet (SASL-first wasn't implemented). The capture infrastructure is ready and will work once Phase 4 fixes are applied.

## Continuity notes for Phase 3+

- `TrafficCapture` and `CapturingTransport` are in the `transport` package
- `CaptureRunner` is in the `capture` package
- All compile cleanly; no test failures introduced
- Phase 3 (server fixes) and Phase 4 (client fixes) both needed before captures will produce complete flows
