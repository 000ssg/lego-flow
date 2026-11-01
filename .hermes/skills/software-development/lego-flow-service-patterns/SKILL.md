---
name: lego-flow-service-patterns
description: "Use when building or fixing lego-flow services."
created: 2026-08-28
---

# Lego Flow Service Patterns

## Trigger

Building, refactoring, or testing a service class in the lego-flow project (Maven multi-module, JDK 25, DP/DF/service architecture).

## AbstractService State Machine (CRITICAL)

`AbstractService.connect(ctx)` already manages state transitions:
```
connect():  transitionTo(CONNECTING) → doConnect() → transitionTo(READY)
disconnect(): doDisconnect() → transitionTo(STOPPED)
```

**RULE:** Your `doConnect()` and `doDisconnect()` must NOT call `transitionTo()` — the parent does it.
Duplicate calls cause `StateTransitionException: CONNECTING → CONNECTING`.

**Fix:** Remove any `transitionTo(ProcessorState.CONNECTING)` from `doConnect()` and `transitionTo(ProcessorState.STOPPED)` from `doDisconnect()`.

## Handshake Discipline (MANDATORY)

**NEVER use timeouts in the protocol handshake path.** The data flow is purely event-driven:

```
Socket → Selector fires OP_READ → ProcessingThread reads bytes
  → Pipeline.fireRead() → handler.onRead() → transport.onRead() (ring buffer + semaphore)
  → Protocol layer calls transport.receive() (blocks on semaphore, not timeout)
```

- TCP connection state machine: managed by `SelectableChannelManager` (selector + event loop)
- Protocol state machine: managed by the client (`AmqpClient`, etc.)
- These are TWO INDEPENDENT state machines. Do not mix them.
- The handshake runs on the calling thread AFTER `onConnect()` latches down — data arrives via `onRead()` callbacks, and the protocol layer reads from the ring buffer via `transport.receive()` which blocks on the semaphore until `onRead()` releases it.
- **Never add `receiveWithTimeout()` or any timeout-based read in the handshake.** The user corrected: "there should be no timeouts as we use state transitions to reflect both TCP and protocol states."

## Service Infrastructure Wiring (MANDATORY)

**STRICT ARCHITECTURE RULE: Protocol implementations NEVER touch SocketChannel or Selector directly.** They only use `DataChannel`. The `SelectableChannelManager` owns the selector and event loop — it is the SINGLE authorized component for network I/O.

### Client-side TCP connection lifecycle (MUST NOT be broken)

