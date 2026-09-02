# Protocol Implementation Design Guidelines

> **Project:** lego-flow — cross-cutting guidelines for all protocol modules  
> **Based on:** AMQP 1.0 implementation (messaging/amqp)  
> **Applies to:** All new protocol modules and major revisions of existing ones

---

## 1. Pipeline Architecture (DP/DF/Service)

### 1.1 Core Principle

**Every protocol must use the DP/DF/service pipeline.** Raw sockets are the last layer,
not the protocol layer. The protocol operates on framed, accumulated data — never on
raw byte streams directly.

```
SelectableChannelManager (NIO selector)
    └── ProcessingThread
        └── ChannelPipeline
            ├── TcpDataChannel (service module)
            ├── FrameCodec (bytes → complete frames)
            └── ProtocolService (frames → protocol actions)
```

### 1.2 Component Responsibilities

| Layer | Responsibility | Owns |
|-------|---------------|------|
| `TcpDataChannel` | Socket I/O | `SocketChannel`, NIO registration |
| `FrameCodec` | Byte accumulation & frame extraction | Accumulator buffer, frame boundaries |
| `ProtocolContext` | Protocol state machine | Connection state, session map, link registry |
| `ProtocolService` | Frame routing & business logic | Performative dispatch, message routing |
| `ChannelHandler` | Event → service binding | Connect/disconnect/read callbacks |

### 1.3 Golden Rule

> **Protocol implementation should react on input either consuming bytes fully or buffering
> them until it can consume. Receive should not directly issue protocol action, but prepare
> data and dispatch it through the pipeline.**

Consequences:
- `receive()` accumulates bytes → codec extracts complete frames → service processes
- `send()` encodes complete frames → channel flushes → pipeline confirms
- Never call protocol methods directly from a transport read callback

---

## 2. State Management

### 2.1 Separate Infrastructure States from Protocol States

**Infrastructure states** (lifecycle, thread, manager) live in `ServiceContext`.
**Protocol states** (connection phase, session phase, link phase) live in the protocol's
typed `Context` implementation.

```java
// Generic infrastructure (from service module)
ProcessorState: STOPPED, STARTING, READY, PAUSED, STOPPING

// Protocol-specific (AMQP example)
ConnectionState: START, HDR_SENT, HDR_RCVD, HDR_EXCH,
                OPEN_PIPE, OPEN_SENT, OPEN_RCVD, OPENED,
                CLOSE_PIPE, CLOSE_SENT, CLOSE_RCVD, END, FAILED
```

**Never mix these.** `ProcessorState` tracks if the service pipeline is alive.
`ConnectionState` tracks where the protocol negotiation is.

### 2.2 Typed Context, Not Generic Attributes

**Do not** store protocol state in `context.setAttribute("key", value)`.
**Do** create a typed interface with getters/setters.

```java
// ❌ BAD — generic attributes
context.setAttribute("connectionState", ConnectionState.OPENED);
context.setAttribute("channelId", 3);

// ✅ GOOD — typed interface
interface AmqpContext extends ServiceContext {
    ConnectionState getConnectionState();
    void transitionTo(ConnectionState newState);
    int getChannelMax();
}
```

### 2.3 State Machine Transitions

- State transitions must validate the target state against allowed transitions
- Use CAS (`AtomicReference.compareAndSet`) for thread safety
- State validation lives in the state enum (`isValidTransition()`), not the context
- Failed transitions are errors (log + close connection)

---

## 3. Frame/Message Accumulation

### 3.1 Never Trust a Single Read

Network reads are partial by nature. Your codec must handle:
- A single read containing multiple frames
- A single frame split across 3+ reads
- Zero-byte reads (keep reading)
- Connection close mid-frame

### 3.2 Accumulator Design

Use a `byte[]` with length tracking — not `ByteBuffer` — for accumulation:

```java
class FrameAccumulator {
    byte[] buffer = new byte[256];
    int length = 0;

    void append(byte[] data, int offset, int len) {
        if (length + len > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, length + len));
        }
        System.arraycopy(data, offset, buffer, length, len);
        length += len;
    }

    List<ByteBuffer> extractFrames() {
        // Read size header, extract complete frames, shift remainder
    }
}
```

**Why `byte[]` over `ByteBuffer`?**
- `ByteBuffer.compact()` loses position semantics under repeated partial fills
- `slice()` creates views with their own limits that become stale after flip
- Raw `byte[]` with length tracking is deterministic and testable

