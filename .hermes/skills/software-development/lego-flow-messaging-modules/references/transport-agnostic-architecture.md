## Transport-Agnostic Architecture (MQTT Reference)

Established during MQTT module refactor (Sept 2026). Every protocol module should follow this pattern:

### Layer Stack

1. **ByteTransport SPI** — base interface in `service/channel/ByteTransport.java` with `send()`, `receive()`, `receiveWithTimeout()`, `close()`, `isOpen()`
2. **Protocol Transport Interface** — optional, only if protocol needs protocol-specific methods (e.g. `MqttTransport` with `getChannel()`). **If empty (zero methods beyond ByteTransport), DELETE it and use ByteTransport directly.**
3. **Pipeline Transport** — ring buffer over `DataChannel`, generic in `service/channel/PipelineByteTransport.java`
4. **In-Memory Transport** — two variants in `service/channel/`:
   - `InMemoryByteTransport` — configurable TCP semantics (chunked delivery by default, partial reads); disable with `TcpSemantics.NONE` for raw synchronous queue behavior
   - `InMemoryByteHeadBuffer` — partial reads hold remainder at stream head (Kafka, NATS, XMPP style)
5. **TLS Wrapper** — DF that wraps any transport with transparent SSL (handshake, wrap/unwrap)
6. **WebSocket Wrapper** — wraps WebSocket session, encodes/decodes binary frames via frame codec
7. **Broker** — accepts `ByteTransport`, never touches sockets. Protocol engine only.
8. **Client** — accepts `ByteTransport`, never touches sockets. Protocol engine only.
9. **Service** — opens sockets, creates `TcpDataChannel`, wraps in `PipelineByteTransport`, injects into client/broker

### Example Transport Files

- `service/channel/ByteTransport.java` — base SPI
- `service/channel/InMemoryByteTransport.java` — simple in-memory (re-queue tail)
- `service/channel/InMemoryByteHeadBuffer.java` — head-buffer in-memory (preserve order)
- `service/channel/PipelineByteTransport.java` — ring buffer over DataChannel
- `messaging/mqtt/transport/MqttTransport.java` — protocol interface (adds `getChannel()`)
- `messaging/mqtt/transport/MqttPipelineTransport.java` — extends PipelineByteTransport
- `messaging/mqtt/transport/InMemoryMqttTransport.java` — extends InMemoryByteTransport

### Implementation Rules

- **Generic base classes must NOT be `final`** — they must be extensible by protocol-specific implementations
- **Protocol implementations extend the generic base** — never reimplement the ring buffer or in-memory pairing logic
- **Omit redundant `implements ByteTransport`** — when extending a base class that already implements ByteTransport, don't re-declare it
- **NEVER write directly to channel from send()** — `PipelineByteTransport.send()` must enqueue to `outboundQueue` and let the selector thread write via `onWrite()`. Direct `channel.write()` from the calling thread races with the selector and causes message loss (NATS/STOMP/MQTT failures). Any protocol needing fast initial writes should handle it at the codec or protocol layer, not in the transport SPI.
- **When removing an empty protocol interface**, replace ALL usages across the module: `find <module> -name '*.java' -not -path '*/target/*' -exec sed -i '' 's/\bOldTransport\b/ByteTransport/g' {} \;`, then add imports as needed

### Critical Rule

**NEVER** have protocol implementations (broker, client) touch `SocketChannel`/`Selector` directly. TCP lifecycle is strictly the service layer's responsibility. The service opens the socket, wraps in `TcpDataChannel`, then `PipelineByteTransport`, and injects into the transport-agnostic broker/client.

### Test Pattern

```java
var pair = InMemoryByteHeadBuffer.createPair();  // or InMemoryByteTransport.createPair()
broker.handleConnection(pair[0]);
var client = new MqttClient(MqttClientConfig.defaults()
    .host("localhost").port(1883).clientId("test").build(), pair[1]);
```

### Config Patterns

- `MqttClientConfig` uses a fluent builder: `MqttClientConfig.defaults().host().port().clientId().build()`
- `MqttBrokerConfig` is a **record with no builder** — use direct constructor or `MqttBrokerConfig.minimal()`

### Shared Subscriptions

- Registry tracks share groups with round-robin selection (`SharedSubscriptionRegistry`)
- `TopicFilter.getEffectiveFilter()` strips `$share/group/` prefix
- Broker splits routing: regular subscriptions fan-out, shared subscriptions deliver to one member per group
- Publisher excluded from shared group delivery (no-local semantics)

