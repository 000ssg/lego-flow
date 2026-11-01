# Service Pipeline Patterns

Learnings from Phase 1-3 of the AMQP DP/DF migration.

## TcpDataChannel placement

TcpDataChannel belongs in `service/src/main/.../channel/` alongside UdpDataChannel. It is general-purpose infrastructure, not protocol-specific. The service module owns SelectableChannelManager, ChannelPipeline, and DataChannel — TcpDataChannel completes this symmetry.

Key contract:
- Wraps non-blocking SocketChannel
- `connect(host, port)` initiates async connection
- `finishConnect()` called from handler's `onConnect()` after selector fires connectable event
- `close()` cancels the selection key before closing socket
- `setSelectionKey(key)` / `getSelectionKey()` for lifecycle

## SelectableChannelManager.registerChannel() fix

The base method stored channels in maps but did NOT register with the NIO selector. Fixed to register with selector and attach ChannelRegistration:

```java
public void registerChannel(Service<?, ?> service, DataChannel channel) {
    var name = service.getDescriptor().name();
    channelsByService.put(name, channel);
    var pipeline = pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());
    try {
        if (channel instanceof TcpDataChannel tcp) {
            int ops = SelectionKey.OP_READ | SelectionKey.OP_CONNECT;
            var key = tcp.getSocketChannel().register(selector, ops,
                    new ChannelRegistration(channel, pipeline));
            tcp.setSelectionKey(key);
        } else if (channel instanceof UdpDataChannel udp) {
            var key = udp.getDatagramChannel().register(selector, SelectionKey.OP_READ,
                    new ChannelRegistration(channel, pipeline));
            udp.setSelectionKey(key);
        }
    } catch (IOException e) {
        channelsByService.remove(name);
        pipelinesByService.remove(name);
        throw new UncheckedIOException("Failed to register channel", e);
    }
}
```

Also added `setSelectionKey(SelectionKey)` to UdpDataChannel for symmetry.

## State separation

**Distinguish generic service lifecycle states from protocol-specific states.** Two orthogonal state machines:
- `ProcessorState` (STOPPED, STARTING, READY, STOPPING) — managed by AbstractService framework
- Protocol state (e.g., `ConnectionState`: START → HDR_SENT → OPENED) — managed by protocol code in the Context

Never conflate them. ProcessorState tracks whether the service is running; protocol state tracks handshake/flow progress.

## Frame accumulation

Transport delivers byte chunks, codec accumulates and extracts complete frames. **Use `byte[]` with length tracking, NOT `ByteBuffer`**, for the accumulator. ByteBuffer's compact/flip/limit semantics are error-prone and caused multiple bugs:
- compact() at position 0 is a no-op
- slice() semantics vary by position
- flip() swaps position/limit which breaks array-based extraction

Working pattern:
```java
static class FrameAccumulator {
    private byte[] data = new byte[4];
    private int length = 0;
    void put(ByteBuffer src) {
        int n = src.remaining();
        if (length + n > data.length) {
            var next = new byte[Math.max(data.length * 2, length + n)];
            System.arraycopy(data, 0, next, 0, length);
            data = next;
        }
        System.arraycopy(src.array(), src.position(), data, length, n);
        length += n;
    }
    ByteBuffer extract(int frameSize) {
        if (length < frameSize) return null;
        var frame = ByteBuffer.wrap(data, 0, frameSize);
        if (length > frameSize) {
            System.arraycopy(data, frameSize, data, 0, length - frameSize);
        }
        length -= frameSize;
        return frame;
    }
}
```

## Async connect pattern

Selector fires connectable → ProcessingThread fires `fireConnect()` → handler's `onConnect()` calls `tcp.finishConnect()`. The pipeline itself does NOT call finishConnect(); the protocol handler owns that step.

```java
@Override
public void onConnect(DataChannel channel) {
    if (channel instanceof TcpDataChannel tcp) {
        try { tcp.finishConnect(); }
        catch (Exception e) { onError(channel, e); return; }
    }
    context.setState(ProcessorState.READY);
    context.setChannel(channel);
}
```

## Connection state transitions

Protocol state transitions must be validated. Implement `isValidTransition(target)` on the protocol state enum — don't allow blind `compareAndSet` without validation. Header phases (HDR_SENT, HDR_RCVD) can complete independently and either can transition to HDR_EXCH.

```java
public boolean isValidTransition(ConnectionState target) {
    if (this == target) return true;
    switch (this) {
        case START: return target == HDR_SENT || target == HDR_RCVD;
        case HDR_SENT: return target == HDR_RCVD || target == HDR_EXCH;
        case HDR_RCVD: return target == HDR_SENT || target == HDR_EXCH;
        // ... etc
    }
}
```

## Context defaults

When implementing AmqpContext, initialize enum fields (BrokerMode, ContainerMode) with their default values. Don't leave them null — tests and callers expect sensible defaults.

## ChannelPipeline.fireRead() passes read-only buffers

Handlers receive `asReadOnlyBuffer()` views. Don't try to modify them. Use them for reading only.

## Pitfalls summary

- Never use blocking reads/writes in async pipeline — timeouts are anti-patterns
- TcpDataChannel in service module, not protocol module
- SelectableChannelManager.registerChannel() must register with selector (was broken)
- SelectionKey must be stored and cancelled on close
- ByteBuffer accumulator is error-prone — use byte[] instead
- ProcessorState vs protocol state are orthogonal — never conflate
- Validate state transitions, don't use blind compareAndSet
- Context enum fields need default values, not null
