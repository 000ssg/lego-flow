# In-Memory Transport Testing

## Problem

`AmqpContainer.handleConnection()` submits the protocol loop to `executor.submit()` — a virtual-thread executor. The calling test thread (also a virtual thread) proceeds to connect the client.

On macOS CI, the executor's virtual thread can be descheduled between frames. The client connects, sends the handshake, then sends BEGIN — but the server's executor task is descheduled and hasn't sent the BEGIN response yet. The client times out.

This is **not a protocol bug** — it's virtual-thread scheduling variance. On Ubuntu/Windows CI, the executor is fast enough. On macOS CI, it's not.

## Root Cause

Two layers of virtual threads in tests:
1. Test thread: `Thread.ofVirtual().start(() -> container.handleConnection(...))`
2. Executor thread: `executor.submit(() -> { protocol loop... })`

The test thread fires the ready latch and returns. The executor thread hasn't started yet. When it does, it's a new virtual thread subject to scheduling delays.

## Solution

When a `readyCallback` is provided (test scenario), run the protocol loop **synchronously** on the calling thread:

```java
public void handleConnection(AmqpTransport transport, Runnable readyCallback) {
    Runnable protocolLoop = () -> {
        if (readyCallback != null) readyCallback.run();
        // ... protocol loop ...
    };
    if (readyCallback != null) {
        protocolLoop.run();       // synchronous — calling thread is already a virtual thread
    } else {
        executor.submit(protocolLoop); // service layer — don't block event loop
    }
}
```

The calling thread is already a virtual thread. It blocks on `transport.receive()`, which uses `LinkedBlockingQueue.poll()` — returns immediately when data arrives from the client. No scheduling delay between frames because there's only one thread.

## Why Thread.sleep() Doesn't Work

`Thread.sleep(300)` before the client connects doesn't help. The executor task can be delayed 5+ seconds on macOS CI. No fixed sleep is reliable.

## Why CountDownLatch on Thread Start Isn't Enough

Firing the latch at thread start (before `executor.submit()`) tells the test "the server thread is alive" — not "the server is reading from the transport". The executor delay still applies.

Firing the latch inside the executor task is better, but still two virtual threads. The real fix is synchronous execution when the caller already provides a virtual thread.

## Applied Fixes

- `AmqpContainer.handleConnection(transport, readyCallback)` — runs synchronously when callback provided
- `InProcessIntegrationTest` — passes `serverReady::countDown` as callback
