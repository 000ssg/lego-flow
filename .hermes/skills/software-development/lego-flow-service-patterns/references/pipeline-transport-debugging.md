# PipelineTransport Debugging Lessons

## Semaphore Binary Signal (NOT byte counter)

**Bug**: `onRead()` released N permits for N bytes: `available.release(n)`. Handshake responses (SASL, OPEN, BEGIN, ATTACH) inject 80+ bytes, creating 80+ excess permits. Next `tryAcquire()` succeeds instantly even when buffer is empty, causing the reader to spin in a tight loop and burn the timeout in ~1ms.

**Fix**: Always `available.release(1)` — one permit per `onRead()` call, regardless of byte count. The semaphore is a binary "data available" signal, not a byte counter.

```java
// GOOD
int n = add(data);
if (n > 0) available.release(1);

// BAD
int n = add(data);
if (n > 0) available.release(n);
```

## Shared Ring Buffer Frame Stealing

**Bug**: Multiple threads calling `pollFrame()` on the same `AmqpClient` share one `PipelineTransport` ring buffer. The receiver thread consumed broker's ATTACH response, DETACH, and END frames that were meant for the setup/handshake thread.

**Fix**: Ensure the setup thread consumes ALL frames from the broker before a second thread starts polling. In interop tests, start receiver threads AFTER `createReceiver()` fully returns.

## Diagnostic Tracing Techniques

When `System.out` traces don't appear in Maven surefire output:
- `mvn test` redirects System.out to `target/surefire-reports/<class>-output.txt`
- Check that trace appears in the full build log with `grep` — Maven may suppress stdout in parallel execution
- Use `--no-daemon` with Maven to avoid daemon process buffering issues
- Force JAR rebuild: `rm -rf ~/.m2/repository/ssg/lego-flow-<module>/` before `mvn install` to ensure changes propagate

## Selector/Dispatch Debugging

When `processReadable()` never fires despite OP_READ events:
1. Verify `SelectableChannelManager.dispatchKey()` sees the key (`key.isReadable()` is true)
2. Verify `processingPool.submit()` actually executes (trace inside the lambda)
3. Virtual thread executors CAN lose output if the thread hasn't started yet — add trace INSIDE the submitted task, not just before `submit()`
4. Check that `ProcessingThread` reads data and calls `pipeline.fireRead()` — add trace at each step of the chain: `processReadable()` → `channel.read()` → `pipeline.fireRead()` → `handler.onRead()` → `transport.onRead()`

## Key Trace Locations

For AMQP client debugging, trace at these layers:
- `SelectableChannelManager.dispatchKey()` — selector sees the event
- `ProcessingThread.processReadable()` — virtual thread reads from socket
- `AmqpClientChannelHandler.onRead()` — handler receives data
- `PipelineTransport.onRead()` — ring buffer receives bytes
- `AmqpClient.pollFrame()` — client reads frames from transport
- `ReceiverLink.pollFromTransport()` — link-level receive logic

Each layer can fail silently. Trace every layer to isolate which link in the chain breaks.
