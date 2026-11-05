---
title: Lego Flow Java Module Creation
name: lego-flow-java-module
description: Create a new lego-flow module with correct build setup.
trigger: Creating a new lego-flow module
---

# Lego Flow Java Module Creation

Use when creating a new Java module in the lego-flow project (protocol, utility, or infrastructure).

## Module Structure

Every module MUST have this structure:

```
module/
  pom.xml                     — Maven POM (groupId: ssg, artifactId: lego-flow-<name>)
  build.gradle.kts            — Gradle build (NO standalone java {} block — parent handles it)
  README.md                   — Features, quick-start, shields badges
  AGENTS.md                   — Module conventions (references root AGENTS.md)
  CLAUDE.md                   — Symlink: CLAUDE.md -> AGENTS.md
  doc/
    ARCHITECTURE.md           — Mermaid diagrams, design decisions
    REQUIREMENTS.md           — Append-only commit history
  src/main/java/ssg/legoflow/<name>/
  src/test/java/ssg/legoflow/<name>/
```

## Build System Alignment

### pom.xml essentials
```xml
<parent>
    <groupId>ssg</groupId>
    <artifactId>lego-flow</artifactId>
    <version>${legoFlowVersion}</version>
</parent>
<artifactId>lego-flow-<name></artifactId>
```
- groupId is ALWAYS `ssg`, never `ssg.legoflow`
- Parent POM handles Java toolchain (JDK 25) and common plugins
- Declare test dependencies explicitly: JUnit 5, AssertJ, SLF4J simple
- Add third-party deps (BouncyCastle, Jackson, SnakeYAML) as needed

### build.gradle.kts essentials
```kotlin
// DO NOT declare java {} toolchain block — parent build.gradle.kts handles this
dependencies {
    implementation("org.slf4j:slf4j-api:1.7.36")
    // ... other deps
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}
tasks.withType<Test> {
    // Add --add-opens only if reflecting into JDK internals
}
```
- Parent build handles Java toolchain, test config, JaCoCo, publishing
- Only declare deps and module-specific overrides
- See `references/jdk25-cert-generation.md` for `--add-opens` needs

### Registration
Add to root `pom.xml` `<modules>` and `settings.gradle.kts` `include()` before building.

## JDK 25 Compatibility Gotchas

1. **Certificate generation**: `sun.security.x509.X509CertImpl` is SEALED. `X509CertImplBuilder` is REMOVED. `SignatureAndKeyInfo` is REMOVED. **Must use BouncyCastle** (`bcprov-jdk18on` + `bcpkix-jdk18on`). See `references/jdk25-cert-generation.md`.
2. **`ByteArrayOutputStream.write()`** always declares `IOException` — must handle even though it never throws
3. **`Instant.plus()`** doesn't support `ChronoUnit.YEARS` — convert to seconds or use `LocalDate`
4. **`KeyPair.getPublic()` / `.getPrivate()`** — never `.public()` or `.private()`
5. **Record visibility**: package-private records used across packages must be in their own files

## In-Memory Transport TCP Semantics

`InMemoryByteTransport` supports configurable TCP semantics via `withTcpSemantics(TcpSemantics)`:

| Semantics | Chunk Size | Coalescing | Backpressure | Use Case |
|-----------|------------|------------|--------------|----------|
| `DEFAULT` | 256 bytes | No | No | Simulates TCP segmentation (default) |
| `NONE` | N/A | N/A | N/A | Raw synchronous queue behavior |
| `COALESCING` | 16 KB | Yes | No | Simulates TCP coalescing |
| `BACKPRESSURE` | 256 bytes | No | Yes (4 KB) | Simulates full send buffer |

**Enabled by default** — tests get TCP-like behavior out of the box. Disable with `TcpSemantics.NONE` for deterministic synchronous semantics.

See `references/in-memory-transport-tcp-semantics.md` for full details.

```java
// Default: chunked delivery (256-byte chunks)
var pair = InMemoryByteTransport.createPair();

// Disable TCP semantics for raw synchronous behavior
var pair = InMemoryByteTransport.createPair();
pair[0].withTcpSemantics(TcpSemantics.NONE);
pair[1].withTcpSemantics(TcpSemantics.NONE);

// Enable backpressure simulation
var pair = InMemoryByteTransport.createPair();
pair[0].withTcpSemantics(TcpSemantics.BACKPRESSURE);
pair[1].withTcpSemantics(TcpSemantics.BACKPRESSURE);
```

**Async delivery:** With TCP semantics enabled (DEFAULT, COALESCING, BACKPRESSURE), data is delivered asynchronously via a background daemon thread. Use `transport.waitForDelivery(timeout, unit)` to wait for all sent data to reach the peer's inbound queue.

**Protocol-specific InMemoryTransport:** Each protocol defines its own `InMemoryTransport` subclass. The `createPair()` method MUST set `peerRef` on both ends — failing to do so breaks bidirectional communication (sends on one end never reach the other). Always construct the subclass directly (do NOT use `InMemoryByteTransport.createPair()` and cast, which causes `ClassCastException`):

```java
public static InMemoryTransport[] createPair() {
    var q1 = new LinkedBlockingQueue<ByteBuffer>();
    var q2 = new LinkedBlockingQueue<ByteBuffer>();
    var a = new InMemoryTransport(q1, q2);
    var b = new InMemoryTransport(q2, q1);
    a.peerRef = b;
    b.peerRef = a;
    return new InMemoryTransport[]{a, b};
}
```

## Service Integration Test Patterns

For service integration tests using real TCP through `SelectableChannelManager`:

1. **Wait for server to be ready** before connecting client:
```java
int retries = 20;
while (retries-- > 0) {
    if (server.getServer() != null && server.getServer().isRunning()) break;
    Thread.sleep(100);
}
```

2. **Wait for handshake to drain** before assertions:
```java
long deadline = System.currentTimeMillis() + 2_000;
while (System.currentTimeMillis() < deadline) {
    var transport = client.getClient().transport();
    if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
    Thread.sleep(50);
}
```

3. **Wait for protocol operations** (e.g., SUB before PUB):
```java
long subDeadline = System.currentTimeMillis() + 2_000;
while (System.currentTimeMillis() < subDeadline) {
    var transport = client.getClient().transport();
    if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
    Thread.sleep(50);
}
```

4. **Use 10-second timeouts** for TCP round-trips:
```java
assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
```

## Third-Party Dependencies (common)
| Need | Maven | Gradle |
|------|-------|--------|
| Certs/SSL | `bcprov-jdk18on` + `bcpkix-jdk18on` | same |
| JSON | `jackson-databind:2.18.2` | same |
| YAML | `org.yaml:snakeyaml:2.3` | same |
| XML/JAXB | `jakarta.xml.bind-api:4.0.2` + `jaxb-runtime:4.0.5` | same |
