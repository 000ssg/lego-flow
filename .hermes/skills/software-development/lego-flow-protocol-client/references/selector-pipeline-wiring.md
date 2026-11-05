# SelectableChannelManager Pipeline Wiring

## The Handler Binding Problem

When a service delegates I/O to `SelectableChannelManager`, the `ChannelHandler` MUST be
wired into the manager's `ChannelPipeline` at channel registration time. The manager
creates an empty pipeline per channel — without a handler, read events are consumed by
the selector and silently dropped.

## Data Flow Chain

```
Selector.select() → dispatchKey(key)
  → ProcessingThread.processReadable()
    → channel.read(buffer)
      → pipeline.fireRead(channel, buffer)
        → handler.onRead(channel, buffer)  ← handler must be here!
          → transport.onRead(channel, data)  ← buffers for protocol layer
```

## Correct Pattern

```java
// In doConnect():
var transport = new PipelineTransport(dataChannel);
var handler = new XxxChannelHandler(this);
handler.transport = transport;  // wire handler → transport
ctx.registerChannel(this, dataChannel, handler);  // 3-arg form
```

## Anti-Patterns

1. **Empty pipeline** — `registerChannel(service, channel)` without handler. Selector reads data, fires `fireRead()`, but no handler exists to receive it. Data vanishes.

2. **Handler created but not registered** — Creating `XxxChannelHandler` and never passing it to `registerChannel()`. The handler exists as an object but is never attached to the pipeline.

3. **Null channel manager** — `ctx.registerChannel()` calls `getChannelManager()` which returns `getAttribute("channelManager")`. If the test/service doesn't set the attribute, registration silently fails. Always check for null:
   ```java
   var mgr = ctx.getChannelManager();
   if (mgr != null) mgr.registerChannel(this, dataChannel, handler);
   ```

## Channel Keying

Channels are keyed by `service.name + "#" + System.identityHashCode(channel)`, so multiple
client connections can share the same service descriptor without pipeline collision.

## Server-Side Accept

`handleAccept()` in `SelectableChannelManager` fires `pipeline.fireConnect(clientChannel)`
to the server's pipeline, then registers the accepted channel with a new pipeline. The
server's handler (bound at `registerServerChannel()` time) receives `fireConnect` and
spawns the protocol handler for the new connection.

## PipelineTransport Bridge

`PipelineTransport.onRead()` buffers incoming fragments and releases a semaphore.
`PipelineTransport.receive()` blocks on that semaphore until data arrives, then drains
the buffer. This enables blocking protocol code to work with non-blocking NIO pipelines.
