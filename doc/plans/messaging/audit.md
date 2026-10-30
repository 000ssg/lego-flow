# Messaging Compliance Audit

Date: 2026-09-18 · Branch: `cleanup-messaging` · Method: read of `src/main` source + test counts.
This is the **ground truth** the plan's phases act on. Re-verify before each migration commit.

## Reference pattern (the bar, from MQTT/STOMP/AMQP, already compliant in WAMP)

A messaging module is **compliant** iff it has **all six**:

| # | Criterion | What it looks like | Where to check |
|---|-----------|--------------------|----------------|
| 1 | Byte-level **Transport SPI** in `src/main` | `XxxTransport { send(ByteBuffer); receive(ByteBuffer); receiveWithTimeout(buf,long,unit); close(); isOpen(); }` | `.../transport/XxxTransport.java` |
| 2 | **InMemory** transport `createPair()` | two connected byte transports, `BlockingQueue` per direction, no network | `.../transport/InMemoryXxxTransport.java` |
| 3 | **Pipeline** transport | DataChannel + ring buffer (read) + outbound queue (write); **never writes the socket from the calling thread** | `.../transport/PipelineXxxTransport.java` |
| 4 | **Service layer drives I/O** | `XxxClientService`/`XxxServerService` extend `AbstractService`; `ChannelHandler` bridges events → transport; TCP lifecycle in `SelectableChannelManager` | `.../service/` |
| 5 | **Codec stream reassembly** | frame codec accumulates bytes; handles a frame split across N reads **and** multiple frames in one read | `.../codec/` or `.../stream/` |
| 6 | **Test location + coverage** | unit + in-process in `<module>/src/test`; Docker interop only in `interop-tests`; ≥80% line coverage | both + JaCoCo |

The **golden rules** carried over from MQTT/STOMP/AMQP (must hold in every migration):
- **No `import java.net.Socket / ServerSocket / nio.channels.{Socket,ServerSocket}Channel / Selector / SelectionKey`** in a module's `src/main` — except the transport/adapter classes that the **service** module's `DataChannel` wraps. The *protocol core* never imports NIO.
- **Never write the socket from the calling thread** — enqueue to the outbound queue and let the selector thread's `OP_WRITE` drain it (the STOMP/AMQP/MQTT rule).
- **Never trust a single read** — reassemble (the STOMP `findFrameEnd` lesson).

## Per-module findings

### ✅ mqtt — BASELINE (do not rebuild; verify only)
- SPI `MqttTransport`, `InMemoryMqttTransport`, `PipelineTransport` ✓; `MqttClientService`/`MqttBrokerService` ✓;
  codec accumulator + `decodeAll` ✓. 355 tests. **Compliant.**

### ✅ stomp — BASELINE (do not rebuild; verify only)
- SPI `StompTransport`, `InMemoryTransport`, `PipelineTransport` ✓; `StompClientService`/`StompServerService` ✓;
  `StompFrameCodec` stream reassembly (rewritten 2026-09-18) ✓. 233 tests. **Compliant.**

### ✅ amqp — BASELINE (do not rebuild; verify only)
- SPI `AmqpTransport`, `InMemoryTransport`, `PipelineTransport` ✓; `AmqpClientService`/`AmqpContainerService` ✓;
  frame codec reassembly ✓. ~640 tests. **Compliant.**

### ✅ wamp — BASELINE (do not rebuild; verify only)
- SPI `WampTransport` (core), `WebSocketWampTransport` adapter, InMemory in demo base ✓; `WebSocketWampService`
  drives sessions ✓. WAMP is **WebSocket-bound** (message boundaries per WS frame), so byte-reassembly is not
  applicable — it uses WS frame boundaries. 295 tests. **Compliant** (noted: no raw-socket adapter, only WS).

