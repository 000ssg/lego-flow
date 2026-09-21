# Phase 3 — XMPP compliance migration — implementation spec

Project: lego-flow (Java 25, Maven). Working dir: `/Users/sergey.sidorov/work/projects/github/lego-flow`, branch `cleanup-messaging`.

Compliance goal: protocol cores must NEVER touch `java.net.Socket`/`ServerSocket`/`SocketChannel`. Only the service layer (via `SelectableChannelManager`) owns sockets, in non-blocking mode. A sibling agent just did the identical migration for NATS (commit `ee36c4e2`) — that is the reference pattern.

## Build environment (prepend to EVERY mvn command; no mvnw/gradlew wrapper; mvn not on default PATH)

```
export JAVA_HOME=~/.sdkman/candidates/java/25.0.3-tem
export PATH=$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH
cd /Users/sergey.sidorov/work/projects/github/lego-flow
```
Use Maven **offline** (`-o`).
- Install: `mvn -q -B -o install -DskipTests -pl messaging/xmpp -am`
- Tests: `mvn -B -o test -pl messaging/xmpp`
- Coverage: `mvn -q -B -o verify -pl messaging/xmpp` (JaCoCo, gate 80%)

## READ THESE NATS REFERENCE FILES FIRST (the exact pattern to mirror)

```
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/transport/NatsTransport.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/transport/InMemoryNatsTransport.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/transport/PipelineNatsTransport.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/transport/TransportStreams.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/client/NatsClient.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/server/NatsServer.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/server/ClientConnection.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/service/NatsService.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/service/NatsServerService.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/service/NatsClientChannelHandler.java
messaging/nats/src/main/java/ssg/legoflow/messaging/nats/service/NatsServerChannelHandler.java
messaging/nats/src/test/java/ssg/legoflow/messaging/nats/service/NatsServiceIntegrationTest.java
messaging/nats/src/test/java/ssg/legoflow/messaging/nats/transport/NatsTransportTest.java
messaging/nats/src/test/java/ssg/legoflow/messaging/nats/server/InMemoryNats.java
```

## READ THESE CURRENT XMPP FILES (what you are migrating)

```
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/server/XmppServer.java           # raw ServerSocket + accept loop + per-client Socket read loop = the violation
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/client/XmppClient.java           # new XmppClient(); connect(config)->stream.open; login() simulated; sendMessage->stream.sendStanza; handleStanza() dispatches BUT client never registers as stream listener (fix it)
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/stream/XmppStream.java           # state machine; open() queues open XML; sendStanza() needs ACTIVE/BOUND; receiveData(ByteBuffer) decodes+notifies; drainOutbound(). KEEP no-transport ctor working for tests.
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/stream/XmppCodec.java            # regex incremental XML<->Stanza. Keep it (already transport-agnostic).
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/client/XmppClientConfig.java
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/client/service/XmppClientService.java     # current stub
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/server/service/XmppServerService.java     # currently `new XmppServer(port); server.start()` in doConnect
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/client/service/XmppClientChannelHandler.java  # stub
messaging/xmpp/src/main/java/ssg/legoflow/xmpp/server/service/XmppServerChannelHandler.java  # stub
```
Existing XMPP tests (268) to keep green + relevant ones:
`messaging/xmpp/src/test/java/ssg/legoflow/xmpp/client/XmppClientTest.java` (standalone: connect/login/sendMessage/listener), `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/stream/XmppStreamTest.java` (standalone: open/drainOutbound/state), plus the pure-logic tests (SASL, codec, roster, presence, MUC, pubsub, IoT, stream-mgmt) which should be unaffected.

## DESIGN (follow exactly; decisions already made)

