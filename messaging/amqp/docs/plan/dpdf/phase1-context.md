# Phase 1: AmqpContext Design

**Status:** Complete  
**Started:** 2026-08-26  
**Completed:** 2026-08-26  
**Summary:** See below →

## Goal

Create `AmqpContext` — typed protocol state container for both client and server. Replaces generic `setAttribute`/`getAttribute` with typed getters/setters. Extends `ServiceContext`.

## Deliverables

1. `AmqpContext` interface — extends `ServiceContext` with typed protocol fields
2. `AmqpContextImpl` — concrete implementation with volatile fields
3. `ConnectionState` enum — shared protocol state machine states
4. Unit tests for context creation and state transitions

## Sub-tasks (chained)

| # | Task | Plan | Status | Result |
|---|------|------|--------|--------|
| 1.1 | Study `ServiceContext` / `DefaultServiceContext` | Understand extension points, scope hierarchy, attribute model | ✔ | `DefaultServiceContext` is concrete — can extend it. `Context.getAttribute/setAttribute` uses `ConcurrentHashMap`. Scopes provide hierarchy. Best: extend `DefaultServiceContext` for `AmqpContextImpl`, add typed volatile fields. |
| 1.2 | Define `AmqpContext` interface | Typed fields: connectionState, maxFrameSize, channelMax, sessionId, codec ref, brokerMode/containerMode, sessions map | ✔ | Interface created at `common/AmqpContext.java`. Extends `ServiceContext`. |
| 1.3 | Implement `AmqpContextImpl` | Extend `DefaultServiceContext`, add typed volatile fields, thread-safe transitions | ✔ | `AmqpCtxImpl` — CAS transitions, volatile fields, ConcurrentHashMap for sessions. |
| 1.4 | Verify context creation | Client and server context variants, field access, state transitions | ✔ | 5 tests pass: creation, valid/invalid transitions, happy path, typed fields. |

## Decisions & Findings

| # | Date | Decision/Finding | Rationale |
|---|------|-----------------|-----------|
| D1 | 2026-08-26 | Use `ServiceUser.anonymous()` not `.ANONYMOUS` | `ServiceUser` has factory methods, not static constants. |
| D2 | 2026-08-26 | `AmqpFrameCodec` stub needed in Phase 1 | Context needs a typed back-reference; full codec is Phase 2. Stub interface created. |
| D3 | 2026-08-26 | `ConnectionState` enum reused from existing code | Already has the AMQP spec state machine states. Valid transitions coded in `AmqpCtxImpl`. |
| D4 | 2026-08-26 | CAS transitions via `AtomicReferenceFieldUpdater` | Stronger than `volatile` — prevents lost state updates under concurrent VT access. |

## Summary

**Files created:**
- `common/AmqpContext.java` — interface, extends `ServiceContext`, typed getters/setters
- `common/AmqpCtxImpl.java` — implementation with volatile fields, CAS transitions, ConcurrentHashMap sessions
- `transport/AmqpFrameCodec.java` — stub interface (extends `ChannelHandler`, will be full codec in Phase 2)
- `common/AmqpCtxImplTest.java` — 5 unit tests (creation, transitions, happy path, typed fields)

**Key API:**
```java
AmqpContext ctx = new AmqpCtxImpl();
ctx.setBrokerMode(BrokerMode.RABBITMQ);
ctx.transitionTo(ConnectionState.HDR_SENT);
ctx.getMaxFrameSize(); // 4294967295 (AMQP max uint32)
ctx.getChannelMax();   // 65535 (AMQP max ushort)
```

**State machine:** Full `ConnectionState` transition table implemented. CAS ensures no lost updates under concurrent virtual thread access.

**Tests:** 5 pass, 0 fail.

## Summary (filled on completion)

See above. Phase 1 complete. Proceeds to Phase 2 (AmqpFrameCodec).