### Topic Alias (MQTT 5.0)

- Tracked per-connection in `ClientConnection` via `Map<Integer, String>`
- Alias range: 1-200
- Empty topic name + alias property → resolve alias to topic
- Non-empty topic name + alias property → register alias mapping
- Unknown alias → silently drop message

### Frame Reassembly and Reader-Loop Contract

The byte→frame boundary (in-memory transport + frame codec + reader loop) has three failure modes with distinct unit-test shapes:

#### Reassembly (codec side)

- One `transport.receive()` + one `decode()` per read silently **drops coalesced frames** (a broker batching several small frames into one TCP segment) and cannot decode a frame **split across reads**.
- The frame codec must keep a reassembly buffer across calls: append every read, locate the first complete frame boundary with a finder that distinguishes complete from partial (STOMP: `StompCodec.findFrameEnd` throwing `FrameIncompleteException`; MQTT: the codec's internal accumulator via `decodeAll`), decode exactly that frame, and buffer the remainder.
- The codec `receive()` should block until one complete frame is available and signal EOF **only** when the transport is actually closed — never on an idle timeout — so reader loops don't die on quiet.

#### -1 semantics (reader-loop side)

- The transport SPI contract is `-1` on close **or** timeout, and every transport (pipeline, in-memory, TLS wrapper) honors it. A reader loop that treats every `-1` as "connection lost" kills a live idle connection after one read timeout (5s on the pipeline transports).
- Disambiguate with `isOpen()`: `-1` while `transport.isOpen()` → idle timeout, keep waiting; `!isOpen()` → real connection loss. House pattern: AMQP `pollFrame` treats a short read as "no frame, try again".
- Handshake reads (first CONNECT / CONNECTED / CONNACK) need the same treatment: loop on short/empty reads while open, error only on `!isOpen()`.
- Fix the reader loop and the codec, not individual transports — in-memory transports deliberately return -1 on timeout, so per-transport patches hide the bug in production.
- Closing-peer side: an in-memory transport must drain queued inbound bytes before reporting EOF on `close()` — otherwise the peer's final control frame (auth rejection `-ERR`, protocol error) is discarded and the failure becomes a nondeterministic timeout race. The reader loop only works if the queued bytes are actually delivered first.

#### Partial-read ordering (in-memory transport side)

- When a read partially consumes a queued buffer, the remainder must stay at the stream head (a held head-buffer, exactly like TCP ordering), not be re-queued at the tail of the pending-send queue.
- A tail re-queue is invisible when each read consumes a whole buffer and when only one send is in flight — the classic unit-test shape. It corrupts the stream the moment a partial read and a second send interleave: the remainder reorders after newer bytes, and a frame split across reads reassembles out of order. Unlike the other two modes this one DOES surface in in-memory unit tests, so a targeted test catches it early.
- Regression test shape: `send(bufA)`; read part of A; `send(bufB)`; assert the rest of A arrives before B. Plus broker-side wire tests over the pair (partial length-prefix, fragmented body, coalesced frames) to catch it end-to-end.

#### Audit checklist (transport redesigns, codec work, interop failures)

- [ ] Codec reassembles coalesced multi-frame reads and frames split across reads (unit test: inject 2+ frames in one chunk; split a frame at a header boundary)
- [ ] Codec `receive()` blocks until a complete frame; returns null only when the transport is closed
- [ ] Every reader loop (client reader thread, broker per-connection loop, handshake) checks `isOpen()` before treating -1 as loss
- **In-memory transport drains queued bytes before EOF on close** (unit test: enqueue a failure frame, close, assert the reader receives it — not just a timeout)
- **In-memory transport holds a partial read's remainder at the stream head** (unit test: send A, read part of A, send B, assert the rest of A arrives before B)
- **TCP semantics are enabled by default** on `InMemoryByteTransport` — each `send()` splits into 256-byte chunks, `receive()` delivers one chunk at a time. Disable with `withTcpSemantics(TcpSemantics.NONE)` for deterministic synchronous behavior. Other modes: `COALESCING` (16 KB chunks) and `BACKPRESSURE` (bounded queue, 4 KB limit)
- **Pre-existing service-layer bugs vs. in-memory bugs**: When a service integration test fails over real TCP (not in-memory), the bug may be in the service layer, not the transport. Verify against the committed HEAD baseline before assuming regression. Use `git stash` + `git checkout` to run tests on a prior state rather than assuming the failure is caused by in-memory transport changes.
- [ ] Interop multi-message test (5+ small messages) passes — the canary for all three failure modes
