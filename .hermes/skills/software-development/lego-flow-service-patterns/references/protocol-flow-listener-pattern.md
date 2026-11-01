# Protocol Flow Listener Pattern

## Requirement

Every protocol module MUST implement a lightweight event listener for lifecycle events.
This is the ONLY authorized way to synchronize in-process tests — never `Thread.sleep`
to wait for a server to be ready.

## Interface

```java
public interface ProtocolEventListener {
    void onEvent(EventType event, String connectionId, String detail);
    ProtocolEventListener NO_OP = (event, connId, detail) -> {};
}
```

The `NO_OP` sentinel avoids null checks in the hot path — zero overhead when not configured.

## Container setup

```java
private ProtocolEventListener listener = ProtocolEventListener.NO_OP;

public void setListener(ProtocolEventListener listener) {
    this.listener = listener != null ? listener : ProtocolEventListener.NO_OP;
}
```

## Required Event Types

| Event | When | Purpose |
|-------|------|---------|
| `CONNECTION_STARTED` | Protocol loop begins reading | Test knows server is ready for client |
| `CONNECTION_OPENED` | Handshake complete | Test knows session setup can begin |
| `SESSION_CREATED` | Session/channel mapped | Test can verify session lifecycle |
| `LINK_ATTACHED` | Logical endpoint attached | Test can verify routing setup |
| `CONNECTION_CLOSING` | Connection teardown | Test can verify cleanup |

## Synchronous Dispatch (CRITICAL)

When a listener is configured (not NO_OP), the protocol loop MUST run
**synchronously** on the calling thread — no executor. This eliminates executor
scheduling delays (5-15s on CI) that cause clients to time out waiting for responses.

```java
public void handleConnection(Transport transport) {
    String connId = UUID.randomUUID().toString().substring(0, 8);
    var ctx = new ConnectionContext(connId, transport);
    connections.put(connId, ctx);

    if (listener != ProtocolEventListener.NO_OP) {
        // Run SYNCHRONOUSLY — no executor scheduling delay
        // Safe for in-memory transports where caller is already a virtual thread
        listener.onEvent(CONNECTION_STARTED, connId, null);
        try {
            runProtocol(ctx);
        } catch (Exception e) {
            LOG.debug("Connection {} error: {}", connId, e.getMessage());
        } finally {
            listener.onEvent(CONNECTION_CLOSING, connId, null);
            cleanup(ctx);
            connections.remove(connId);
            transport.close();
        }
    } else {
        // Run ASYNCHRONOUSLY — service layer, multiple connections
        executor.submit(() -> {
            listener.onEvent(CONNECTION_STARTED, connId, null);
            try {
                runProtocol(ctx);
            } catch (Exception e) {
                LOG.debug("Connection {} error: {}", connId, e.getMessage());
            } finally {
                listener.onEvent(CONNECTION_CLOSING, connId, null);
                cleanup(ctx);
                connections.remove(connId);
                transport.close();
            }
        });
    }
}
```

## Implementation Rules

1. **Listener must be optional** — default to NO_OP sentinel, not null
2. **Fire at transition points only** — never in the hot data path (frame reads/writes)
3. **Connection ID must be included** — tests identify which entity triggered the event
4. **Detail is optional** — free-form string for link names, channel IDs, etc.
5. **Zero overhead when absent** — NO_OP sentinel avoids null checks in protocol code
6. **Synchronous dispatch when active** — eliminates executor scheduling delays on CI

## Test Usage

```java
@Test
void testMessaging() throws Exception {
    var transportPair = InMemoryTransport.createPair();
    var container = new AmqpContainer(ContainerConfig.builder()...);

    // Configure listener-driven sync
    var serverReady = new CountDownLatch(1);
    container.setListener((event, connId, detail) -> {
        if (event == AmqpEventListener.EventType.CONNECTION_STARTED) {
            serverReady.countDown();
        }
    });

    // Server runs in a virtual thread — blocks on handleConnection (sync path)
    Thread.ofVirtual().start(() -> container.handleConnection(transportPair[1]));

    // Wait for server to actually start reading — no Thread.sleep guesswork
    assertThat(serverReady.await(5, TimeUnit.SECONDS)).isTrue();

    // Client can now connect — server is already blocked on receive()
    try (var client = new AmqpClient(ClientConfig.builder()...)) {
        client.connect(transportPair[0], Duration.ofSeconds(5));
        // ... proceed with session/link setup ...
    }
}
```

## Root Cause

Virtual-thread executors on CI runners (especially macOS/Windows) can delay task
execution by 5-15 seconds under parallel test load. When `handleConnection` submits
the protocol loop to an executor, the loop's virtual thread gets descheduled between
frames. The client times out waiting for the server's response — even though both
sides run in the same JVM.

The synchronous dispatch path eliminates this entirely: the test's virtual thread
runs the protocol loop directly, so frame processing is synchronous and deterministic.

## Reference Implementation

`AmqpEventListener.java` in the AMQP module (`messaging/amqp/src/main/java/.../common/`).
