# OP_CONNECT Spurious Re-selection Fix

## Problem
After `finishConnect()` succeeds in `dispatchKey()`, the key's `readyOps` still contains `OP_CONNECT`. On the next `select()` call, the selector re-selects the key as connectable-ready. This causes `finishConnect()` to be called again, which throws an exception and closes the channel.

## Root Cause
`key.interestOps(READ|WRITE)` changes what the selector listens for, but does NOT clear the key's `readyOps`. The key remains connectable-ready from the previous selection cycle.

## Fix
In `SelectableChannelManager.dispatchKey()`, after `finishConnect()` succeeds:

1. `key.cancel()` — cancels the old key
2. `socketChannel.register(selector, OP_READ|OP_WRITE, attachment)` — creates a fresh key with clean readyOps
3. Submit `processConnectable()` asynchronously

```java
if (key.isConnectable()) {
    var socketChannel = ((TcpDataChannel) channel).getSocketChannel();
    if (socketChannel.isConnectionPending()) {
        try {
            socketChannel.finishConnect();
            key.cancel();
            var newKey = socketChannel.register(selector,
                    SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
        } catch (IOException e) {
            LOG.warn("TCP connect failed: {}", e.getMessage());
            try { channel.close(); } catch (IOException ignored) {}
            key.cancel();
            return;
        }
        var processingThread = new ProcessingThread(channel, pipeline, bufferSize);
        connectionPool.submit(() -> processingThread.processConnectable());
    }
    return;
}
```

## Separation of Concerns (CRITICAL)
- **SelectableChannelManager**: owns TCP lifecycle (`finishConnect`, interest ops, channel close)
- **Protocol handler**: handles ONLY protocol events (`onRead`, `onWrite`, `onDisconnect`) + signals latch
- Handler must NEVER call `finishConnect()`, `updateChannelOps()`, or close the channel
- Mix concerns → double `finishConnect()` → IOException → channel closed → "Transport closed"

## Handler Transport Timing
Handler must have `transport` set at construction time. Between `onConnect()` and `doProtocolHandshake()`, data may arrive. If `onRead()` fires with `transport == null`, data is silently lost → `readFully()` blocks forever → "Transport closed".
