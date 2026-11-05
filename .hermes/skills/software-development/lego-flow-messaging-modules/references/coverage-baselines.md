# Coverage Baselines & Testing Patterns

## Current Coverage (as of Sept 2026)

| Module | Line Coverage | Tests | Status |
|--------|--------------|-------|--------|
| MQTT | 80.0% | 352 | ✅ Target met |
| STOMP | ~78% | — | Pending TLS + coverage |
| AMQP | 78.8% | 355 | In progress (need ~37 lines) |
| NATS | 85.1% | 343 | ✅ Target met |
| XMPP | 81.7% | 283 | ✅ Target met |
| Kafka | 91.7% | 416 | ✅ Target met |

Target: 80%+ line coverage for all messaging modules.

Note: `mvn -P jacoco-coverage test` alone produces no report (the profile binds reporting to `prepare-package`) — always include the explicit `jacoco:report` goal, or run it as a second command. If a bare `jacoco:report` exits 0 but produces no `target/site/jacoco/` (the plugin goal isn't bound for that module), retry with the fully qualified GAV — `mvn -o org.jacoco:jacoco-maven-plugin:<version from root pom>:report -pl <module>` — and confirm success by checking the site directory, not the exit code. `jacoco.xml` (INSTRUCTION/BRANCH/LINE counters on `<counter>` elements) is parseable with ElementTree as an alternative to the CSV.

## Coverage Command

Gradle has no JaCoCo. Always use Maven:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/current
export PATH=$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH
mvn clean test jacoco:report -P jacoco-coverage -pl messaging/<module>
```

Both export lines are mandatory in a fresh terminal — there is no `mvnw` and no system Maven, so a bare `mvn` fails with `command not found` (and the failure can be masked as exit 0 if piped through `tail`). Add `-o` when the network is blocked; the dependency set is already in `~/.m2`.

Parse `target/site/jacoco/jacoco.csv` with Python `csv` module. Header row must be skipped. Columns: `GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...`. Skip rows where column 5 (branch mode) is `INTERFACE` or `ENUM`.

## TLS Transport Testing Patterns

### MQTT — MqttTlsTransportTest

- Uses `CertificateFactory.generateDomainCerts("localhost", ...)` from ACL module
- Forces TLS 1.2 by excluding TLS 1.3: `engine.setEnabledProtocols(new String[]{"TLSv1.2"})`
- Handshakes engines externally using **single-threaded alternating loop** (not virtual threads)
- Data flows via `onRead(channel, byte[])` push model — TLS transport no longer pulls from inner transport
- `InMemoryMqttTransport` conflates timeout and close as `-1`, so `receiveWithTimeout` reads from ring buffer filled by `onRead`

### ACL Reference

- `SslContextsTest.encryptedDataTransfer()` is the canonical pattern
- Copy encrypted bytes with `ByteBuffer.allocate(enc.remaining()).put(enc).flip()` before passing to unwrap
- Handshake uses `Queue<ByteBuffer>`, `c2s.add()` / `s2c.remove()` pattern
- TLS 1.2 forced, TLS 1.3 explicitly excluded

## Quick Coverage Wins

When near the 80% target (10-15 lines away), target:
1. **0% coverage small records** (2-3 lines) — simple `new` + assertion
2. **50% records** (1 missed line) — instantiate and call `type()` / accessor
3. **Encode/decode round-trip tests** — cover `encode()` and `decode()` methods in protocol classes
4. **Closed/edge-case paths** — `close()`, `send when closed`, `receive timeout`

## Packet Type Test Pattern

MQTT packet records at 50% (1 missed `type()` line each) — cover by instantiating:

```java
@Test void publishPacketType() {
    var pkt = new PublishPacket("t", "payload".getBytes(), QoS.AT_LEAST_ONCE, false, false, 0, new MqttProperties());
    assertThat(pkt.type()).isEqualTo(MqttPacketType.PUBLISH);
}
```

Check actual record constructor signature with `grep 'public record'` before writing — constructors differ (some need packet ID, some need `ConnectReturnCode`, etc.).
