# TLS Transport Design & In-Memory Queue Wiring

## MqttTlsTransport Lazy Handshake

**Problem**: The original design blocked the constructor on `doHandshake()`. With in-memory transports (both sides in same JVM), this deadlocks — each side waits for peer data that arrives only after the peer's constructor returns.

**Fix**: Lazy handshake. Constructor calls `beginHandshake()` and does the initial `wrap()` (ClientHello/ServerHello). Subsequent handshake rounds are driven by `onRead()`/`unwrapAndAdd()` callbacks from the pipeline.

**Constructor pattern**:
```java
public MqttTlsTransport(MqttTransport inner, SSLEngine engine) {
    this.inner = inner;
    this.engine = engine;
    // Skip beginHandshake() if engine was pre-handshaked (e.g. external handshake for tests)
    if (engine.getHandshakeStatus() != NOT_HANDSHAKING) {
        try { engine.beginHandshake(); }
        catch (SSLException e) { throw new RuntimeException("TLS handshake init failed", e); }
    }
    handshakeComplete = (engine.getHandshakeStatus() == NOT_HANDSHAKING);
    if (!handshakeComplete) {
        // Do initial wrap to send ClientHello/ServerHello
        doInitialWrap(); // wraps and sends first handshake record via inner.send()
    }
}
```

**Broker channel handler routing**:
- `onRead()` goes through outer transport. If it's `MqttTlsTransport`, call `tls.onRead()` (drives handshake + unwrap). If it's `PipelineTransport`, call `pt.onRead()`.
- `onWrite()` goes through `PipelineTransport.onWrite()` (pipeline drains outbound queue to channel).
- Store two maps: `transportByChannel` (outer TLS or pipeline) and `pipelineByChannel` (always pipeline).

**ByteBuffer contract for `unwrapAndAdd()`**: Caller MUST set `position=0, limit=n` (ready to read). Do NOT flip inside `unwrapAndAdd()` — doing so double-flips and empties the buffer when called from `receiveWithTimeout()`.

## unwrapAndAdd Must Handle BUFFER_UNDERFLOW

`SSLEngine.unwrap()` can return `BUFFER_UNDERFLOW` when it consumed all input bytes but needs more to complete an SSL record. The fix: just return and wait for the next `onRead()` call. The SSLEngine buffers internally — no external stash needed.

```java
SSLEngineResult res = engine.unwrap(netData, appData);
if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
    return; // Engine consumed all input, needs more
}
```

## receiveWithTimeout Timeout/Closed Conflation

`InMemoryMqttTransport.receiveWithTimeout()` returns -1 on BOTH timeout (queue empty) AND close (peer closed). Cannot distinguish from the return value alone.

**Fix**: Do NOT use `receiveWithTimeout` for the inner transport in TLS receive loop. In the pipeline model, data is pushed via `onRead()`. The TLS transport's `receiveWithTimeout()` only reads from its own ring buffer (filled by `onRead` → `unwrapAndAdd`), blocking on a semaphore. This avoids the timeout/closed conflation entirely.

## TLS Test Data Flow (Push-Based)

In tests, the TLS transport receives data via `onRead(null, netBuf)`:
```java
clientTls.send(ByteBuffer.wrap("Hello".getBytes()));
var netBuf = ByteBuffer.allocate(16384);
int n = serverInner.receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
assertThat(n).isGreaterThan(0);
netBuf.flip(); // position=0, limit=n
serverTls.onRead(null, netBuf); // push encrypted bytes through TLS layer
var buf = ByteBuffer.allocate(256);
int received = serverTls.receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
assertThat(received).isEqualTo(5);
```

## External Handshake Test Pattern (ACL — Single-Threaded Alternating)

**CRITICAL: Use the single-threaded alternating loop from `SslContextsTest`, NOT two virtual threads with queues.** The virtual thread approach deadlocks because both threads block on `poll()` waiting for the other.

From `SslContextsTest.handshake()` (the authoritative pattern):
```java
SSLEngine[] handshake(SSLEngine server, SSLEngine client) throws Exception {
    server.beginHandshake();
    client.beginHandshake();
    int maxBuf = Math.max(client.getSession().getPacketBufferSize() + 32,
                          server.getSession().getPacketBufferSize() + 32);
    var empty = ByteBuffer.allocate(0);
    var c2s = new ArrayList<ByteBuffer>();
    var s2c = new ArrayList<ByteBuffer>();
    while (!(done(server) && done(client))) {
        process(client, c2s, s2c, maxBuf, empty);
        process(server, s2c, c2s, maxBuf, empty);
    }
    return new SSLEngine[]{server, client};
}
```

- Uses `ArrayList` (not `LinkedBlockingQueue`) for encrypted data queues
- Runs entirely on one thread, alternating between client and server — no deadlock
- After external handshake, both engines are in `NOT_HANDSHAKING` state
- **Do NOT use `TestDomain.INSTANCE`** — its certs have CN="Test". Use `CertificateFactory.generateDomainCerts("localhost", ...)` for matching CN.
- **JDK 25**: `runDelegatedTasks()` doesn't exist — use `getDelegatedTask()` loop. `bytesDecoded()` removed — use `bytesProduced()`.

## In-Memory Transport Queue Wiring

`InMemoryMqttTransport.createPair()` creates:
- `q1`, `q2` — two `LinkedBlockingQueue<ByteBuffer>`
- `pair[0]` — reads from q1, writes to q2 (server transport)
- `pair[1]` — reads from q2, writes to q1 (client transport)

**Data flow**:
- Client sends → `pair[1].send()` → puts in q1 → `pair[0].receive()` reads (server gets client data)
- Server sends → `pair[0].send()` → puts in q2 → `pair[1].receive()` reads (client gets server data)

**Handshake pump loop** (when doing external handshake + in-memory routing):
- Server side reads from `pair[0]` (reads q1 = client's outbound)
- Client side reads from `pair[1]` (reads q2 = server's outbound)

**Common bug**: Reversing the pair indices in the pump loop. Always verify: `pair[0].receive()` reads client→server direction.

## ByteBuffer Position/Flip Pitfalls

When `InMemoryMqttTransport.receiveWithTimeout()` copies data into the caller's buffer, it leaves `position=n, limit=capacity`. The caller must set `position=0, limit=n` before passing to `unwrap()`.

```java
int n = inner.receiveWithTimeout(netBuf, timeout, unit);
if (n > 0) {
    netBuf.limit(n);   // Set limit to bytes read
    netBuf.position(0); // Set position to start
    unwrapAndAdd(netBuf); // unwrapAndAdd does NOT flip — expects position=0
}
```

**Double-flip bug**: If `receiveWithTimeout` flips and `unwrapAndAdd` also flips, the second flip reverses the first, resulting in `position=limit` (empty buffer). Always have exactly ONE flip between read and read operation.