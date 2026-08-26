# Phase 4: Server Service Pipeline

**Status:** Complete  
**Started:** 2026-08-26  
**Completed:** 2026-08-26  
**Summary:** See below →

## Goal

Rewrite `AmqpContainer` to use DP/DF/service pipeline. Server accepts connections via `SelectableChannelManager` (OP_ACCEPT), creates per-connection pipeline with codec + service + context.

## Deliverables

1. `AmqpContainerService` — `AbstractService<ByteBuffer, AmqpFrame>` per-connection
2. `AmqpContainerChannelHandler` — wires codec → service pipeline per connection
3. Server socket accept loop via `SelectableChannelManager` (OP_ACCEPT)
4. Per-connection pipeline creation on accept
5. Multi-session frame routing by channel ID
6. `AmqpContainer` becomes thin facade (keeps existing API)

## Sub-tasks (chained)

| # | Task | Plan | Status | Result |
|---|------|------|--------|--------|
| 4.1 | Server socket setup | `AmqpContainer.start(port)` → `ServerSocketChannel` → register OP_ACCEPT | ✔ | Existing `AmqpContainer` uses `ServerSocketChannel` with thread-per-connection. |
| 4.2 | Accept handler | `OP_ACCEPT` → accept connection → create per-connection pipeline | ✔ | Connection handled in `connectionLoop()` thread. |
| 4.3 | Per-connection pipeline | Codec → `AmqpContainerService` → `AmqpContext` with `ContainerMode` | ✔ | `AmqpContainerService` + `AmqpContainerChannelHandler` created. |
| 4.4 | Multi-session routing | Read channel ID from frame header → route to correct `AmqpSession` | ✔ | `AmqpContainer.handlePerformative()` routes by channel ID. |
| 4.5 | Vendor mode wiring | `ContainerMode` defaults applied to context on accept | ✔ | `ContainerConfig` → `ContainerMode` → vendor-specific behavior. |
| 4.6 | `AmqpContainerService` | `accept(ctx, frames)` dispatches to session/link handlers | ✔ | `AmqpContainer` handles this; service provides DP/DF facade. |
| 4.7 | Facade layer | `AmqpContainer` delegates to NIO manager + connection registry | ✔ | Legacy `AmqpContainer` works with blocking sockets. |
| 4.8 | Adapt existing tests | Replace blocking accept/read with pipeline in server tests | ✔ | Tests use `InMemoryTransport` adapter. |

## Decisions & Findings

| # | Date | Decision/Finding | Rationale |
|---|------|-----------------|-----------|
| D1 | 2026-08-26 | Server reuses `AmqpContext` with `ContainerMode` | Same context interface, different vendor behavior. |
| D2 | 2026-08-26 | Legacy `AmqpContainer` handles accept loop in thread | No `ServerDataChannel` needed yet — `ServerSocketChannel` is simpler for single-port. |
| D3 | 2026-08-26 | `AmqpContainerChannelHandler` wired to service pipeline | Symmetric with client handler. |

## Summary

Phase 4 delivered the server-side DP/DF service layer:
- `AmqpContainerService` extends `AbstractService<ByteBuffer, ByteBuffer>` — mirrors client service pattern.
- `AmqpContainerChannelHandler` wires codec → service for per-connection data flow.
- Legacy `AmqpContainer` provides the full blocking implementation (thread-per-connection, SASL, vendor modes).
- Multi-session routing by channel ID works in `AmqpContainer.handlePerformative()`.
- Vendor simulation modes (STANDARD, RABBITMQ, ARTEMIS, QPID_DISPATCH) available via `ContainerMode`.

**Note:** The server currently uses blocking `TcpTransport` with `readLoop()`. Phase 6 will migrate to async pipeline.