### 3.3 Frame Header Contract

Every protocol frame codec must:
1. Define a fixed-size header with total frame length
2. Validate the length field before allocating the payload buffer
3. Reject frames exceeding `maxFrameSize` (configurable)
4. Return zero or more complete frames per call (never partial frames)

---

## 4. Protocol Context Design

### 4.1 Context Extends ServiceContext

Your protocol context extends `DefaultServiceContext` and implements a typed interface:

```java
interface MyProtocolContext extends ServiceContext {
    ProtocolState getState();
    void transitionTo(ProtocolState state);
    FrameCodec getCodec();
    // Protocol-specific typed fields...
}

class MyProtocolCtxImpl extends DefaultServiceContext implements MyProtocolContext {
    private final AtomicReference<ProtocolState> state = new AtomicReference<>(START);
    // ...
}
```

### 4.2 Per-Connection Context

Each connection gets its own context instance. The context is created when the channel
opens and destroyed when it closes. It carries:
- Protocol negotiation state
- Session/link/channel registries scoped to this connection
- Flow control windows scoped to this connection
- Error context (last error, close reason)

### 4.3 Context Lifecycle

```
Channel open → createContext() → registerContext()
Frame received → context.getState() → route to handler → context.transitionTo(newState)
Channel close → context.transitionTo(FAILED) → cleanupContext()
```

---

## 5. Error Handling

### 5.1 Transport Errors Become Protocol Errors

When the transport layer fails (socket closed, send fails), it must surface as a protocol
error — not silently close the connection.

```java
// ❌ BAD — silent swallow
public void send(ByteBuffer data) {
    try { channel.write(data); } catch (IOException e) {
        LOG.debug("send failed", e);
        close();  // caller never knows
    }
}

// ✅ GOOD — throw on failure
public void send(ByteBuffer data) {
    try {
        while (data.hasRemaining()) channel.write(data);
    } catch (IOException e) {
        LOG.warn("Send failed", e);
        close();
        throw new ProtocolException("Send failed: " + e.getMessage());
    }
}
```

### 5.2 Error Frames

Protocol errors should send an explicit error frame (if the protocol supports it) before
closing the connection. AMQP uses `ERROR + CLOSE`. HTTP uses `5xx`. TLS uses `alert`.

### 5.3 State Machine Enforcement

An unexpected frame in the current state is a protocol violation:
1. Log the violation with current state + unexpected frame type
2. Send protocol error (if state allows)
3. Transition to terminal state (FAILED/CLOSED)
4. Close connection

---

## 6. Testing Strategy

### 6.1 Three-Tier Test Pyramid

| Tier | Scope | Transport | Example |
|------|-------|-----------|---------|
| Unit | Codec, types, state machine | None (memory) | `TypeCodecTest`, `ConnectionStateTest` |
| Integration | Client ↔ Server | `InMemoryTransport` | `AmqpSessionTest`, `LinkCreditTest` |
| Interop | Client ↔ Real Broker | `TcpTransport` | `BrokerInteropTest` |

### 6.2 InMemoryTransport for Integration Tests

Every protocol module must provide a memory-based transport for client↔server tests:
- Uses `BlockingQueue<ByteBuffer>` pair (one queue per direction)
- `send()` on one side → `receive()` on the other
- No network, no threads, deterministic ordering
- `close()` poisons both queues

### 6.3 Frame Codec Tests Must Cover

- Single frame in single read
- Partial frame header, partial body (2 reads)
- Frame spanning 3+ reads
- Multiple frames in one read
- Frame with zero-length payload
- Frame exceeding maxFrameSize → rejection
- Accumulator reset/flush behavior

### 6.4 State Machine Tests Must Cover

- Full happy path (START → ... → OPENED → ... → CLOSED)
- Each valid transition individually
- Each invalid transition (expect rejection)
- Concurrent transition attempts (CAS failure)

### 6.5 Test Location by Transport Dependency

Split tests based on whether they need external services:

| Test Type | Lives In | Transport | Runs When |
|-----------|----------|-----------|-----------|
| Unit | `<module>/src/test/java/` | None (memory) | Every build |
| In-process integration | `<module>/src/test/java/` | `InMemoryTransport` | Every build |
| Docker interop | `interop-tests/src/test/java/` | TCP (Docker) | CI interop job only |

**Never put Docker-dependent tests in module `src/test/java/`.** They will fail on
CI runners that don't have Docker services running during the `build` job.

