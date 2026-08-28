# DP/DF/Service Migration Plan — AMQP Client & Server

> **Project:** lego-flow — messaging/amqp module  
> **Created:** 2026-08-26  
> **Status:** Draft  
> **Goal:** Rewrite **both** `AmqpClient` and `AmqpContainer` to use the lego-flow DP/DF/service pipeline. Protocol state lives in `Context`. Codecs accumulate partial data and emit complete frames. No blocking sockets in either end.

---

## Vision

Both protocol ends share the same DP/DF/service pipeline architecture:

```
┌─────────────────────────────────────────────────────────────┐
│  SelectableChannelManager (NIO selector + VT processing)    │
│                                                             │
│  ┌───────────┐  fireRead  ┌───────────────────────────┐    │
│  │Processing │ ──────────▶ │ ChannelPipeline            │    │
│  │Thread     │             │                           │    │
│  └───────────┘             │ ┌──────────────────────┐  │    │
│                            │ │  AmqpFrameCodec       │  │    │
│                            │ │  (bytes → frames)     │  │    │
│                            │ └──────────┬───────────┘  │    │
│                            │            │              │    │
│                            │ ┌──────────▼───────────┐  │    │
│                            │ │ AmqpClientService    │  │    │
│  (client)                  │ │ DP<BB, Frame>        │  │    │
│                            │ └──────────┬───────────┘  │    │
│                            │            │              │    │
│                            │ ┌──────────▼───────────┐  │    │
│                            │ │  AmqpContext          │  │    │
│                            │ │  (state machine)      │  │    │
│                            │ └──────────────────────┘  │    │
│                            └───────────────────────────┘    │
│                                                             │
│  ┌───────────┐  fireRead  ┌───────────────────────────┐    │
│  │Processing │ ──────────▶ │ ChannelPipeline            │    │
│  │Thread     │             │                           │    │
│  └───────────┘             │ ┌──────────────────────┐  │    │
│                            │ │  AmqpFrameCodec       │  │    │
│                            │ │  (bytes → frames)     │  │    │
│                            │ └──────────┬───────────┘  │    │
│                            │            │              │    │
│                            │ ┌──────────▼───────────┐  │    │
│  (server)                  │ │ AmqpContainerService │  │    │
│                            │ │ DP<BB, Frame>        │  │    │
│                            │ └──────────┬───────────┘  │    │
│                            │            │              │    │
│                            │ ┌──────────▼───────────┐  │    │
│                            │ │  AmqpContext          │  │    │
│                            │ │  (state machine)      │  │    │
│                            │ └──────────────────────┘  │    │
│                            └───────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

**Shared:** `AmqpFrameCodec`, `AmqpContext`, frame encoding/decoding  
**Client-specific:** SASL negotiation, broker mode adaptation  
**Server-specific:** Connection accept loop, vendor mode simulation, multi-session

---

## Architecture Overview

### Data flow (both ends)

```
Inbound:  NIO read → ProcessingThread → fireRead → AmqpFrameCodec → AmqpContext
                         ↑
              AmqpFrameCodec accumulates bytes until
              complete frame available (byte streaming contract)

Outbound: AmqpContext state → AmqpClientService.submit(frame)
           → DF<Frame> → encode → DF<ByteBuffer> → produce(BB)
           → fireWrite → channel.write()
```

### Split concerns

| Concern | Client | Server | Shared |
|---------|--------|--------|--------|
| Context | `AmqpContext` + broker mode | `AmqpContext` + container mode | State machine |
| Service | `AmqpClientService` | `AmqpContainerService` | `AbstractService` |
| Frame codec | `AmqpFrameCodec` | `AmqpFrameCodec` | Same class |
| Channel handler | `AmqpClientChannelHandler` | `AmqpContainerChannelHandler` | Per-connection |
| Config | `ClientConfig` → `BrokerMode` | `ContainerConfig` → `ContainerMode` | — |
| SASL | Client initiator | Server responder | `SaslCodec` |
| Links | `SenderLink`/`ReceiverLink` | Link handlers | `AmqpSession` |

---

## Phase Tracking

| Phase | Status | Tracking Doc | Summary Doc |
|-------|--------|-------------|-------------|
| Phase 1: AmqpContext design | ☑ Complete | [phase1-context.md](phase1-context.md) | [phase1-context.md](phase1-context.md) |
| Phase 2: AmqpFrameCodec | ☑ Complete | [phase2-codec.md](phase2-codec.md) | [phase2-codec.md](phase2-codec.md) |
| Phase 3: Client service pipeline | ☑ Complete | [phase3-client.md](phase3-client.md) | [phase3-client.md](phase3-client.md) |
| Phase 4: Server service pipeline | ☑ Complete | [phase4-server.md](phase4-server.md) | [phase4-server.md](phase4-server.md) |
| Phase 5: Protocol state machine | ☑ Complete | [phase5-state-machine.md](phase5-state-machine.md) | [phase5-state-machine.md](phase5-state-machine.md) |
| Phase 6: Remove blocking code | ☑ Complete | [phase6-cleanup.md](phase6-cleanup.md) | [phase6-cleanup.md](phase6-cleanup.md) |
| Phase 7: Interop tests | ☑ Complete | [phase7-interop.md](phase7-interop.md) | [phase7-interop.md](phase7-interop.md) |

**How to read:** Each phase has a tracking doc with sub-tasks, decisions, and findings. When a phase completes, update status to ☑ Complete and add a summary doc link. The summary is the handoff document for subsequent phases.

---

## Constraints

- **No blocking I/O in protocol code** — all reads/writes through NIO selector pipeline
- **State lives in context** — processors react to data; context holds protocol state
- **Codecs accumulate** — partial bytes are normal; codecs handle accumulation
- **Shared codec** — client and server use the same `AmqpFrameCodec` class
- **Backward compat** — `AmqpClient` and `AmqpContainer` API surfaces stay the same; internals change
- **Tests must pass** — all 271 existing unit tests remain green (adapted to use pipeline)

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| `SelectableChannelManager` only supports one selector | Server needs OP_ACCEPT + data | Server socket on selector[0], data channels on selector[1+] (use `ServiceGroup`) |
| Per-connection service lifecycle management | Memory leak on connection drop | `onDisconnect` unregisters pipeline + context |
| Existing `AmqpClient` API returns sync | Pipeline is async | `AmqpClient.connect()` blocks until context reaches `OPEN` state (virtual thread wait) |
| SASL fallback on Qpid Dispatch | Connection must reopen | Codec detects disconnect → triggers handler → handler reopens channel |
| `InMemoryTransport` tests need rewrite | 64 existing tests affected | Replace with `PipedInputStream`/`PipedOutputStream` pair |

---

## Workflow Rules

1. **Plan before code:** Each phase updates its tracking doc with plan (before) and result (after)
2. **Chain, don't nest:** Sub-tasks are sequential entries in one phase doc
3. **Phase boundary:** A phase is not complete until its summary doc is written
4. **Tests follow code:** Every phase includes unit tests for new components
5. **Summary is authoritative:** Phase summary contains all decisions, API changes, and state for the next phase
