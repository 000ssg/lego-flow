# AMQP 1.0 Client: Lessons from RabbitMQ Interop (August 2026)

## Single Synchronous Reader Architecture (User-Mandated)

**Rule:** After TCP connect, use ONE `ChannelHandler` for both protocol handshake and ongoing data flow. No background `readLoop`.

### Why no background readLoop?
A background reader (e.g., `readLoop()` on an executor) races with synchronous `readFrame()` calls made by handshake code and `receiver.receive()`. Both compete for frames on the ring buffer. The background thread steals frames meant for the caller, causing:
- Handshake failures (expected frames consumed by background thread)
- Receiver timeouts (broker responses eaten before `receive()` polls)
- Platform-specific non-determinism (virtual thread scheduling variance)

### Correct flow
1. Socket channel registers for `OP_CONNECT` only
2. On connect, `onConnect()` enables `OP_READ|OP_WRITE` with the SAME handler
3. Protocol handshake runs synchronously on the calling thread via `readFrame()`/`pollFrame()`
4. User calls `receiver.receive()` → actively polls frames via `client.pollFrame()`
5. Single path for all frame consumption — state machine drives everything

## AMQP 1.0 `session.send()` Silent Drop Bug

`AmqpSession.send()` checks `if (frameSender != null)` and silently does nothing when `frameSender` is null. Links that send FLOW, DISPOSITION, etc. through `session.send()` appear to work but frames never reach the transport.

**Fix:** Use `client.sendPerformative(channel, performative)` from link code when a client reference is available. Make `sendPerformative` public/package-private rather than private.

## `pollFrame()` Single Deadline for Multi-Part Reads

When reading a frame header (4 bytes) then body (N bytes) from transport, compute ONE deadline at the start. Pass remaining time (`deadline - now`) to each `receiveWithTimeout()` call. Calling `receiveWithTimeout(buf, fullTimeout)` for both header and body can exhaust the timeout on the header read, leaving zero time for the body even though data is available.

```java
long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
// Header
remainingMs = deadline - System.currentTimeMillis();
transport.receiveWithTimeout(sizeBuf, remainingMs, TimeUnit.MILLISECONDS);
// Body — uses same deadline, not a new full timeout
remainingMs = deadline - System.currentTimeMillis();
transport.receiveWithTimeout(body, remainingMs, TimeUnit.MILLISECONDS);
```

## AMQP 1.0 FLOW/Credit Flow

Receiver link MUST send FLOW with credit before the broker delivers messages. After `createReceiver()`:
1. `issueCredit(N)` → builds FLOW performative → `session.send(flow)`
2. If `session.frameSender` is null, the FLOW is silently dropped (see bug above)
3. Broker never receives credit → never sends TRANSFER → receiver hangs

**Fix:** Wire `issueCredit` through `client.sendPerformative()` instead of `session.send()`.

## SelectableChannelManager Pipeline Data Flow

1. Selector thread sees `OP_READ` → dispatches to processing pool
2. Processing pool reads from socket → calls `pipeline.fireRead(buffer)`
3. Pipeline iterates handlers → calls `handler.onRead(channel, data)`
4. Handler calls `transport.onRead(channel, data)` → adds to ring buffer
5. `available.release(n)` signals the semaphore
6. Calling thread's `receive()` wakes up → fetches from ring buffer

**Important:** The processing thread drains the socket in a loop (`read()` until -1 or 0) before calling `fireRead()`. This ensures all available data arrives in one buffer, reducing selector wake-up overhead.

## AMQP 1.0 Session Window Initialization

`remoteIncomingWindow` and `remoteOutgoingWindow` must be initialized to `DEFAULT_INCOMING_WINDOW` (e.g., 1024) rather than 0. If 0, `session.allocateDeliveryId()` returns -1 because it can't track deliveries against a window of 0. The broker's FLOW frame eventually updates these, but the first few sends fail before FLOW arrives.

## Diagnostic Pattern: Send/Read Tracing

When debugging interop failures, add `System.out.println` to:
- `PipelineTransport.send()` — logs bytes being sent
- `PipelineTransport.onRead()` — logs bytes arriving in ring buffer
- `AmqpClient.pollFrame()` — logs frame size and body read result
- `ReceiverLink.issueCredit()` — logs session/client availability

This reveals which step in the chain is silently dropping data.
