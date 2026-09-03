# ACL Module Test Guide

## Using Test Certificates and Domains

The `acl` module provides test infrastructure for SSL/TLS, SSH keys, and access control. When testing SSL functionality in other modules, reuse the `CertificateFactory` and `DomainCerts` from this module instead of creating your own certificate generation code.

### Certificate Generation

```java
// Generate CA + server + client certs for a named domain
DomainCerts certs = CertificateFactory.generateDomainCerts("MyDomain", 2048, 10, "server", "client");

// Get individual certs
CertificateEntry serverCert = certs.signedCerts().get(0);  // server
CertificateEntry clientCert = certs.signedCerts().get(1);  // client

// Get all certs (CA + server + client) — use as trust store
List<CertificateEntry> allCerts = certs.all();
```

### Creating SSL Contexts

```java
char[] password = "changeit".toCharArray();

// Server context
var serverCtx = SslContexts.serverContext(serverCert, allCerts, password);

// Client context
var clientCtx = SslContexts.clientContext(clientCert, allCerts, password);

// Trust-only (no client cert)
var trustOnlyCtx = SslContexts.trustOnlyContext(allCerts, password);

// Engines
var serverEngine = SslContexts.serverEngine(serverCtx, "localhost", 443);
var clientEngine = SslContexts.clientEngine(clientCtx, "localhost", 443);
var clientAuthEngine = SslContexts.clientAuthServerEngine(serverCtx, "localhost", 443);
```

### In-Memory Handshake Tests

Reference: `src/test/java/ssg/legoflow/acl/ssl/SslContextsTest.java`

Use the `process()` pattern for in-memory SSLEngine handshake. Two queues (`c2s`, `s2c`) pass encrypted bytes between engines. Loop until both report `NOT_HANDSHAKING`.

#### Handshake (no data transfer)

```java
handshake(serverEngine, clientEngine);
assertThat(serverEngine.getSession().getProtocol()).isNotNull();
```

#### Data Transfer After Handshake

1. Force TLS 1.2 before `beginHandshake()` — TLS 1.3 `HelloRetryRequest` breaks the simple loop
2. Run handshake loop until both engines are `NOT_HANDSHAKING`
3. Client wraps application data → queue → server unwraps

```java
serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});

serverEngine.beginHandshake();
clientEngine.beginHandshake();

int maxBuf = Math.max(clientEngine.getSession().getPacketBufferSize() + 32,
                      serverEngine.getSession().getPacketBufferSize() + 32);
var c2s = new ArrayList<ByteBuffer>();
var s2c = new ArrayList<ByteBuffer>();
var empty = ByteBuffer.allocate(0);

// Handshake loop
while (!(done(server) && done(client))) {
    process(client, c2s, s2c, maxBuf, empty);
    process(server, s2c, c2s, maxBuf, empty);
}

// Data: client wraps → server unwraps
var appData = ByteBuffer.wrap("Hello SSL".getBytes());
var enc = ByteBuffer.allocate(maxBuf);
clientEngine.wrap(appData, enc);
enc.flip();
c2s.add((ByteBuffer) ByteBuffer.allocate(enc.remaining()).put(enc).flip());

var src = c2s.remove(0);
var serverOut = ByteBuffer.allocate(maxBuf);
serverEngine.unwrap(src, serverOut);
serverOut.flip();
```

### SSH Key Generation

Reference: `src/test/java/ssg/legoflow/acl/key/SshKeyGeneratorTest.java`

`SshKeyGenerator` supports RSA, Ed25519, and ECDSA. BC provider is auto-registered.

```java
var rsa = SshKeyGenerator.generate("RSA");
var ed25519 = SshKeyGenerator.generate("Ed25519");
var ecdsa = SshKeyGenerator.generate("ECDSA");
```

### Known Issues

- **JaCoCo**: Exclude this module from JaCoCo coverage. The parent build's JaCoCo agent modifies bytecode and causes `SSLEngine` handshake infinite loops with BouncyCastle in Gradle.
- **TLS 1.3 in tests**: Force TLS 1.2 for in-memory handshake tests. TLS 1.3 `HelloRetryRequest` requires additional state machine handling not supported by the simple `process()` loop.
