# Two-Pipeline Bug in ServiceContext.registerChannel()

**Root cause**: `ServiceContext.registerChannel(service, channel, handler)` was creating a pipeline with the handler, then calling `mgr.registerChannel(service, channel)` which created a SECOND empty pipeline. The selector attached to the manager's empty pipeline — so events (OP_CONNECT, OP_READ) dispatched to the empty pipeline, never reaching the handler. Data arrived from the network but was silently dropped.

**Symptom**: `transport.receive()` returns -1 (EOF) immediately during handshake, even though the server is sending data. Transport appears "closed" because `onRead()` never fires and the handshake blocks forever.

**Fix**: `ServiceContext.registerChannel()` must pass the handler directly to `mgr.registerChannel(service, channel, handler)` so the manager creates ONE pipeline with the handler wired in. The old `ctx.registerChannel()` default method created a `ChannelPipeline(handler)` locally, then called `mgr.registerChannel()` which created another pipeline via `pipelinesByService.computeIfAbsent()`.

**SelectableChannelManager.registerChannel()** now accepts an optional handler:
```java
public void registerChannel(Service<?, ?> service, DataChannel channel, ChannelHandler handler) {
    var name = service.getDescriptor().name();
    channelsByService.put(name, channel);
    var pipeline = pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());
    if (handler != null) {
        pipeline.addLast(handler);  // NOT addHandler()
    }
    // register with selector, attach ChannelRegistration(channel, pipeline)
}
```

**ServiceContext.registerChannel()** now delegates correctly:
```java
default void registerChannel(Service<?, ?> service, DataChannel channel, ChannelHandler handler) {
    var mgr = getChannelManager();
    mgr.registerChannel(service, channel, handler);  // handler passed through
}
```

**Artemis broker behavior** (retained — do not scan again):
- Proto-0 mode (AMQP_HEADER): echoes AMQP_HEADER (8 bytes), does NOT send sasl-mechanisms frame. Connection proceeds to OPEN without SASL.
- Proto-3 mode (SASL_HEADER): does NOT echo SASL_HEADER. Waits for sasl-init frame before responding.
- Use `BrokerMode.ARTEMIS` which sets `proto0Accepted=true` in ClientConfig.
- Credentials: `guest` / password from Docker env, role mapping `amq = guest`.
- Runs on port 5675 in `artemis-test` Docker container (`apache/artemis:latest-alpine`).