1. **Create non-blocking socket + TcpDataChannel**
2. **Create handler with completion latch** — `new ChannelHandler(...)` + `new CountDownLatch(1)` + `handler.setConnectLatch(latch)`
3. **Register channel with manager** — `ctx.registerChannel(this, dataChannel, handler)` (registers OP_CONNECT only)
4. **Start async connect** — `socketChannel.connect(address)` (AFTER registration, not before)
5. **Await latch** — `latch.await(timeout)` (blocks until onConnect fires)
6. **Handler.onConnect()** runs:
   - Guard with `AtomicBoolean.compareAndSet(false, true)` to prevent duplicate calls (see pitfall #8)
   - `finishConnect()` on SocketChannel
   - `updateChannelOps(service, READ | WRITE)` to enable data flow
   - `latch.countDown()` to unblock the calling thread
7. **Protocol handshake** runs on the calling thread (NOT in `onConnect`) — `client.connect(transport)` in a separate method called after latch fires
8. **Data flows** via `fireRead/fireWrite` → handler → transport → protocol

**NEVER** mix TCP connection process and protocol initialization. TCP lifecycle is managed by the selector; protocol handshake happens after `onConnect()` enables OP_READ|OP_WRITE and signals the latch.

### `SelectableChannelManager` API

- `registerChannel(service, channel)` — registers for OP_CONNECT (client) or OP_READ (server accept)
- `registerChannel(service, channel, handler)` — same + wires handler into pipeline
- `updateChannelOps(service, ops)` — switch interest ops (e.g., OP_CONNECT → READ|WRITE) + wakes selector
- `unregisterChannel(service)` — cancels key, closes channel
- `getChannelPipeline(service)` — returns the `ChannelPipeline` for the service

### `ServiceContext` default methods

- `registerChannel(service, channel)` → delegates to manager
- `registerChannel(service, channel, handler)` → delegates + adds handler to pipeline
- `getChannelManager()` → returns `SelectableChannelManager`

### `PipelineTransport` (protocol-side buffering)

- Ring buffer with `synchronized add()` and `synchronized fetch()` (atomic operations)
- `peek()` is a volatile estimate — no lock needed
- `receive(buffer)` fills the requested buffer completely (loops, waiting via semaphore)
- `onRead(channel, data)` copies from incoming buffer into ring buffer and signals semaphore
- Transport NEVER reads from the channel — only receives data via `onRead()`

### Server-side

- `ServerDataChannel.registerWith(selector)` for OP_ACCEPT
- `handleAccept()`: `fireConnect(clientChannel)` → register accepted channel for OP_READ

## Interop Test Patterns

### Docker containers

- User starts Docker themselves. Agent should just run `docker run` and check `docker ps`.
- Name containers descriptively: `rabbitmq-test`, `qpid-dispatch-test`, `artemis-test`
- Clean up: `docker rm -f <names>` before starting
- Port mapping: standard ports (5672, 5674, 5675)
- **Broker isolation**: when debugging one broker, stop the other. Never have two AMQP brokers running for an AMQP-focused investigation.

### Test independence

- Every test method creates its own connection and uses a unique queue name (UUID-based)
- Only `SelectableChannelManager` is shared across tests via `@BeforeAll`/`@AfterAll`
- Tests must be runnable in parallel and in any order
- `@BeforeEach`/`@AfterEach` for per-test setup/teardown

### Port readiness

```java
void waitForPort(int port, Duration timeout) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
        try (SocketChannel ch = SocketChannel.open()) {
            if (ch.connect(new InetSocketAddress(host, port))) return;
        } catch (Exception ignored) { Thread.sleep(500); }
    }
    throw new RuntimeException("Port " + port + " did not open within " + timeout);
}
```

### Synchronization

- Use `CountDownLatch` with generous timeouts (5-10s), NOT `Thread.sleep` as primary sync
- Brief `Thread.sleep(200-300ms)` only for subscription propagation is acceptable
- Assert latch AFTER operation completes: `latch.await(timeout)` → then assert results

## Build Commands

- Compile: `./gradlew :lego-flow-amqp:compileJava :lego-flow-amqp:compileTestJava --no-daemon`
- Test: `./gradlew :lego-flow-amqp:test --no-daemon`
- Full build: `./gradlew clean test --rerun-tasks`
- Maven: `mvn clean test -pl '!benchmarks'`
- Java Home: `JAVA_HOME=$HOME/.sdkman/candidates/java/current`

## Common Pitfalls

1. **StateTransitionException**: `doConnect()` calls `transitionTo(CONNECTING)` — remove it
2. **Cross-module imports**: Transport bridges must live in protocol module (can't reference protocol types from service module)
3. **Missing LOG import**: Always import `org.slf4j.Logger; org.slf4j.LoggerFactory;` and declare `private static final Logger LOG`
4. **ServiceContext API**: `ServiceContext` doesn't have `getSelector()` — use `SelectableChannelManager`
5. **DefaultServiceContext**: Uses constructor `new DefaultServiceContext(user)`, NOT a builder pattern
6. **OP_CONNECT race**: Setting latch AFTER `registerChannel()` misses fast TCP connects (localhost). Always set latch before registering.
7. **SocketChannel.connect() before register()**: Connect starts the TCP handshake. If done before `registerChannel()`, the connection completes before selector is listening → `CancelledKeyException`. Always register first, then connect.
8. **OP_CONNECT fires multiple times**: The selector re-dispatches OP_CONNECT on every wake-up cycle, even after `updateChannelOps()` changes interestOps. Guard `onConnect()` with `AtomicBoolean.compareAndSet(false, true)` at the top, before `finishConnect()`. Without this guard, duplicate calls throw `ConnectException` and close the connection.
9. **`processConnectable()` must be synchronous**: Do NOT wrap `fireConnect()` in `PROCESSING_POOL.submit()`. The selector dispatches to `connectionPool` which creates a new virtual thread per OP_CONNECT. If you then submit to another pool, the connection pool fires again before `finishConnect()` runs → race → `Connection refused`. Call `fireConnect()` directly in the connection pool thread.
10. **Stale compiled classes**: When you change source and re-run tests, the interop module may use stale classes from the last `mvn install`. Always run `mvn install -pl messaging/amqp -DskipTests` before re-running interop tests.
11. **Don't inspect sockets**: Protocol implementations must never call `socketChannel.socket().getSocketException()` or similar. `finishConnect()` throws `ConnectException` on refusal — that's the only error signal needed.
12. **SelectionKey teardown race**: When `manager.close()` is called, the selector thread may be in the middle of dispatching an event, or the key may already be cancelled. Handler callbacks like `onWrite()` that call `key.interestOps()` must wrap the call in try/catch for `ClosedChannelException`. This is a normal race during test teardown, not an error.

**Pattern:**
```java
@Override
public void onWrite(DataChannel channel) {
    var key = channel.getSelectionKey();
    if (key != null) {
        try {
            key.interestOps(SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            // Key cancelled during teardown — ignore
        }
    }
}
```

**Pitfall**: A flaky test that fails with `ClosedChannelException` during cleanup is a race, not a timing issue. Adding `Thread.sleep()` or increasing timeouts will not fix it. The fix is defensive coding in the handler callback.
13. **PipelineByteTransport.send() must never write directly to channel**: The transport SPI's `send()` method must enqueue data to `outboundQueue` and let the selector thread write via `onWrite()`. Writing directly to `channel.write()` from the calling thread races with the selector's read/write and causes message loss (confirmed by NATS/STOMP/MQTT test failures). This rule applies to ALL protocols — do not add "immediate-write optimizations" to any transport implementation. If a protocol needs fast initial writes, handle it at the codec or protocol layer.
14. **PipelineByteTransport.send() must consume the caller's buffer**: After enqueuing a duplicate of the data, `send()` must advance the caller's `ByteBuffer` position to its limit (`data.position(data.limit())`). Without this, repeated calls to `send()` with the same buffer enqueue the same bytes multiple times, causing data duplication and outbound queue growth. The contract is: `send()` consumes the buffer — the caller should not reuse it after calling `send()`.
