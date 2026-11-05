# Synchronous TCP Read (Selector Thread)

**CRITICAL RULE: All TCP socket reads MUST happen in the selector thread.**

## Background

Session: 2026-09-09, cleanup-messaging branch. Debugging AMQP Artemis interop test failure.

## Problem

`ProcessingThread.processReadable()` was submitting `channel.read()` to an async virtual thread pool (`PROCESSING_POOL`). Multiple OP_READ events from the selector fired rapidly, each submitting a read to a separate thread. These threads raced on the same non-blocking `SocketChannel`, causing:

1. One thread reads 8 bytes (Artemis AMQP_HEADER echo)
2. Another thread reads 0 bytes (EAGAIN - no more data)
3. Third thread reads -1 (false EOF because the socket state was inconsistent)
4. `fireDisconnect()` closes the transport prematurely

The fix: read in the selector thread (single-threaded, no race), then dispatch the event.

## Fix

In `SelectableChannelManager.dispatchKey()`:
```java
if (key.isReadable()) {
    // READ in selector thread (synchronous)
    readBuffer.clear();
    int n = channel.read(readBuffer);
    // FIRE async (protocol handler)
    processingPool.submit(() -> processingThread.processReadable());
}
```

`processReadable()` only fires:
```java
public void processReadable() {
    processingPool.submit(() -> pipeline.fireRead(channel, readBuffer));
}
```

## Session Log (proof of data race)

```
[dispatchKey] readable=true writable=true ops=5 ready=5
[dispatchKey] → submitting processReadable to processingPool
[dispatchKey] readable=true writable=true ops=5 ready=5
[dispatchKey] → submitting processReadable to processingPool
[processReadable] read 8 bytes from channel
[processReadable] final n=0
[processReadable] nothing to fire after flip
[processReadable] read 8 bytes, firing
[processReadable] final n=-1  ← FALSE EOF from race
```

## User Directive

> "enforce this (non-async read from TCP) for any protocols implementation. as decoupling is handled via use of internal protocol ring buffer."
