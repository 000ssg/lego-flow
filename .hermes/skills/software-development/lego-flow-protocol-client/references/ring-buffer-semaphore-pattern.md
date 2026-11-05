# Ring Buffer Semaphore Pattern: Binary Signal, Not Byte Counter

## The Bug

When `PipelineTransport.onRead()` adds N bytes to the ring buffer and releases N semaphore permits, every `receiveWithTimeout()` call that acquires just 1 permit burns through the accumulated permits instantly.

**Symptom:** A receive thread's `pollFrame()` call exhausts a 10-second timeout in ~1ms. The thread spins through leftover permits from earlier reads (handshake data) without ever actually reading meaningful data.

**Root cause:** Handshake data (e.g., 80 bytes from broker's BEGIN response) leaves 80 semaphore permits. When the receiver thread later calls `pollFrame()` waiting for the broker's TRANSFER, it acquires 1 permit, finds partial data, loops back, acquires another, and so on — burning through all 80 leftover permits in microseconds before the broker's actual response arrives.

## The Fix

Release EXACTLY 1 permit per `onRead()` call, regardless of byte count:

```java
// BAD: releases N permits for N bytes
public void onRead(DataChannel ch, ByteBuffer data) {
    int n = add(data);
    available.release(n);  // 80 bytes → 80 permits!
}

// GOOD: binary signal
public void onRead(DataChannel ch, ByteBuffer data) {
    int n = add(data);
    if (n > 0) available.release(1);  // Signal: data available
}
```

The `peek()` fast path in `receiveWithTimeout()` checks `count >= needed` before trying to fetch. If data is already in the buffer, it reads immediately without touching the semaphore. The semaphore is ONLY a wake-up signal for when data isn't yet available.

## Why This Works

1. `receiveWithTimeout()` first checks `peek() >= needed`. If true, it fetches from the buffer immediately (no semaphore).
2. Only when `peek() < needed` does it call `tryAcquire(1, timeout)`.
3. After `tryAcquire()` succeeds, it calls `fetch()`. If `fetch()` returns 0 (false wake-up or partial data), it loops back and checks `peek()` again.
4. The semaphore is a simple "something arrived" signal, not a byte-level ledger.

## Where This Applies

Any transport implementation using a ring buffer + semaphore pattern:
- `PipelineTransport` in AMQP
- Similar patterns in MQTT, SSH, CoAP transports

## Discovered

AMQP 1.0 RabbitMQ interop testing (August 2026). The receiver thread's `pollFrame()` would burn through ~80 leftover permits from the handshake phase and time out before the broker's TRANSFER response arrived.
