---
description: Implement lego-flow protocol clients via DP/DF/service.
name: lego-flow-protocol-client
---

# lego-flow protocol client implementation

Every protocol client in lego-flow MUST be implemented as a Service (DataProcessor + DataFilter pipeline), not as a monolithic socket handler. This is a hard architectural requirement.

## Mandatory Transport Architecture (AMQP is the authoritative reference)

**CRITICAL: AMQP implementation is the authoritative reference for all protocol modules in lego-flow.**
When in doubt about how a protocol module should be structured, look at AMQP. It is the correct pattern.
The user has explicitly corrected agents who tried to suggest alternatives — AMQP's approach is mandatory.

**The transport SPI is byte-level for EVERY protocol shape — streaming (NATS, STOMP) and request-response (Kafka, AMQP).** For a request-response protocol, do NOT invent a "request/response transport" that sends/receives framed requests; that is a frame-level transport and a violation. The length-prefix + correlation-ID framing is a **codec** concern (layer 4) that sits on top of the same byte-level transport SPI.

Every protocol MUST implement these layers in order:

1. **Byte-level transport SPI** (`transport/XxxTransport.java`)
   - `send(ByteBuffer)` — write bytes
   - `receive(ByteBuffer)` — fill buffer with available bytes, return count (-1 on close)
   - `receiveWithTimeout(ByteBuffer, long, TimeUnit)` — same with timeout
   - `close()` / `isOpen()` — lifecycle

2. **PipelineTransport** (`transport/PipelineTransport.java`)
   - Wraps `DataChannel` (the network SPI)
   - Ring buffer with `synchronized add()` and `fetch()` + semaphore
   - Implements `XxxTransport` byte-level SPI
   - Created by the service layer with `DataChannel`

3. **InMemoryTransport** (`transport/InMemoryTransport.java`)
   - In-memory byte-level transport pairs for testing
   - `createPair()` returns two connected instances
   - Uses `BlockingQueue<ByteBuffer>` internally
   - Tests create the pair and inject into broker/client

4. **Frame codec** (`transport/XxxFrameCodec.java` or `core/XxxCodec.java`)
   - Sits ON TOP of byte-level transport — wraps it
   - Provides `send(Frame)` (encode + write bytes) and `receive()` (read bytes + decode)
   - The protocol core uses the codec, NOT raw bytes
   - Codec has a stateful ring buffer for partial frames across reads

5. **Protocol core** (`core/XxxBroker.java`, `core/XxxClient.java`)
   - Takes `XxxTransport` (byte-level) in constructor
   - Creates `XxxFrameCodec(transport)` inside `handleConnection()` or `connect()`
   - All frame-level I/O goes through the codec

6. **Services** (`service/XxxService.java`)
   - `AbstractService<ByteBuffer, ByteBuffer>` — never frame types
   - `doConnect()` creates `TcpDataChannel` + `PipelineTransport`
   - Registers with `SelectableChannelManager`
   - Calls `core.handleConnection(transport)` or `core.connect()`
   - NEVER directly uses `Socket`, `ServerSocket`, `SocketChannel`, or `Selector`
   - Protocol implementations NEVER touch sockets

**This is the ONE authoritative pattern.** Frame-level transports, raw `java.net.Socket`, direct `InputStream/OutputStream` in protocol adapters are all violations of this architecture. If a protocol module (STOMP, MQTT, etc.) uses raw sockets in its main source code, it needs to be refactored to match AMQP.

## DP/DF/Service structure (legacy terminology)

- **Transport (DF)** — raw bytes I/O layer. See layers above.
- **Frame codec (DF)** — encode/decode protocol frames at the network boundary.
- **Client (DP)** — extends `AbstractService<FrameType, ResultType>`. Owns protocol state machine.

## Service layer — sync + async dual API

Every protocol client in lego-flow follows the **established service pattern**
(Redis, MQTT, SSH, DNS, HTTP):

- **Sync service** — `XxxClientService extends AbstractService<>`. The sync service
  delegates to a **blocking protocol implementation** (`XxxClient`, `XxxContainer`).
  The blocking class IS the protocol layer — not deprecated, not a temporary shim.
  It owns the state machine, SASL/handshake, sessions, links, and I/O.
  `doConnect()` instantiates and connects it; `doDisconnect()` closes it.
  `getClient()`/`getContainer()` exposes the protocol API after connection.
- **Async wrapper** — `.async()` on any `Service` returns `DefaultAsyncService<>`,
  which wraps every call in `CompletableFuture.runAsync()` on virtual threads.
  This is the standard dual API — sync-primary, async-wrapper. See `AbstractService.async()`.
