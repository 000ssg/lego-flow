# In-Process Test Synchronization

## The Executor-Ready Problem

When a server-side method (`AmqpContainer.handleConnection()`, etc.) submits work to `executor.submit()` and returns immediately, the server thread starts but the actual protocol processing hasn't begun yet. On macOS CI, virtual-thread executor scheduling can be delayed 5+ seconds.

### Anti-Pattern: Latch Before handleConnection

```java
// BAD: latch fires before executor task starts
var latch = new CountDownLatch(1);
Thread.ofVirtual().start(() -> {
    latch.countDown();              // fires here — too early!
    container.handleConnection(t);  // submits to executor, returns immediately
});
latch.await(5, TimeUnit.SECONDS);
client.connect(transport);          // client times out — server not reading yet
```

### Fix: Ready Callback Inside Executor Task

Add a `Runnable readyCallback` parameter to the server method. Fire it as the first statement inside `executor.submit()`:

```java
// In AmqpContainer:
public void handleConnection(AmqpTransport transport, Runnable readyCallback) {
    executor.submit(() -> {
        if (readyCallback != null) readyCallback.run(); // fires INSIDE executor task
        byte[] header = readHeader(transport);          // blocks until client writes
        // ... rest of protocol lifecycle
    });
}

// In test:
var latch = new CountDownLatch(1);
Thread.ofVirtual().start(() -> {
    container.handleConnection(t, latch::countDown);  // fires inside executor, before readHeader()
});
latch.await(5, TimeUnit.SECONDS);
client.connect(transport);  // server is now actively reading
```

## QUIC Loss Detection Flakiness

First RTT sample in in-process tests is artificially small (~1ms). CI scheduling jitter exceeds the tiny time threshold, falsely marking packets as lost. Per RFC 9002 §6.1.2, time-based detection requires a valid RTT estimate.

**Fix:** Only apply time-based loss detection when `rttSampleCount >= 2`. The initial sample alone is unreliable.

```java
// In QuicLossDetection.detectLostPackets():
boolean timeThresholdEnabled = rttSampleCount >= 2;
if (timeThresholdEnabled && elapsed >= timeThreshold) {
    lost.add(packet);
}
```

## Pipeline Buffer Sharing

When `ChannelPipeline.fireRead()` passes a `ByteBuffer` to multiple handlers, the first handler that reads it advances `position()`, leaving nothing for downstream handlers.

**Fix:** Use `data.duplicate()` in recording/inspection handlers so they don't consume the original buffer's position:

```java
public void onRead(DataChannel channel, ByteBuffer data) {
    var dup = data.duplicate();
    var bytes = new byte[dup.remaining()];
    dup.get(bytes);
    // downstream handlers still see full buffer
}
```

## Event-Driven Synchronization with Protocol Event Listeners

When tests need to wait for protocol lifecycle events (handshake completion, session creation, etc.), use the protocol's event listener with `CountDownLatch` instead of `Thread.sleep()`. This is deterministic and works reliably under CI load.

**Pattern:**
```java
// 1. Create latch before connecting client
CountDownLatch openLatch = new CountDownLatch(1);

// 2. Attach listener to server
server.setListener(AmqpEventListener.latchOnFirst(openLatch, AmqpEventListener.EventType.CONNECTION_OPENED));

// 3. Connect client (blocks until handshake completes)
client.connect(ctx);

// 4. Wait for server to complete handshake
assertThat(openLatch.await(10, TimeUnit.SECONDS)).isTrue();
```

**Why this works:** The `CONNECTION_OPENED` event fires only after the server has fully processed the client's AMQP OPEN frame and sent its response. This is a definitive synchronization point — no guesswork about timing.

**Multiple clients:** For tests with multiple clients, use an array of latches and a listener that counts down the next available latch:
```java
CountDownLatch[] latches = new CountDownLatch[numClients];
for (int i = 0; i < numClients; i++) latches[i] = new CountDownLatch(1);

server.setListener((event, connId, detail) -> {
    if (event == AmqpEventListener.EventType.CONNECTION_OPENED) {
        for (CountDownLatch latch : latches) {
            if (latch.getCount() > 0) {
                latch.countDown();
                break;
            }
        }
    }
});

for (int i = 0; i < numClients; i++) {
    clients[i].connect(ctx);
    assertThat(latches[i].await(10, TimeUnit.SECONDS)).isTrue();
}
```

**Available events by protocol:**
- AMQP: `CONNECTION_STARTED`, `CONNECTION_OPENED`, `SESSION_CREATED`, `LINK_ATTACHED`, `LINK_DETACHED`, `CONNECTION_CLOSING`, `MESSAGE_RECEIVED`, `MESSAGE_SENT`
- MQTT: `CLIENT_CONNECTED`, `CLIENT_DISCONNECTED`, `SESSION_CREATED`, `SESSION_RESUMED`, `SUBSCRIPTION_ADDED`, `WILL_DELIVERED`, `SESSION_EXPIRED`, `KEEP_ALIVE_TIMEOUT`
- STOMP: `SESSION_CONNECTED`, `SESSION_DISCONNECTED`, `MESSAGE_DELIVERED`, `TRANSACTION_COMMITTED`, `TRANSACTION_ABORTED`

**Pitfall — set listener BEFORE connecting client:** The listener must be attached to the server before the client connects, otherwise the event may fire before the listener is in place and the latch will never count down.

**Pitfall — service layer must expose setListener():** If the service class doesn't have a `setListener()` method, add one that forwards to the underlying container/broker. Without this, tests cannot attach listeners for synchronization.
