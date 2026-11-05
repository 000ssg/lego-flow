# AMQP 1.0 Sync Client: Critical Pitfalls Discovered During Interop

## session.send() silently drops when frameSender is null

`AmqpSession.send()` checks `if (frameSender != null)` before transmitting.
If called before `frameSender` is wired (or from a code path without access to the client),
frames are silently lost — no exception, no log.

**Fix:** Always prefer `client.sendPerformative(channel, performative)` which goes directly
to transport. Links (ReceiverLink, SenderLink) MUST carry `client(AmqpClient)` reference
and use it preferentially, falling back to `session.send()` only when client is null
(in-memory tests).

```java
// ReceiverLink.issueCredit():
if (client != null) {
    client.sendPerformative(session.localChannel(), flow);  // direct to transport
} else {
    session.send(flow);  // fallback for in-memory tests
}
```

## Multi-threaded pollFrame() races on shared ring buffer

In synchronous single-reader architecture, `pollFrame()` reads from a ring buffer filled
by the ProcessingThread's `onRead()`. If TWO threads call `pollFrame()` concurrently
(e.g. receiver thread's `receive()` + main thread's `createSession()`), they race for
frames: one thread steals handshake frames meant for the other.

**Observed symptom:** Receiver thread wakes from `pollFrame()`, reads BEGIN/ATTACH frames
intended for main thread's `waitForFrame()`, processes them (updating state correctly),
but then blocks waiting for the next frame. Main thread's `waitForFrame()` also blocks
because the frame was already consumed. Result: both threads block, test times out.

**Fixes (pick one):**
1. Serialize `pollFrame()` with a ReentrantLock so only one thread reads at a time
2. Use separate transports per thread (not possible with single TCP connection)
3. Ensure only ONE thread ever calls `pollFrame()` — handshake runs synchronously on the
   calling thread before any background thread starts reading

## Wire link.client() in createSender/createReceiver

Both `ReceiverLink` and `SenderLink` need `link.client(client)` called in the factory
method BEFORE the link is returned. Without it, `issueCredit()`, `settle()`, and `send()`
use `session.send()` which drops frames when frameSender is null.

```java
// AmqpClient.createSender():
link.session(session);
link.client(this);  // MUST be present
session.addSenderLink(link);

// AmqpClient.createReceiver():
link.session(session);
link.client(this);  // MUST be present
session.addReceiverLink(link);
```

## sendPerformative needs payload overload for TRANSFER

The basic `sendPerformative(channel, performative)` creates frames without payload.
For protocols that carry data (TRANSFER frames with message bodies), provide a payload
overload:

```java
public void sendPerformative(int channel, Performative performative) {
    sendPerformative(channel, performative, null);
}

public void sendPerformative(int channel, Performative performative, ByteBuffer payload) {
    var described = PerformativeCodec.encode(performative);
    var frame = new AmqpFrame(channel, FRAME_TYPE_AMQP, described, payload);
    transport.send(FrameCodec.encode(frame, maxFrameSize));
}
```

## Diagnostic logging approach

When debugging sync client interop issues, add `System.out.println` to:
- `PipelineTransport.onRead()` — confirms data arrival from socket
- `PipelineTransport.send()` — confirms data transmission
- `AmqpClient.pollFrame()` — confirms frame reads and decode
- `ReceiverLink.issueCredit()` — confirms credit issuance path
- `SenderLink.send()` — confirms send path

Remove all diagnostics before commit. Use `grep -r 'System.out.println'` to find stragglers.
