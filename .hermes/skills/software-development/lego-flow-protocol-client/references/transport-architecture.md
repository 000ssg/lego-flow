# Transport Architecture — Byte-Level Transport + Codec Layering

## The One Authoritative Pattern

AMQP implementation is the authoritative reference. Every protocol module MUST follow this layering:

```
Bytes ↔ XxxTransport ↔ XxxFrameCodec ↔ Protocol Core (Broker/Client)
         ↑                    ↑
   PipelineTransport      Stateful ring buffer
   (ring buffer)          for partial frames
```

## Layer 1: Byte-Level Transport SPI

```java
public interface XxxTransport extends AutoCloseable {
    void send(ByteBuffer data);
    int receive(ByteBuffer buffer);
    int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit);
    void close();
    boolean isOpen();
}
```

Rules:
- Handles raw bytes only — no frame awareness
- `receive()` fills the buffer with available bytes, returns count (-1 on close)
- `receiveWithTimeout()` is the primary read method; `receive()` delegates to it with infinite timeout

## Layer 2: PipelineTransport (DataChannel-backed)

Wraps `DataChannel` for production use. Ring buffer with semaphore synchronization.

```java
public class PipelineTransport implements XxxTransport {
    private final DataChannel channel;
    private final byte[] buffer = new byte[65536];
    private final Semaphore semaphore = new Semaphore(0);

    PipelineTransport(DataChannel channel) { this.channel = channel; }
}
```

## Layer 3: InMemoryTransport (test transport)

Creates connected pairs for testing:

```java
var pair = InMemoryTransport.createPair();
// pair[0] → pair[1] (bytes flow both ways)
```

Uses `BlockingQueue<ByteBuffer>` internally. When one side sends, the other polls.

## Layer 4: Frame Codec

Sits on top of byte-level transport:

```java
public class XxxFrameCodec {
    private final XxxTransport transport;
    private final ByteBuffer buffer = ByteBuffer.allocate(65536); // for partial reads
    private int bufferEnd = 0;

    XxxFrameCodec(XxxTransport transport) { this.transport = transport; }

    void send(XxxFrame frame) {
        var bytes = XxxCodec.encode(frame);
        transport.send(bytes);
    }

    XxxFrame receive() {
        // Read bytes from transport, handle partial frames
        // Return when complete frame is decoded
    }

    XxxTransport getTransport() { return transport; }
}
```

## Layer 5: Protocol Core

Takes byte-level transport, creates codec inside connection handler:

```java
public void handleConnection(XxxTransport transport) {
    var codec = new XxxFrameCodec(transport);
    // Use codec.send(frame) and codec.receive()
}
```

## Critical Anti-Patterns

1. **Frame-level transport** — `send(Frame)`, `receive()` returning `Frame`. WRONG.
2. **Raw Socket in protocol** — `java.net.Socket`, `ServerSocket` in main source. WRONG.
3. **InputStream/OutputStream in protocol** — Wrapping Socket streams directly. WRONG.
4. **Codec as transport** — Codec should wrap transport, not be the transport.

## When Refactoring a Protocol Module

If a protocol module (STOMP, MQTT, etc.) uses frame-level transports or raw sockets:

1. Create `transport/XxxTransport.java` (byte-level SPI)
2. Create `transport/PipelineTransport.java` (DataChannel-backed)
3. Create `transport/InMemoryTransport.java` (test pairs)
4. Create `transport/XxxFrameCodec.java` (wraps transport for frame I/O)
5. Update `core/XxxBroker.java` and `core/XxxClient.java` to accept byte-level transport + create codec
6. Update services to create `PipelineTransport` with `TcpDataChannel`
7. Remove old TCP adapters that use raw sockets
8. Update tests to use `InMemoryTransport` + `FrameCodec` for frame-level I/O

Do all changes in one pass. Never leave a protocol in a half-refactored state.
