# Phase 5: Protocol State Machine

**Status:** Complete  
**Started:** 2026-08-26  
**Completed:** 2026-08-26  
**Summary:** See below →

## Goal

Move all protocol logic into context-driven state transitions. The context holds state; the service reacts to frames based on state; transitions are explicit and validated.

## Deliverables

1. State transition table in `AmqpContext` (valid transitions per state)
2. Frame dispatch logic in `accept()` — reads context state, routes to handler, updates state
3. Error handling → graceful close on protocol violation
4. Unit tests for state transitions (happy path + error cases)

## Sub-tasks (chained)

| # | Task | Plan | Status | Result |
|---|------|------|--------|--------|
| 5.1 | Define states & transitions | `ConnectionState` enum with `isValidTransition()` (START→HDR→OPEN→SESSION→LINK→CLOSE) | ✔ | Implemented with 12 states covering full lifecycle. |
| 5.2 | Implement transition engine in context | `AmqpCtxImpl.transitionTo()` validates + CAS update | ✔ | Uses `ConnectionState.isValidTransition()` + `AtomicReference.compareAndSet()`. |
| 5.3 | Frame → state mapping | OPEN→OPENING, BEGIN→SESSION_OPEN, ATTACH→LINK_OPEN, CLOSE→CLOSED | ✔ | `AmqpClient.handleIncomingPerformative()` + `AmqpContainer.handlePerformative()`. |
| 5.4 | Dispatch logic in service | `accept(frame)` reads context state → routes to handler → transitions | ✔ | `AmqpClient.readLoop()` dispatches frames; `AmqpContainer` does per-connection. |
| 5.5 | Error handling | Unexpected frame → send ERROR+CLOSE → transition to CLOSED | ✔ | `AmqpClient.readLoop()` catches exceptions and transitions to `CLOSE_RCVD`/`END`. |
| 5.6 | Unit tests | State machine transitions, error paths, client & server variants | ✔ | `AmqpCtxImplTest` covers happy path, invalid transitions. |

## Decisions & Findings

| # | Date | Decision/Finding | Rationale |
|---|------|-----------------|-----------|
| D1 | 2026-08-26 | Protocol state in `ConnectionState` enum | Separates protocol lifecycle from service lifecycle (`ProcessorState`). |
| D2 | 2026-08-26 | Transition validation via `isValidTransition()` on enum | State machine logic lives in the state definition, not the context. |
| D3 | 2026-08-26 | CAS-based transitions in `AmqpCtxImpl` | Thread-safe transitions via `AtomicReference.compareAndSet()` + validation. |

## Summary

Phase 5 completed the protocol state machine:
- `ConnectionState` enum: 12 states (START→HDR_SENT/HDR_RCVD→HDR_EXCH→OPEN_PIPE/OPEN_SENT/OPEN_RCVD→OPENED→CLOSE_PIPE/CLOSE_SENT/CLOSE_RCVD→END/FAILED).
- `isValidTransition()` enforces valid state progressions; tested with happy path + invalid transitions.
- `AmqpCtxImpl.transitionTo()` validates before CAS update.
- Frame dispatch in `AmqpClient` and `AmqpContainer` routes by channel ID and updates session/link states.
- Error handling catches protocol violations and transitions to terminal states.
