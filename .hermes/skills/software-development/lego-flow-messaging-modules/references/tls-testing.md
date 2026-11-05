# TLS Testing in Lego Flow Messaging Modules

## Reference Pattern: ACL SslContextsTest

All TLS testing for MQTT, STOMP, and AMQP follows the ACL module's `SslContextsTest` pattern. Do not invent your own handshake or data flow patterns.

## Creating Test Certificates

Generate per-test certificates using `CertificateFactory.generateDomainCerts()`:

```java
var certs = CertificateFactory.generateDomainCerts("localhost", 2048, 10,
    "localhost", "server", "client");
var ca = certs.ca();
var serverContext = SslContexts.serverContext(ca.certificate(), ca.privateKey(),
    certs.all(), "changeit".toCharArray());
var clientContext = SslContexts.clientContext(ca.certificate(),
    certs.all(), "changeit".toCharArray());
```

Or use the pre-built `TestDomain.INSTANCE` (domain "Test"):

```java
var domain = TestDomain.INSTANCE;
var admin = domain.user("admin").get();
var cert = admin.certificates().iterator().next();
var sslCtx = SslContexts.serverContext(cert, domain.certificates(),
    "changeit".toCharArray());
```

## Forcing TLS Version

**Always force TLS 1.2** to avoid MAC mismatches in in-memory tests:

```java
serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
```

TLS 1.3 can cause `bad_record_mac` / `decrypt_error` in alternating single-thread handshake loops.

## Handshake Pattern (single-thread alternating)

From `SslContextsTest.handshake()`:

```java
void handshake(SSLEngine serverEngine, SSLEngine clientEngine) throws Exception {
    boolean running = true;
    while (running) {
        boolean oneMore = false;
        for (var engine : List.of(serverEngine, clientEngine)) {
            while (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED) {
                var status = engine.getHandshakeStatus();
                if (status == NEED_WRAP) {
                    wrapAndRoute(engine);
                    oneMore = true;
                } else if (status == NEED_UNWRAP) {
                    unwrapAndRoute(engine);
                    oneMore = true;
                } else if (status == NEED_TASK) {
                    // Run delegated tasks
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) task.run();
                    oneMore = true;
                } else {
                    break;
                }
            }
        }
        if (!oneMore) running = false;
    }
}
```

**Key**: Single thread, alternating between engines. Two virtual threads deadlock on `poll(5s)`.

## Post-Handshake Data Transfer

After handshake, use the same `wrap`/`unwrap` pattern with `NEED_WRAP`/`NEED_UNWRAP` in a loop until `FINISHED`:

```java
void wrapAndRoute(SSLEngine engine, List<ByteBuffer> outputQueue) throws SSLException {
    var plain = ByteBuffer.allocate(256); // pre-filled with app data
    var enc = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
    var result = engine.wrap(plain, enc);
    if (result.getStatus() == OK || result.getStatus() == CLOSED) {
        enc.flip();
        outputQueue.add((ByteBuffer) enc.slice());
    }
}
```

## Common Pitfalls

1. **Don't call `runDelegatedTasks(S -> {})`** — use the `getDelegatedTask()` loop pattern.
2. **Don't double-flip ByteBuffers** — `wrap()` leaves position after data; flip once before routing.
3. **Don't assume TLS preserves message boundaries** — multiple sends can arrive as one unwrap.
4. **MqttTlsTransport uses push model** — `onRead(channel, data)` pushes raw bytes; `unwrapAndAdd` handles SSL record boundaries internally.
