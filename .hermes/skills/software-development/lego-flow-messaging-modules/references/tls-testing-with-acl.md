# TLS Testing with ACL Module

## User Directive

> *"use for TLS tests ACL module (it is designed to help in these cases). Also take into account how ACL uses these especially for tests as there're some complexities which WERE RESOLVED in the module and should be taken into account when used in other modules."*

**NEVER** create custom certificate generation, SSL context code, or TLS handshake loops in protocol modules. The ACL module (`acl/`) provides everything.

## Setup

Add ACL as a test dependency in `pom.xml`:

```xml
<dependency>
    <groupId>ssg</groupId>
    <artifactId>lego-flow-acl</artifactId>
    <scope>test</scope>
</dependency>
```

## Generate Certificates

```java
// Generate CA + signed server/client certs with matching CN
var certs = CertificateFactory.generateDomainCerts("localhost", 2048, 10,
    "localhost", "server", "client");
var allCerts = certs.all();
var serverCert = certs.signedCerts().get(0);
var clientCert = certs.signedCerts().get(1);
```

## Create SSL Contexts & Engines

```java
char[] password = "changeit".toCharArray();

var serverContext = SslContexts.serverContext(serverCert, allCerts, password);
var serverEngine = SslContexts.serverEngine(serverContext, "localhost", 0);

var clientContext = SslContexts.clientContext(clientCert, allCerts, password);
var clientEngine = SslContexts.clientEngine(clientContext, "localhost", 0);

// Force TLS 1.2 (same as ACL reference test)
serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
```

## Handshake (MUST follow ACL pattern exactly)

**DO NOT use virtual threads.** They deadlock when both threads block on `poll()`.

```java
private SSLEngine[] handshake(SSLEngine server, SSLEngine client) throws Exception {
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

private boolean done(SSLEngine engine) {
    var s = engine.getHandshakeStatus();
    return s == SSLEngineResult.HandshakeStatus.FINISHED
        || s == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
}

private void process(SSLEngine engine, List<ByteBuffer> outQ, List<ByteBuffer> inQ,
                      int maxBuf, ByteBuffer empty) throws Exception {
    switch (engine.getHandshakeStatus()) {
        case NEED_WRAP: {
            var bb = ByteBuffer.allocate(maxBuf);
            var r = engine.wrap(empty, bb);
            if (r.bytesProduced() > 0) outQ.add((ByteBuffer) bb.flip());
            break;
        }
        case NEED_UNWRAP:
            if (!inQ.isEmpty()) engine.unwrap(inQ.remove(0), ByteBuffer.allocate(maxBuf));
            break;
        case NEED_TASK:
            runTasks(engine);
            if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                var bb = ByteBuffer.allocate(maxBuf);
                var r = engine.wrap(empty, bb);
                if (r.bytesProduced() > 0) outQ.add((ByteBuffer) bb.flip());
            }
            break;
    }
}

private void runTasks(SSLEngine engine) {
    var t = engine.getDelegatedTask();
    while (t != null) { t.run(); t = engine.getDelegatedTask(); }
}
```

## Post-Handshake Data Transfer

After handshake, wrap/unwrap data following the ACL `encryptedDataTransfer` pattern:

```java
// Client sends to server
int maxBuf = Math.max(clientEngine.getSession().getPacketBufferSize() + 32,
                      serverEngine.getSession().getPacketBufferSize() + 32);

// 1. Client wraps app data
var appData = ByteBuffer.wrap("Hello".getBytes());
var enc = ByteBuffer.allocate(maxBuf);
var wr = clientEngine.wrap(appData, enc);
enc.flip();

// 2. Copy encrypted data to queue (ALWAYS copy, never pass the same buffer)
var c2sQueue = new ArrayList<ByteBuffer>();
c2sQueue.add((ByteBuffer) ByteBuffer.allocate(enc.remaining()).put(enc).flip());

// 3. Server unwraps from the copy
var src = c2sQueue.remove(0);
var serverOut = ByteBuffer.allocate(maxBuf);
var ur = serverEngine.unwrap(src, serverOut);
assertThat(ur.bytesProduced()).isGreaterThan(0);
serverOut.flip();
assertThat(new String(serverOut.array(), 0, serverOut.remaining())).isEqualTo("Hello");
```

### With in-memory transport

When using `InMemoryTransport` pairs:

```java
// pair[0] sends to pair[1], pair[1] sends to pair[0]
var pair = InMemoryMqttTransport.createPair();

var serverTls = new MqttTlsTransport(pair[0], serverEngine);
var clientTls = new MqttTlsTransport(pair[1], clientEngine);

// Client sends → wraps → pair[1].outbound → pair[0].inbound → server reads
clientTls.send(ByteBuffer.wrap("Hello".getBytes()));

// Server reads encrypted bytes from pair[0]
var netBuf = ByteBuffer.allocate(maxBuf);
int n = pair[0].receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
assertThat(n).isGreaterThan(0);
netBuf.flip();

// Feed through server TLS layer
serverTls.onRead(null, netBuf);

// Server reads decrypted data
var buf = ByteBuffer.allocate(256);
int received = serverTls.receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
assertThat(received).isEqualTo(5);
buf.flip();
assertThat(new String(buf.array(), 0, received)).isEqualTo("Hello");
```

## Debugging `bad_record_mac`

If the first TLS direction works but the second fails with `(bad_record_mac) Tag mismatch`:

1. Check `send()` doesn't double-flip (InMemoryMqttTransport.send already flips)
2. Verify `receiveWithTimeout` returns the correct byte count
3. Ensure test uses a fresh `ByteBuffer` per direction (`netBuf.clear()` is NOT enough — allocate new or use `allocate()`)
4. Verify engines are handshaked with the SAME `setEnabledProtocols()` setting

## Reference

- `acl/src/test/java/ssg/legoflow/acl/ssl/SslContextsTest.java` — the definitive reference
- `acl/src/main/java/ssg/legoflow/acl/cert/CertificateFactory.java` — cert generation
- `acl/src/main/java/ssg/legoflow/acl/ssl/SslContexts.java` — context/engine factories