- **Channel handler** — `XxxChannelHandler implements ChannelHandler` is a thin
  adapter between `SelectableChannelManager` + `ChannelPipeline` and the service.
  Feeds raw bytes through `service.consume(ctx, data)`. Protocol logic stays
  in the blocking client, not the handler.

```java
// Sync — delegates to blocking protocol client
class XxxClientService extends AbstractService<ByteBuffer, ByteBuffer> {
    private volatile XxxClient client;  // blocking protocol impl
    protected void doConnect(ServiceContext ctx) {
        client = new XxxClient(config);
        client.connect();  // blocks on virtual thread
    }
    public XxxClient getClient() { return client; }  // expose protocol API
}

// Async — standard wrapper via Service.async()
XxxClientService service = XxxClientService.builder(host, port).build();
var ctx = DefaultServiceContext.builder(ServiceUser.anonymous()).build();
service.async().connect(ctx).get(10, SECONDS);  // CompletableFuture on virtual thread
XxxClient client = service.getClient();           // sync protocol API still available
```

**Tests concentrate on async API** using `CountDownLatch` for coordination.
Sync API is fine when needed (e.g., interop tests against real brokers).
Never preserve original test structure — only functional intent matters.

## Construction rules

```java
public class XxxClient extends AbstractService<XxxFrame, Object> {

    // Constructor must pass 3 args: inputType, outputType, descriptor
    private XxxClient(Builder builder) {
        super(XxxFrame.class, Object.class, DESCRIPTOR);
        ...
    }

    private static final ServiceDescriptor DESCRIPTOR = new ServiceDescriptor(
            "protocol-client", "Protocol X client service");

    // Builder auto-wires socket transport if not explicitly set
    public XxxClient build() {
        if (builder.transport == null && builder.config != null) {
            builder.transport = new SocketTransport(config.host(), config.port(), ...);
        }
        return new XxxClient(this);
    }
}
```

## Convenience connect() for standalone use

The Service interface defines `connect(ServiceContext ctx)`. For standalone/interop use, provide a no-arg `connect()` that performs the handshake directly (without needing a full ServiceContext). The `doConnect(ServiceContext)` then delegates to `connect()`.

```java
public void connect() throws IOException {
    // handshake logic here
}

@Override
protected void doConnect(ServiceContext ctx) {
    try { connect(); }
    catch (IOException e) { throw new RuntimeException("...", e); }
}
```

## Convention over configuration

- Provide convenience overloads for common operations (e.g. `publish(routingKey, body)` delegates to `publish(exchange, routingKey, body, mandatory, immediate)`)
- Named `fromConfig(ClientConfig)` factory method auto-wires socket transport
- All public protocol methods throw `IOException`, never checked `Exception`
- Record types for results (`QueueDeclareResult`, `DeliverResult`)

## Critical pitfalls

1. **Greeting byte count** — the AMQP 0-9-1 client greeting is EXACTLY 8 bytes. A 9-byte greeting causes immediate RabbitMQ EOF. Always match the reference client byte-for-byte.
2. **String encoding** — RabbitMQ strictly enforces `shortstr` (1-byte length prefix) for names and vhosts, even though the AMQP 0-9-1 spec says `longstr` (4-byte). Always verify against the real broker, not just the spec.
3. **reserved-1 field placement** — NOT uniform across methods. Each method has its own field order. Verify against the official client wire capture, not spec diagrams alone.
4. **connect() throws IOException** — `doConnect()` cannot throw checked exceptions. Wrap IOException in RuntimeException inside doConnect().
5. **`connected` field** — declare your own `volatile boolean connected` (AbstractService has a private one). Do not shadow the parent field.
6. **Don't confuse protocol layer with deprecated code** — A blocking client that owns a socket and state machine IS the protocol implementation layer. Debug utilities (traffic capture, wire dump) are what get deprecated, not the blocking protocol classes themselves.
7. **Frame-level transports are violations** — Do not create transports that send/receive frames directly. The transport layer is byte-level only. Frame I/O belongs in the codec that wraps the transport.
8. **When refactoring, update tests too** — Changing the transport interface requires updating the in-memory test transport, all test files that create transport pairs, and the service layer. Do all changes in one pass.

## References

- `references/amqp-091-rabbitmq-quirks.md` — AMQP 0-9-1 RabbitMQ 4.x deviations from spec
- `references/handshake-sequencing.md` — connection handshake frame sequencing
- `references/service-pipeline-patterns.md` — DP/DF/service pipeline wiring patterns
- `references/in-memory-transport-testing.md` — in-memory transport testing patterns
- `references/transport-architecture.md` — byte-level transport + codec layering
- `references/in-process-test-synchronization.md` — event-driven test synchronization patterns
