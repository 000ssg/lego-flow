# Phase 2: AmqpFrameCodec

**Status:** Complete  
**Started:** 2026-08-26  
**Completed:** 2026-08-26  
**Summary:** See below →

## Goal

Shared `ChannelHandler` that accumulates raw bytes from NIO reads and emits complete AMQP frames. Implements the byte streaming contract: transport delivers chunks, codec accumulates and extracts.

## Deliverables

1. `AmqpFrameCodec` — stateful `ChannelHandler` with per-connection byte accumulators
2. Encoder support: frame → ByteBuffer for outbound writes
3. Unit tests: partial frames, multi-frame-per-read, frame spanning reads

## Sub-tasks (chained)

| # | Task | Plan | Status | Result |
|---|------|------|--------|--------|
| 2.1 | Design accumulator buffer | `byte[]` with length tracking, expand-on-put, shift-on-extract | ✔ | Simplified from `ByteBuffer` to `byte[]` — avoids compact/flip/limit complexity. |
| 2.2 | Implement `onRead` handler | Drain → accumulate → loop extract frames → fire | ✔ | `onRead` calls `accumulator.put()`, then loops `extractCompleteFrame()` until null. |
| 2.3 | Implement `onWrite` handler | Empty — service layer handles outbound | ✔ | Pipeline compatibility stub. |
| 2.4 | Implement `onDisconnect`/`onError` | Reset accumulator | ✔ | Clears state on disconnect/error. |
| 2.5 | Unit tests | Partial data, multi-frame, spanning reads, reset | ✔ | 5 tests pass. |

## Decisions & Findings

| # | Date | Decision/Finding | Rationale |
|---|------|-----------------|-----------|
| D1 | 2026-08-26 | `byte[]` accumulator instead of `ByteBuffer` | `ByteBuffer` compact/flip/limit semantics are error-prone for accumulation. `byte[]` + `length` is straightforward and correct. |
| D2 | 2026-08-26 | Copy frame to new buffer on extract | Avoids shared-buffer pitfalls. Each frame gets its own `ByteBuffer.wrap()` — safe for downstream handlers. |
| D3 | 2026-08-26 | 16 MB hard cap on accumulator | Prevents OOM on malformed streams. Throws `IllegalArgumentException` if exceeded. |
| D4 | 2026-08-26 | `FrameExtractor` as functional interface | Allows test injection and pipeline wiring without coupling to `ChannelPipeline`. |

## Summary

**Files created:**
- `transport/AmqpFrameCodecImpl.java` — stateful codec with `FrameAccumulator` (byte[] based)
- `transport/AmqpFrameCodecImplTest.java` — 5 tests: single frame, partial accumulation, multi-frame, spanning reads, reset

**Byte streaming contract verified:**
- Single read → complete frame: ✅
- Partial read → next read completes frame: ✅
- Single read → 2 complete frames: ✅
- Frame spanning 3 reads: ✅
- Reset on disconnect clears accumulator: ✅

**Tests:** 5 pass, 0 fail.

## Summary (filled on completion)

—