1. **New package `ssg.legoflow.xmpp.transport`** with three files mirroring the NATS trio:
   - `XmppTransport.java`: byte-level SPI, identical shape to `NatsTransport` (`send(ByteBuffer)`, `receiveWithTimeout(buf,timeout,unit)`, `close()`, `isOpen()`, default `receive()`). Same "timeout is NEVER EOF" contract (a silent peer must not surface as -1; callers check `isOpen()`).
   - `InMemoryXmppTransport.java`: `createPair()`, identical to `InMemoryNatsTransport` (blocking queue per direction; close wakes blocked peer with 0-byte poison marker; buffered bytes still readable after close).
   - `PipelineXmppTransport.java`: identical to `PipelineNatsTransport` (DataChannel ring for inbound; outbound queue + OP_WRITE registration; `onRead`/`onWrite` called by pipeline; `send()` enqueues + wakes selector thread — never writes the channel from the calling thread).

2. **`XmppServer`**: remove ALL `java.net.Socket`/`ServerSocket` and the accept loop. Make it headless: keep `port()`, `addStanzaHandler(name,handler)`, `isRunning()`, `start()`/`close()`; add `handleConnection(XmppTransport)` that creates a per-connection object (its own `XmppCodec` + a virtual-thread read loop calling `transport.receiveWithTimeout`, and for each decoded stanza dispatches to all registered handlers). Mirror `NatsServer.handleConnection` + `ClientConnection`. Each connection gets its OWN codec (do not share one across connections).

3. **`XmppStream`**: keep the existing no-transport constructor AND behavior (so `XmppStreamTest` stays green: `open()` -> NEGOTIATING + queues open element; `drainOutbound()` returns queued buffers; state machine unchanged). Add an optional transport: a second constructor `XmppStream(codec, transport)`. When a transport is present, `open()`/`sendStanza()`/`closeStream()` additionally write their XML through the transport (keep also enqueuing to `outboundQueue` so `drainOutbound` still works). `receiveData(ByteBuffer)` is the inbound entry point — the client read loop calls it. Keep the state machine exactly as-is.

4. **`XmppClient`**: keep the no-arg constructor (standalone, no transport — `XmppClientTest` must still pass). Add a transport-injected constructor `XmppClient(XmppTransport)` that wires the stream to the transport and starts a virtual-thread read loop reading `transport.receiveWithTimeout` into a buffer and calling `stream.receiveData(buffer)`. **CRITICAL FIX**: in BOTH constructors register the client as the stream's stanza listener: `stream.addStanzaListener(this::handleStanza)` — currently the client never does this, so received stanzas never reach `addMessageListener`. This makes the receive path actually work.

5. **Service layer** (real TCP, manager-owned):
   - Rewrite `XmppClientService` to mirror `NatsService` (non-blocking `SocketChannel`, `manager.registerChannel`, OP_CONNECT, connect latch, then build `XmppClient(transport)`). Keep the `XmppResult` record + builder + `setStanzaCallback` + `createChannelHandler()`.
   - Rewrite `XmppServerService` to mirror `NatsServerService` (`ServerSocketChannel` + `ServerDataChannel` + `manager.registerServerChannel` + headless `XmppServer` + `getPort()` from the bound channel). Keep the `XmppResult` record + builder + `setStanzaCallback`.
   - Rewrite `XmppClientChannelHandler` to mirror `NatsClientChannelHandler` (connect latch; onRead/onWrite to transport; onDisconnect closes client).
   - Rewrite `XmppServerChannelHandler` to mirror `NatsServerChannelHandler` (per-connection `PipelineXmppTransport` in a `ConcurrentHashMap<DataChannel,transport>`; onConnect -> `server.handleConnection`; onRead/onWrite forward; onDisconnect closes).
   - IMPORTANT: use the EXACT same service-module API the NATS services use (`ctx.registerChannel` / `ctx.registerServerChannel` / `ctx.getChannelManager`). Do not invent service-module methods — read `service/src/main/java/ssg/legoflow/service/...` if unsure, and read the NATS services (already correct).