```
module/src/test/java/.../      ← unit + in-process tests (run on every build)
interop-tests/src/test/java/.../  ← Docker interop tests (run in CI interop job)
```

The AMQP module demonstrates this pattern:
- `InProcessIntegrationTest.java` — lives in `messaging/amqp/src/test/`, uses InMemoryTransport
- `AmqpBrokerInteropTest.java` — lives in `interop-tests/src/test/`, connects to Docker brokers

### 6.6 Interop Tests

- Connect to real implementations (brokers, servers, clients)
- Test SASL/auth negotiation if applicable
- Test basic message flow
- Test graceful close
- Disable unsupported vendor modes with TODO comments (never silently skip)

---

## 7. Vendor Simulation Modes

When implementing a protocol with multiple real-world variants (AMQP vendors, HTTP modes),
support simulation modes:

```java
enum ContainerMode { STANDARD, RABBITMQ, ARTEMIS, QPID_DISPATCH }
enum BrokerMode { STANDARD, RABBITMQ, ARTEMIS }
```

Rules:
- Standard mode is the default and RFC-compliant
- Vendor modes document deviations from the standard
- Mode is a context field, not a class hierarchy
- Mode affects negotiation parameters and framing, not the core pipeline

---

## 8. Transport SPI

Every protocol module defines a transport interface at its lowest abstraction boundary:

```java
public interface AmqpTransport {
    void send(ByteBuffer data);
    int receive(ByteBuffer buffer);
    void close() throws IOException;
    boolean isOpen();
}
```

Implementations:
- `PipelineTransport` — production transport, driven by `SelectableChannelManager` (server)
  or blocking virtual threads (client)
- `InMemoryTransport` — test pair using `BlockingQueue`
- `WireCaptureInterceptor` — reusable wire-traffic capturer (see §10)

The protocol core never imports NIO classes directly. All I/O flows through the SPI.

---

## 9. Wire Capture for Protocol Debugging

When debugging interop failures, always capture and compare actual wire bytes instead of
guessing from code. The service module provides `WireCaptureInterceptor` for this:

```java
// Capture all traffic flowing through a pass-through connection
var capture = new WireCaptureInterceptor();
connection.addInterceptor(capture);

// ... traffic flows ...

// Read captured entries
for (var entry : capture.getEntries()) {
    System.out.printf("[%s] %d bytes: %s%n",
        entry.direction(), entry.data().length,
        bytesToHex(entry.data()));
}
```

**When to use:**
- **Interop debugging** — capture traffic against a real broker and compare with a reference
  implementation (e.g., rhea.js, Proton-C) to find byte-level differences
- **Protocol development** — verify your codec produces the exact bytes the spec requires
- **Handshake failures** — capture the first 100 bytes of a connection to see where negotiation
  diverges (SASL header, OPEN frame, etc.)

**Workflow:**
1. Wire `WireCaptureInterceptor` into your `PassThroughConnection`
2. Run the failing scenario (connect, send, etc.)
3. Read entries from `capture.getEntries()` and write to a text file
4. Compare with a known-good capture (run the same scenario with a reference client)
5. Fix the byte-level difference in your codec or state machine

See `service/src/main/java/.../service/passthrough/WireCaptureInterceptor.java`.

---

## 10. Checklist: New Protocol Module

When starting a new protocol implementation, create:

- [ ] `ProtocolContext` interface (typed, extends `ServiceContext`)
- [ ] `ProtocolCtxImpl` (extends `DefaultServiceContext`)
- [ ] `ProtocolFrameCodec` / `FrameAccumulator` (byte[] based)
- [ ] `ProtocolService` (extends `AbstractService`)
- [ ] `ProtocolChannelHandler` (implements `ChannelHandler`)
- [ ] `ProtocolState` enum with `isValidTransition()`
- [ ] `InMemoryTransport` or equivalent for integration tests
- [ ] Frame codec unit tests (partial reads, spanning, multi-frame)
- [ ] State machine unit tests (happy path + invalid transitions)
- [ ] Client service + server service (symmetric pipeline)
- [ ] Interop tests against real implementations

---

## Related Documentation

- [AMQP Architecture](messaging/amqp/doc/ARCHITECTURE.md)
- [AMQP Compliance](messaging/amqp/doc/COMPLIANCE.md)
- [Root AGENTS.md](AGENTS.md)
- [Service Module Architecture](service/doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-08-28