### ❌ nats — MIGRATE (Phase 2)
- **Raw sockets in `src/main`:** `client/NatsClient.java:76` `new Socket()` + `.connect(...)`; `server/NatsServer.java:124`
  `new ServerSocket()` + `.bind(...)`. ❌
- **No** `NatsTransport` SPI, **no** `InMemoryNatsTransport`, **no** `PipelineNatsTransport`.
- **Service layer is a stub:** `service/NatsService.java:44` still does `new NatsClient(host,port)`;
  `NatsServerService` does not drive I/O via the manager.
- **Codec reassembly:** `NatsCodec` is **line-oriented** (`readOp(BufferedReader)`, size-based payload read).
  Reassembly is *reader-based* (BufferedReader accumulates) — **adequate**, but must be adapted to feed from a
  byte-level transport instead of a blocking `BufferedReader`.
- 271 tests. **Refactor target.**

### ❌ xmpp — MIGRATE (Phase 3)
- **Raw socket in `src/main`:** `server/XmppServer.java:59` `new ServerSocket()` + `.bind(...)`. ❌
- **No** `XmppTransport` SPI, **no** `InMemoryXmppTransport`, **no** `PipelineXmppTransport`.
- **Service layer is a stub:** `server/service/XmppServerService.java:35` does `new XmppServer(port)` (the blocking
  one); client service likewise.
- **Codec reassembly: PRESENT and correct** — `stream/XmppCodec.java` keeps a `StringBuilder buffer` + `furthestEnd`
  and accumulates partial stanzas, deleting only up to the furthest fully-parsed stanza. ❌→✅ (no rework needed,
  just feed it from the transport).
- ~33 tests (small). **Refactor target** — also a coverage lift (needs ≥80%).

### ❌ kafka — MIGRATE (Phase 4, LARGEST)
- **Raw sockets in `src/main`:** `broker/KafkaBroker.java:123` `ServerSocketChannel.open()` + accept loop (line 300);
  `client/KafkaConnection.java:37` `SocketChannel.open()` + `.connect(...)`. ❌
- **No** `KafkaTransport` SPI, **no** `InMemoryKafkaTransport`, **no** `PipelineKafkaTransport`, **no service layer**.
- **Codec reassembly: present but in the WRONG PLACE** — the 4-byte length-prefix framing is done **inline in
  `KafkaBroker.handleConnection`** (`readFully(client, lenBuf)` then `readFully(client, msgBuf)`, lines 317–330),
  i.e. blocking socket reads in the broker, not a transport. `KafkaCodec` itself is clean `ByteBuffer` encode/decode
  per API. → the length-prefix framing must **move into** `PipelineKafkaTransport` / `InMemoryKafkaTransport`.
- 399 tests. **Refactor target** — split client path and broker path across two commits.

## Migration work estimate (rough, straight mirroring)

| Module | New SPI | New InMemory | New Pipeline | Refactor core | Service layer | Coverage gap |
|--------|---------|--------------|--------------|---------------|---------------|--------------|
| nats | `NatsTransport` | `InMemoryNatsTransport` | `PipelineNatsTransport` | `NatsClient`, `NatsServer` | wire into manager | fill to 80% |
| xmpp | `XmppTransport` | `InMemoryXmppTransport` | `PipelineXmppTransport` | `XmppServer` | wire into manager | fill to 80% (small base) |
| kafka | `KafkaTransport` | `InMemoryKafkaTransport` | `PipelineKafkaTransport` | `KafkaConnection`, `KafkaBroker` | **new** service layer | fill to 80% |

## Commands used to produce this audit
- `grep -rn "Socket|Selector|SelectionKey|InetSocketAddress|\.open()" messaging/*/src/main`
- `find messaging/*/src/main -name "*Transport*.java"` (SPI + InMemory + Pipeline presence)
- per-module `grep` on the service-layer classes to confirm whether they drive I/O or delegate to a blocking socket
- `find ... -name '*Test.java' | wc -l` per module (test counts in the plan table)