6. **Tests**:
   - Add `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/transport/XmppTransportTest.java` mirroring `NatsTransportTest` (in-memory pair send/receive, close semantics, timeout-vs-EOF).
   - Add `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/server/InMemoryXmpp.java` mirroring `InMemoryNats` (in-memory client+server pair helper over `InMemoryXmppTransport`).
   - Add `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/service/XmppServiceIntegrationTest.java` mirroring `NatsServiceIntegrationTest`: real TCP, `XmppServerService` binds port 0, `XmppClientService` connects, and a stanza the client sends is decoded by the server and delivered to a registered stanza handler (verify with `CountDownLatch`). Scope assertions to what the current protocol can actually do (client->server byte flow + server-side decode + handler dispatch). Do NOT require a full XMPP SASL/stream-feature handshake (the protocol is intentionally minimal and SASL is simulated).
   - Keep all 268 existing XMPP tests green. `XmppClientTest` and `XmppStreamTest` must still pass with the no-arg constructors.

7. **Demos + interop must still compile and run**:
   - `demos/src/main/java/ssg/legoflow/xmpp/demo/*.java` use `new XmppClient()` — keep them working (standalone constructor or in-memory seam; do NOT break them).
   - `interop-tests/src/test/java/ssg/legoflow/interop/xmpp/XmppInteropTest.java` connects a real client to a real Prosody server — migrate it to the real-TCP service layer (`XmppClientService`) like the NATS interop test, or the transport-injected `XmppClient` if cleaner. Must at minimum COMPILE and reflect the new architecture.

8. **Docs** (module describes CURRENT state, Mermaid not ASCII, no historical/versioning refs):
   - `messaging/xmpp/README.md`, `messaging/xmpp/doc/ARCHITECTURE.md`, `messaging/xmpp/doc/COMPLIANCE.md`, `messaging/xmpp/doc/REQUIREMENTS.md` — update to the headless/transport architecture (mirror how `messaging/nats/*.md` were rewritten). Use the real test count (268 + your new tests).
   - Do NOT touch `messaging/xmpp/AGENTS.md` unless necessary (protected file).
   - Update `doc/plans/messaging/PROGRESS.md` Phase 3 checkboxes to `[x]` for what you completed (transport SPI, in-memory, pipeline, server off ServerSocket, service layer, codec reassembly, tests >=80%; LEAVE "Commit Phase 3" unchecked until the parent commits) and add a Log line. Update `doc/plans/messaging/DECISIONS.md` with any new decisions (numbering continues from D10).

## VERIFICATION (run these yourself and report REAL output — never guess)

1. `mvn -q -B -o install -DskipTests -pl messaging/xmpp -am` -> must succeed.
2. `mvn -q -B -o compile -DskipTests -pl demos,interop-tests` -> must succeed (they depend on xmpp).
3. `mvn -B -o test -pl messaging/xmpp` -> capture the final `Tests run: X, Failures: Y, Errors: Z` + BUILD line; must be 0 failures/errors; count should be >= 268.
4. `mvn -q -B -o verify -pl messaging/xmpp` -> JaCoCo; confirm instruction coverage >= 80%.
5. `grep -r 'java.net.Socket\|ServerSocket\|new Socket(' messaging/xmpp/src/main/java` -> must return NOTHING for the protocol core. (The service layer MAY use `java.nio.channels.SocketChannel`/`ServerSocketChannel` non-blocking — that is allowed. The check is that the PROTOCOL CORE packages — server/, client/, stream/, auth/, core/, iot/, muc/, pubsub/, presence/, roster/, sm/, transport/ — have no `java.net.*` sockets.)

## CONSTRAINTS
- Only touch: `messaging/xmpp`, the xmpp demos under `demos`, the xmpp interop test under `interop-tests`, and `doc/plans/messaging` + xmpp module docs.
- Do NOT commit, do NOT push, do NOT change git config/remote.
- Follow AGENTS.md: dual API where natural, virtual threads for per-connection loops, `CountDownLatch` (not `sleep`) for test sync, no hidden failures (throw errors).
- If a sub-step is genuinely blocked, report exactly what and why — do NOT fabricate test results.

## REPORT BACK (precise, no fluff)
- list of files created/modified
- exact `Tests run: ...` and coverage numbers from a real run
- the grep result for raw sockets in the core
- any decisions you made
- any blockers
