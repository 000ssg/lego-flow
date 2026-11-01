---
name: protocol-interop-engineering
description: "Debug protocol interop: document, capture, test exhaustively"
created: 2026-08-26
---

# Protocol Interop Engineering

## Trigger

Need to make a protocol implementation interoperate with multiple reference implementations. The protocol is binary and underspecified (like AMQP 1.0, MQTT, HTTP/2, gRPC). You need a systematic approach that survives context compaction.

## Phase Tracking Workflow

### Directory structure
```
<project>/<module>/docs/plan/
├── PLAN.md                        # Master plan
├── phase1-<name>.md              # Phase 1 tracking doc
├── phase1-summary.md             # Phase 1 summary (written after completion)
└── ...
```

### Tracking document format (per phase)
```markdown
# Phase N: <Name>
**Status:** ☐ Not started → ⧖ In progress → ✅ Complete
**Started:** 2026-08-26
**Completed:** —

## Goal
One sentence: what this phase produces.

## Sub-tasks (chained)

### N.1 <Name>
**Plan:** What you will do (written BEFORE starting)
**Result:** What happened (written AFTER completion)
**Sub-tasks spawned:** — (if this task needs a new sub-step, add it here)

### N.2 <Name>
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

## References
- Phase N-1: phase(N-1)-name.md
- Source code: relative paths

## Summary doc: — (created after phase completion)
```

### Rules

1. **Track before and after:** Each sub-task updates with plan (before) and result (after)
2. **Chain, don't nest:** Sub-tasks are sequential entries in one phase doc. If a sub-task needs a new sub-step, add it as the next numbered entry after the current one completes
3. **Phase boundary:** A phase is not complete until its summary doc is written
4. **Captures are authoritative:** When implementation disagrees with a captured reference, the capture wins
5. **No local installs:** Use Docker for servers only
6. **Least restrictive defaults:** Standard mode accepts the widest range of inputs; vendor modes add restrictions

## Standard Phases

### Phase 1: Protocol and Vendor Documentation
- Document every spec section requirements
- Document every vendor deviation (broker comparison tables)
- Cross-reference: map your implementation gaps

### Phase 2: Reference Traffic Capture
- Build a generic TCP traffic capture tool (PassThroughConnection or standalone)
- Capture every operation with every client-server pair
- Store captures as JSON: hex plus decoded frames per direction
- Validate captures against WireShark manually

### Phase 3: Server Fixes and Vendor Simulation
- Fix server to least-restrictive spec interpretation
- Add vendor simulation modes (behave like specific brokers)
- Configurable per-connection or per-acceptor

### Phase 4: Client Compatibility
- Fix client for cross-vendor compatibility
- Add broker type detection (manual plus auto)
- Vendor-specific address formats, SASL behavior, etc.

### Phase 5: Unit Test Matrix
- Own server times all clients (own plus reference)
- Standard test suite (T01-T18 or protocol equivalent)
- Document pass/fail matrix

### Phase 6: Interop Test Matrix
- Reference servers (Docker) times own client plus reference clients
- Run standard test suite against each combination
- Update compatibility doc with verified results

## Artifacts per Phase

Phase 1: tracking doc, summary doc, protocol variance tables
Phase 2: tracking doc, summary doc, TrafficCapture app, captured traffic JSON
Phase 3: tracking doc, summary doc, server vendor modes
Phase 4: tracking doc, summary doc, client broker modes
Phase 5: tracking doc, summary doc, test framework, pass/fail matrix
Phase 6: tracking doc, summary doc, interop tests, verified compatibility

## Traffic Capture Design

### Generic TCP proxy
- Uses PassThroughConnection (bidirectional TCP relay with interception)
- Or standalone proxy when framework dependency is undesirable
- Captures both directions as hex byte arrays
- Decodes frames using protocol-specific decoder
- Saves per-operation captures in structured format

### Capture format
JSON with fields: pair, operation, timestamp, frames (direction, hex, decoded)

### Capture location
`<module>/captures/{client}_{server}_{operation}.json`

## Standard Test Suite

Define T01-T18 (or whatever count fits your protocol):
- T01-T05: Connection lifecycle (connect, auth, open, session begin)
- T06-T12: Data flow (attach, send, receive, acknowledge, flow control)
- T13-T18: Error/recovery (detach, end, close, error close, reconnect)

## Context Compression Survival

When context compacts:
1. Load `docs/plan/PLAN.md` — it references all tracking docs
2. Load the current phase tracking doc
3. Find the first sub-task with empty Result — that is your resume point
4. Previous results are already documented in completed sub-tasks
5. Phase summaries provide condensed context for completed phases

## References

Original AMQP 1.0 plan: `messaging/amqp/docs/plan/`
Compatibility analysis: `doc/AMQP10_COMPATIBILITY.md`
OASIS AMQP spec: https://docs.oasis-open.org/amqp/core/v1.0/os/
