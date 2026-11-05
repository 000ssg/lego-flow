# Artemis Interop Findings (Verified 2026-09-09, DO NOT SCAN AGAIN)

## Wire Sequence on Port 5675
1. TCP connect completes
2. Client sends AMQP_HEADER (8 bytes: `\0\0\0\0 AMQP`)
3. Artemis sends its 0-9-1 greeting first (8 bytes: "AMQP 3.1.0.0")
4. Artemis echoes AMQP_HEADER (8 bytes: `\0\0\0\0 AMQP`)
5. Client reads 0-9-1 greeting (skip 8 bytes), then reads echo
6. Client sends OPEN performative → Artemis responds with OPEN
7. SASL is skipped in proto-0 mode (Artemis accepts anonymous connections)

## ClientConfig.PID
Artemis uses BrokerMode.ARTEMIS which sets `proto0Accepted=true`, skipping SASL entirely.

## False EOF Root Cause (Resolved)
The `processReadable()` was dispatched to `PROCESSING_POOL` as async. Multiple OP_READ events fired simultaneously, multiple threads read from the same non-blocking socket. One thread read -1 (false EOF) because the socket had no data at that moment, and the other thread's read consumed partial data. Fix: read from socket in selector thread synchronously in `dispatchKey()`.

## CancelledKeyException Root Cause (Resolved)
OP_CONNECT was dispatched async to `connectionPool`. By the time the virtual thread ran `processConnectable()`, the selector thread had already cancelled the key. Fix: handle OP_CONNECT synchronously in `dispatchKey()` — `finishConnect()`, change interest ops, fire event. All before the next `select()` call.

## Two Pipeline Bug (Resolved)
`ServiceContext.registerChannel()` was creating a `ChannelPipeline(handler)` then calling `mgr.registerChannel()` which created a second empty `ChannelPipeline()`. Selector dispatched to the empty pipeline. Fix: `ServiceContext.registerChannel()` now delegates to `mgr.registerChannel(service, channel, handler)` which creates one pipeline with the handler.
