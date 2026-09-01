# Lego Flow Development Guide

This document describes the development practices, patterns, and conventions used in the Lego Flow project.

## Project Overview

**Lego Flow** is a composable data processing framework for Java built on JDK 25. It provides layered abstractions for building data-driven services: composable blocks (DP/DF), service orchestration, HTTP protocol, web services, and WAMP protocol support.

### ⚠️ CRITICAL: YAML Multi-Line Commands in GitHub Actions — FOLDING BUG

**This bug has caused 3+ CI failures. ALWAYS follow this rule when editing `.github/workflows/*.yml`:**

**NEVER use backslash line continuations (`\\`) in `run:` blocks.** Always write multi-line
shell commands on a single line.

#### Why It Breaks:
GitHub Actions uses YAML implicit folding (no block scalar `|`). Folding converts newlines to
spaces but **keeps backslashes literally**. So bash sees `\` + space as an *escaped space*,
making the next argument start with a literal space character:

```yaml
# ❌ BROKEN — YAML folding converts this to:
# mvn -B verify " --no-daemon" "-DskipInteropTests=false" ...
run: mvn -B verify \
  --no-daemon \          # ← becomes ' --no-daemon' (leading space!)
  -DskipInteropTests=false

# ✅ CORRECT — single line, no backslashes:
run: mvn -B verify --no-daemon -DskipInteropTests=false ...
```

#### Symptom (what CI reports):
```
Unknown lifecycle phase " --no-daemon". You must specify a valid lifecycle phase...
Task ' --no-daemon' not found in root project 'lego-flow'...
```

**Affected commits**: 9c83b88, 33b5830, b4f10b5, 39fd980 — the same bug reappeared every time
CI commands were reformatted with multi-line YAML.

#### Safe Alternatives for Very Long Commands:
1. **Single line** (preferred when under ~200 chars): `run: ./gradlew task1 task2 -Pflag=value ...`
2. **Shell heredoc**: Use `shell: bash` with a `$'...'` quoting style
3. **Environment variable**: Set command in env block, then reference `${{ env.CMD }}`

**When editing any CI workflow file, scan ALL `run:` blocks for backslash continuations and
convert them to single lines.**

---

## Development Practices

### 1. Requirements Documentation

**Primary Rule:** All requirements, design decisions, and their evolution MUST be tracked in `doc/REQUIREMENTS.md`.

#### When Adding Features:
1. **Document Original Request**: Add verbatim user request at the start of each commit section
2. **Reformulate Requirements**: List clear, specific technical requirements
3. **Final Design Decisions**: Document what was chosen and why
4. **Implementation Details**: List files changed, features added, performance metrics
5. **Test Coverage**: Document tests added and total test count

#### REQUIREMENTS.md Structure:
```markdown
## Commit: `<hash>` - <Feature Name> (Date)

### Original Request
> "verbatim user request from conversation or discussion transcript"

### Reformulated Requirements
1. Specific technical requirement
2. Another requirement
...

### Final Design Decisions
- Architectural choices with rationale
- Trade-offs considered

### Implementation Details
- Files modified/created
- Key features implemented

### Test Coverage
- New tests added
- Total tests passing
```

### 1b. Cost Estimate Documentation

**Every commit** must include a `### Cost Estimate` section in the module's `doc/REQUIREMENTS.md` entry:

```markdown
### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | N (list names) |
| Agent tokens | ~X |
| Agent tool calls | ~Y |
| Agent wall time | ~Z min |
| Files created/modified | N |
| Lines added/removed | +A / -B |
| Tests added | N (total: M) |
```

This section goes after `### Test Coverage` in each commit's REQUIREMENTS.md entry.

### 2. Architecture Documentation

**ARCHITECTURE.md** (`doc/ARCHITECTURE.md`) documents the **current** set of architectural decisions.

- **Mandatory update** on every commit with architectural changes
- Unlike REQUIREMENTS.md (append-only, historical), ARCHITECTURE.md is **edited in place** to reflect the latest state
- Sections: module purpose, key abstractions, design patterns, data flow, dual API approach, extension points, thread safety model

### 3. Git Commit Practices

#### Commit Message Format:
```
<Title: Brief summary (max 72 chars)>

<Detailed description of changes>

- Bullet points for key changes
- Implementation highlights
- Test additions

Co-Authored-By: AI assistant
```

#### Commit Workflow:
1. Stage changes: `git add <files>`
2. Commit with detailed message using heredoc for proper formatting
3. Always include `Co-Authored-By: AI assistant`
4. **Update doc/REQUIREMENTS.md** with commit documentation
5. **Update doc/ARCHITECTURE.md** if architectural changes were made
6. **Update README.md**: reflect any API changes, new features, updated module structure, version badges, test counts
7. **NEVER run `git push` automatically.** Inform the user and wait for explicit instruction.

> **MANDATORY DOCUMENTATION RULE**: Steps 4–6 are not optional. Every commit must include up-to-date versions of REQUIREMENTS.md, README.md, and ARCHITECTURE.md (if architecture changed).

### 4. Document Locations

- `README.md` — module root directory (e.g., `blocks/README.md`)
- `AGENTS.md` — module root directory (e.g., `blocks/AGENTS.md`)
- `doc/REQUIREMENTS.md` — in `<module>/doc/` directory
- `doc/ARCHITECTURE.md` — in `<module>/doc/` directory

### 5. Code Organization

#### Module Layout:
```
lego-flow/                         ← root POM (packaging=pom)
├── blocks/                        ← core DP/DF data processing framework
├── service/                       ← service-oriented framework using blocks (TCP + UDP)
├── web/                           ← Web & HTTP protocols
│   ├── http/                      ← HTTP/HTTPS protocol (RFC 2616)
│   ├── http2/                     ← HTTP/2 protocol (RFC 7540/RFC 9113)
│   ├── http3/                     ← HTTP/3 protocol (RFC 9114) over QUIC (RFC 9000)
│   ├── web-services/              ← web service components for HTTP
│   └── http-proxy/                ← HTTP forward/reverse/caching proxy (RFC 7230 §5.7)
├── iot/                           ← Internet of Things & device discovery
│   ├── upnp/                      ← UPnP/DLNA (SSDP, SOAP, GENA, media server/renderer/control point)
│   └── coap/                      ← CoAP (RFC 7252, observe, blockwise, resource discovery)
├── auth/                          ← Authentication parent module
│   ├── gssapi/                    ← GSS-API / Kerberos V5 / SPNEGO primitives (shared)
│   └── http-auth/                 ← HTTP authentication (5 sub-modules)
│       ├── core/                  ← Auth framework, sessions, JWT from scratch
│       ├── basic-digest/          ← HTTP Basic (RFC 7617) + Digest (RFC 7616)
│       ├── oauth/                 ← OAuth 2.0, PKCE, OpenID Connect, provider templates
│       ├── sso/                   ← Reverse proxy SSO, SAML assertion parsing
│       └── spnego/                ← HTTP Negotiate / SPNEGO (RFC 4559)
├── messaging/                     ← Message brokers & event streaming
│   ├── kafka/                     ← Apache Kafka wire protocol
│   ├── amqp/                      ← AMQP 1.0 (ISO 19464)
│   ├── stomp/                     ← STOMP 1.2
│   ├── nats/                      ← NATS core + JetStream
│   ├── mqtt/                      ← MQTT v3.1.1/v5.0 (pub/sub, QoS, broker + client)
│   ├── xmpp/                      ← XMPP (RFC 6120/6121, presence, messaging, IoT extensions)
│   └── wamp/                      ← WAMP protocol (invariant core + WebSocket adapter)
├── rpc/                           ← RPC & API protocols
│   ├── grpc/                      ← gRPC (HTTP/2 + protobuf)
│   └── graphql/                   ← GraphQL (HTTP + WebSocket)
├── database/                      ← Database wire protocols
│   ├── redis/                     ← Redis RESP2/RESP3
│   ├── postgresql/                ← PostgreSQL v3 frontend/backend
│   └── mysql/                     ← MySQL client/server
├── email/                         ← Email protocols
│   ├── common/                    ← Shared MIME parsing (RFC 2045-2049)
│   ├── smtp/                      ← SMTP (RFC 5321)
│   └── imap/                      ← IMAP4rev2 (RFC 9051)
├── network/                       ← Network services & management
│   ├── common/                    ← Shared BER/ASN.1 codec
│   ├── dns/                       ← DNS (RFC 1034/1035 + DNSSEC + DoH + DoT)
│   ├── ldap/                      ← LDAP v3 (RFC 4511)
│   ├── snmp/                      ← SNMP v3 (RFC 3411-3418)
│   ├── syslog/                    ← Syslog (RFC 5424)
│   ├── modbus/                    ← Modbus TCP
│   ├── ssh/                       ← SSH-2 (RFC 4251-4256, client + server, SFTP, SCP, port forwarding)
│   └── ftp/                       ← FTP/FTPS (RFC 959, RFC 4217, client + server)
└── media/                         ← Real-time media & VoIP
    ├── common/                    ← Shared SDP parser (RFC 4566)
    ├── rtsp/                      ← RTSP 2.0 (RFC 7826)
    ├── rtp/                       ← RTP/RTCP (RFC 3550)
    └── sip/                       ← SIP (RFC 3261)
```

#### Package Structure:
```
ssg.legoflow.blocks/       — core DP, DF, Context, State, Statistics
ssg.legoflow.service/      — Service, AsyncService, Scopes, Users, Manager, UDP transport
ssg.legoflow.http/         — HTTP protocol, features, security, WebSocket
ssg.legoflow.http2/        — HTTP/2 protocol, HPACK, streams, flow control, server push
ssg.legoflow.http3/        — HTTP/3 protocol, QUIC transport, QPACK compression
ssg.legoflow.ws/           — Web service components
ssg.legoflow.http.proxy/   — HTTP forward/reverse/caching proxy
ssg.legoflow.upnp/         — UPnP/DLNA: SSDP, SOAP, GENA, media services, control point
ssg.legoflow.coap/         — CoAP: constrained REST, observe, blockwise, discovery
ssg.legoflow.auth.gssapi/  — GSS-API, Kerberos V5, SPNEGO token handling (shared)
ssg.legoflow.http.auth/    — Auth framework: core, basic/digest, OAuth/OIDC, SSO/SAML
ssg.legoflow.http.auth.spnego/ — HTTP Negotiate (SPNEGO) authentication
ssg.legoflow.messaging.kafka/  — Apache Kafka wire protocol
ssg.legoflow.messaging.amqp/   — AMQP 1.0 (ISO 19464)
ssg.legoflow.messaging.stomp/  — STOMP 1.2
ssg.legoflow.messaging.nats/   — NATS core + JetStream
ssg.legoflow.mqtt/              — MQTT v3.1.1/v5.0: broker, client, pub/sub, QoS, topics
ssg.legoflow.xmpp/              — XMPP: streams, stanzas, presence, messaging, IoT
ssg.legoflow.wamp/              — WAMP core + WebSocket adapter
ssg.legoflow.rpc.grpc/         — gRPC (HTTP/2 + protobuf wire format)
ssg.legoflow.rpc.graphql/      — GraphQL query language
ssg.legoflow.database.redis/   — Redis RESP2/RESP3
ssg.legoflow.database.postgresql/ — PostgreSQL v3 wire protocol
ssg.legoflow.database.mysql/   — MySQL client/server protocol
ssg.legoflow.email.common/     — Shared MIME parsing
ssg.legoflow.email.smtp/       — SMTP (RFC 5321)
ssg.legoflow.email.imap/       — IMAP4rev2 (RFC 9051)
ssg.legoflow.network.common/   — Shared BER/ASN.1 codec
ssg.legoflow.network.dns/      — DNS (RFC 1034/1035)
ssg.legoflow.network.ldap/     — LDAP v3 (RFC 4511)
ssg.legoflow.network.snmp/     — SNMP v3 (RFC 3411-3418)
ssg.legoflow.network.syslog/   — Syslog (RFC 5424)
ssg.legoflow.network.modbus/   — Modbus TCP
ssg.legoflow.ssh/               — SSH-2: transport, kex, cipher, MAC, auth, channels, SFTP, SCP
ssg.legoflow.ftp/               — FTP/FTPS: protocol, data connections, TLS, client, server
ssg.legoflow.media.common/     — Shared SDP parser (RFC 4566)
ssg.legoflow.media.rtsp/       — RTSP 2.0 (RFC 7826)
ssg.legoflow.media.rtp/        — RTP/RTCP (RFC 3550)
ssg.legoflow.media.sip/        — SIP (RFC 3261)
```

#### Dual API Convention (service layer and above):
- **Sync procedural (primary)** — core implementation, straightforward method calls
- **Async wrapper** — `CompletableFuture<T>` return types, delegates to sync on virtual threads
- **Functional style** — lambda-friendly, composable (pipelines, builders, composers)
- Both procedural and functional coexist in same interfaces via default methods
- Pattern: `Foo` (sync) + `AsyncFoo` (wrapper) + `foo.functional/` package
- **Sync-primary is deliberate**: the NIO/selector layer is inherently async, but JDK 25 virtual threads make blocking calls near-zero-cost. Sync APIs are simpler to write, debug, and test. The async wrapper exists for callers who need `CompletableFuture` composability. See `service/doc/ARCHITECTURE.md` § "Wrapper Pattern (Async)" for full rationale.

#### Coding Conventions:
- **JDK 25 features**: virtual threads, scoped values, structured concurrency, sealed interfaces, record patterns, pattern matching for switch, stream gatherers, module imports
- **Dual API**: sync + async (CompletableFuture), procedural + functional
- **Statistics**: track all operations (count, amounts per type)
- **State management**: IDLE, CONNECTING, READY, PAUSED, FAILED, STOPPED
- **Javadoc**: all public classes and methods with @param, @return, @throws, @since
- **Thread safety**: ScopedValues for context propagation, concurrent collections, atomic counters
- **Resource management**: implement AutoCloseable, proper cleanup

### 6. Testing Practices

#### Test Categories:
1. **Unit Tests**: core components (DP, DF, Context, State, Statistics)
2. **Functional Demo Tests**: end-to-end demos exercising real usage patterns
3. **API Style Tests**: procedural sync, procedural async, functional sync, functional async

#### Test Naming:
- `test<Operation><Condition>`: e.g., `testConsumeWithFilter`
- `test<Operation>Async`: e.g., `testConsumeAsync`
- `test<Style><Feature>`: e.g., `testFunctionalPipeline`

#### Test Structure:
```java
@Test
void testFeatureDescription() {
    // Given: Setup test data

    // When: Perform operation

    // Then: Verify results using AssertJ
    assertThat(result).isNotNull();
}
```

#### Test Tools:
- **JUnit 5**: test framework
- **AssertJ**: fluent assertions
- **Mockito**: mocking

### 7. Demo Conventions

All demos live in the central `demos/` module, never in individual protocol modules.

- **Directory structure**: Demos use sub-packages mirroring the source module paths
  (e.g., `demos/src/main/java/ssg/legoflow/network/terminals/vt100/demo/`)
- **Main demos**: reusable demo implementations (servers, clients, pipelines)
- **Test demos**: functional tests that exercise demos with detailed feature verification
- Demos progress from **simplest** → **average** → **complex**
- Cover both **common** and **specific** usage variants separately
- From service module upward: include procedural, functional, and combined variants
- See [demos/AGENTS.md](demos/AGENTS.md) for detailed demo conventions

## Current Project Status

- **Total Commits**: 36
- **Total Tests**: 8582
- **Categories**: 9 (web, iot, auth, messaging, rpc, database, email, network, media)
- **Leaf Modules**: 42 (blocks, service + 40 protocol leaf modules)
- **JDK**: 25
- **Project Layout**: Maven multi-module 0.1.0-SNAPSHOT

## Notes for Future AI Sessions

1. **Always read doc/REQUIREMENTS.md first** to understand project history
2. **Check doc/ARCHITECTURE.md** for current architectural decisions
3. **Follow the established patterns** documented here
4. **Track everything** — requirements, design decisions, metrics
5. **Test comprehensively** — unit tests + demo functional tests
6. **Document thoroughly** — REQUIREMENTS.md, ARCHITECTURE.md, and README.md are mandatory deliverables
7. **Never push automatically** — inform the user and wait for explicit "push" instruction
8. **Dual API everywhere** (from service upward) — sync + async, procedural + functional
9. **JDK 25 features** — use virtual threads, scoped values, structured concurrency, sealed interfaces, pattern matching
10. **Maintain documentation navigation** — every README must link to its own Architecture/Requirements/Compliance docs; root README must have a Documentation section with tree-like references to all module/sub-module documents (3-4 links per module); update navigation when modules or documents are added/removed
11. **Use Mermaid diagrams** — all architecture diagrams use Mermaid (graph TD, flowchart LR, sequenceDiagram) instead of ASCII art

---

**Last Updated**: 2026-07-07
**For AI assistant versions**

---

# Testing Guidelines for CI Reliability

## Anti-Patterns to Avoid

### 1. Fixed Thread.sleep() Without Latch Verification ❌
**Never use:** `Thread.sleep(N)` as the primary mechanism to wait for async operations on CI runners. Virtual-thread executors can be significantly delayed under parallel test execution load (up to 10+ seconds observed).

**Always use:** CountDownLatch with generous timeouts + latch verification AFTER the operation:
```java
var readyLatch = new CountDownLatch(1);
// ... setup subscription/callback that calls readyLatch.countDown() ...

// Brief delay only for subscription registration propagation (200-300ms max)
Thread.sleep(300);

// Perform actual operation with generous timeout
result = client.operation(..., Duration.ofSeconds(5));

// Verify callback was invoked as post-condition check
assertThat(readyLatch.await(2, TimeUnit.SECONDS)).isTrue();
```

### 2. Insufficient Polling Timeouts ❌
**Never use:** Fixed timeouts < 5 seconds for operations involving network I/O or executor scheduling on CI runners.

**Always use:** 
- Minimum 5 seconds for simple request/reply operations
- Minimum 10 seconds for operations requiring server-side processing (connection counting, handler registration)
- Poll every 50ms instead of 100ms for faster detection when the condition becomes true:
```java
long deadline = System.currentTimeMillis() + 10_000;
while (!conditionMet()) {
    if (System.currentTimeMillis() > deadline) break;
    Thread.sleep(50); // 50ms polling interval, not 100ms
}
assertThat(conditionMet()).isTrue();
```

### 3. Warmup Requests for Subscription Readiness ❌
**Never use:** Send a "warmup" request before the actual test request to signal subscription readiness. On slow CI runners, the warmup itself times out causing both failures simultaneously (double-race condition).

**Always use:** Rely on the CountDownLatch callback registration + brief propagation delay pattern above.

### 4. Asserting Before Latch Verification ❌
**Never use:** Assert operation results BEFORE verifying the callback latch fired. If the assertion throws, you lose visibility into whether the callback ever executed.

**Always use:** Store results in arrays/atomic variables from callbacks, perform assertions AFTER both the operation completes AND the latch verifies:
```java
final String[] responsePayload = {null};
var responseReady = new CountDownLatch(1);

// ... setup callback that sets responsePayload[0] and counts down latch ...

result = client.operation(...);
assertThat(result).isNotNull();
responseReady.await(2, TimeUnit.SECONDS); // verify callback fired
assertThat(responsePayload[0]).isEqualTo(expected);
```

### 5. Connection Count Assertions Without Polling ❌
**Never use:** Assert server connection count immediately after client connects. Virtual-thread executors process connections asynchronously and can be heavily delayed on CI.

**Always use:** Poll with deadline pattern:
```java
int countBefore = server.connectionCount();
client.connect(...);

long deadline = System.currentTimeMillis() + 10_000;
while (server.connectionCount() <= countBefore) {
    if (System.currentTimeMillis() > deadline) break;
    Thread.sleep(50);
}
assertThat(server.connectionCount()).isGreaterThan(countBefore);
```


### 6. Timing-Dependent State Transitions in Simulations ❌
**Never rely on:** Scheduled background tasks (e.g. heartbeat detection, health checks) to produce
state transitions in tests or demos. Under CI load, `Thread.sleep(N)` can overshoot the task
interval, causing nodes to be falsely marked SUSPECT/FAILED on macOS or Windows CI even though
they are healthy. The same code passes on Ubuntu CI or local machines.

**Root cause:** When `Thread.sleep(X)` duration is close to the failure-detection threshold
(`heartbeatInterval * failureThreshold`), platform-specific scheduling variance makes the
test non-deterministic. On slow runners, `elapsed >= threshold` triggers false failure detection.

**Always use:** One of these approaches:
1. **Explicit state simulation** — call the API directly to change state rather than waiting for
   background tasks:
   ```java
   // ✅ Deterministic — no timing involved
   manager.simulateFailure(nodeId);           // mark node as failed explicitly
   manager.processHeartbeat(recoveredNode);   // recover node explicitly
   ```
2. **Defensive thresholds** — set failure thresholds orders of magnitude higher than any
   sleep duration in the test:
   ```java
   var config = ClusterConfig.builder()
       .heartbeatInterval(Duration.ofMillis(100))
       .heartbeatFailureThreshold(100)  // 10s timeout, far exceeds demo sleeps
       .build();
   ```
3. **No Thread.sleep at all** — if all operations are synchronous and deterministic,
   eliminate sleeps entirely from simulations.

**Key insight:** A test/demo should never depend on the coincidence that a background task
fires within a specific window. Make state transitions explicit and synchronous.

## Test Design Rules

### Timeout Guidelines by Operation Type
| Operation Type | Minimum Timeout | Polling Interval | Notes |
|---------------|-----------------|------------------|-------|
| Simple request/reply | 5 seconds | N/A | Duration.ofSeconds(5) minimum |
| Connection establishment | 10 seconds | 50ms | Server handler registration takes time |
| Subscription readiness | 3 seconds (latch) | N/A | After 200-300ms propagation delay |
| Message delivery verification | 2 seconds (latch) | N/A | Post-condition check only |
| Executor scheduling | 10 seconds | 50ms | Virtual threads can be heavily delayed |

### Callback Testing Pattern
```java
@Test
void testCallbackInvocation() throws Exception {
    // 1. Setup with latch for callback verification
    var callbackLatch = new CountDownLatch(1);
    final boolean[] callbackInvoked = {false};
    
    service.subscribe(subject, msg -> {
        callbackInvoked[0] = true;
        callbackLatch.countDown();
    });
    
    // 2. Brief delay for subscription propagation (200-300ms max)
    Thread.sleep(300);
    
    // 3. Perform operation with generous timeout
    result = client.operation(..., Duration.ofSeconds(5));
    assertThat(result).isNotNull();
    
    // 4. Verify callback was invoked as post-condition
    assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(callbackInvoked[0]).isTrue();
}
```

### Resource Cleanup Pattern
```java
@Test
void testWithResources() throws Exception {
    try (var server = new SomeServer();
         var client = new SomeClient(host, port)) {
        // Server auto-closes when try-with-resources exits
        // Client auto-closes after operations complete
    }
    // Resources guaranteed cleaned up even if test fails
}
```

## CI Runner Characteristics to Account For

1. **Parallel test execution**: All 40+ modules test simultaneously, causing heavy CPU contention
2. **Virtual-thread scheduling delays**: Under load, virtual threads can be delayed 5-15 seconds
3. **Network I/O timing**: Loopback connections still go through the OS networking stack which can be delayed
4. **File system caching**: Test results from cache may not reflect actual execution timing
5. **Thread.sleep precision varies by platform**: `Thread.sleep(300)` may return after 350ms+
   on macOS/Windows CI due to scheduler granularity. Never assume millisecond-precision sleeps;
   add margin or use deterministic patterns (explicit state transitions, latches).

## Service Class Testing Checklist

When creating tests for new service classes, verify:
- [ ] Builder pattern works with custom name/priority/dependencies
- [ ] Initial state shows disconnected (isConnected() returns false)
- [ ] Client/server reference is null before connect
- [ ] Disconnect before connect doesn't throw
- [ ] Connection to nonexistent host throws expected exception
- [ ] Statistics tracking starts at zero
- [ ] Channel handler creation works and references the service correctly


## 6. Synchronous Accept Pattern for Server Tests ⚠️ NEW

### Problem: Virtual Thread Scheduling Delays on CI
When server implementations use virtual threads (via `Executors.newVirtualThreadPerTaskExecutor()`) to handle connections, the accept loop itself may also run in a virtual thread. On heavily loaded CI runners with 40+ modules testing simultaneously, virtual thread scheduling can be delayed 5-15 seconds.

### Anti-Pattern ❌: Latch Countdown in Executor Thread
```java
// BAD: Latch countdown happens inside the executor's virtual thread
private void acceptLoop() {
    Socket clientSocket = serverSocket.accept();
    executor.submit(() -> handleConnection(clientSocket));  // latch countdown inside!
}

private void handleConnection(Socket socket) {
    connectionCount.incrementAndGet();
    connectionLatch.countDown();  // May not execute for 10+ seconds on CI
}
```

### Correct Pattern ✅: Synchronous Accept Thread
```java
// GOOD: Count increment and latch countdown happen in the accept thread (synchronous)
private void acceptLoop() {
    Socket clientSocket = serverSocket.accept();
    // These run synchronously - no executor delay possible
    int connId = connectionCount.incrementAndGet();
    if (connectionLatch != null) {
        connectionLatch.countDown();
        connectionLatch = null;
    }
    executor.submit(() -> handleConnection(clientSocket, connId));
}

private void handleConnection(Socket socket, int connId) {
    // No increment/latch here - already done synchronously above
    // Just process the actual connection
}
```

### Key Insight
The accept loop itself blocks on `serverSocket.accept()` which is a native blocking call. When it returns, execution continues immediately in whatever thread was running the accept loop (even if that's a virtual thread). The critical operations (count increment, latch countdown) should happen BEFORE submitting to the executor, not after.

### Test Pattern for Connection Counting
```java
@Test
void testServerConnectionCount() throws Exception {
    int countBefore = server.connectionCount();
    
    try (var client = new SomeClient()) {
        client.connect("localhost", port);
        
        // Poll with deadline pattern - max 15s for very heavy CI runners
        long deadline = System.currentTimeMillis() + 15_000;
        while (server.connectionCount() <= countBefore) {
            if (System.currentTimeMillis() > deadline) break;
            Thread.sleep(50);
        }
        assertThat(server.connectionCount()).isGreaterThan(countBefore);
    }
}
```


## 7. Unstable Baseline in @Ordered Tests ⚠️ NEW

### Problem: Previous Tests Leave Virtual-Thread Cleanup Pending
When using `@TestMethodOrder(OrderAnnotation.class)` with connection count comparisons
(`countBefore` → connect → assert `countAfter > countBefore`), previous tests' virtual-thread
cleanup may not have completed by the time the next test runs, making the baseline unstable.

### Anti-Pattern ❌: Comparing Against Previous Count
```java
// BAD: Depends on all previous connections being fully cleaned up
int countBefore = server.connectionCount();
client.connect(...);
assertThat(server.connectionCount()).isGreaterThan(countBefore);
// After client disconnects...
assertThat(server.connectionCount()).isEqualTo(countBefore);  // May fail!
```

### Correct Pattern ✅: Absolute Minimum Assertion
```java
// GOOD: Assert the server shows >= 1 connection while our client is connected
client.connect(...);
long deadline = System.currentTimeMillis() + 15_000;
while (server.connectionCount() < 1) {
    if (System.currentTimeMillis() > deadline) break;
    Thread.sleep(50);
}
assertThat(server.connectionCount()).isGreaterThanOrEqualTo(1);
// No assertion about returning to baseline - virtual-thread cleanup timing is non-deterministic
```

### Key Insight
Never assert that connection count returns to a previous baseline after client disconnect.
Virtual thread cleanup on CI runners can take seconds to minutes under heavy load. Only
assert absolute minimums (>= 1 during the test) rather than deltas against prior state.


## 8. API Mismatch Discovery Before Creating Services ⚠️ NEW

### Problem: Creating Service Wrappers Without Verifying Underlying APIs
When creating new service wrappers, it's tempting to assume the underlying implementation has certain methods (start(), stop(), connect(), etc.). Always verify the actual constructor signatures and method names before writing service code.

### Correct Approach ✅:
```java
// 1. First, check what the actual class provides:
grep -E 'public.*start|public.*stop|public.*connect' path/to/SomeClass.java

// 2. Match exactly:
// - Constructor signature (no-arg? with params?)  
// - Lifecycle method names (start vs bind vs init)
// - Shutdown method names (close vs stop vs destroy)
// - Return types of key methods (CompletableFuture vs void)

// 3. Only then create the service wrapper
```

### Common API Mismatches Encountered:
| Class | Expected | Actual | Resolution |
|-------|----------|--------|------------|
| XmppClientConfig.builder() | no-arg builder | `builder(host, domain)` | Use factory with params |
| RtspServer.start() | in Javadoc but missing | Not implemented | Add start() method |  
| MqttClientConfig.builder() | no-arg builder | `defaults()` returns builder | Use defaults() |
| ModbusConnection(addr) | InetSocketAddress | `(String host, int port)` | Use 2-param constructor |
| PgServer.start(int) | needs port param | Correctly takes int | OK as-is |

### Build Early, Fix Fast Pattern:
```bash
# Create the service file
cat > ServiceFile.java << 'EOF' ... EOF

# Immediately compile to catch API mismatches
./gradlew :module-name:compileJava --no-daemon 2>&1 | grep -E 'error:|BUILD'

# If errors, check actual underlying class API and fix
grep -E 'public.*method' path/to/UnderlyingClass.java
```
---

<a id="build-systems"></a>
## Build System Consistency: Maven vs Gradle

**CRITICAL**: This project has TWO independent build systems (Maven and Gradle) with **different module resolution strategies**. Always verify changes work in BOTH build systems.

### Key Structural Differences

1. **Maven root POM has empty modules list** — child modules must be built individually. The benchmarks module is intentionally excluded from Maven builds via -pl !benchmarks. It was never functional due to pre-existing dependency mismatches.

2. **Gradle uses settings.gradle.kts** — fully declares all 55+ nested modules with unique project names matching Maven artifacts. Gradle was the primary build system throughout development.

3. **Dependency groupId mismatch**: Root POM and all modules use ssg as groupId, but several child modules incorrectly referenced ssg.legoflow. Always use ssg as the groupId.

### Pre-Approved Build Commands

**Maven (excluding benchmarks):**
```bash
mvn install -DskipTests -pl '!benchmarks'
```

**Maven (full build with benchmarks excluded):**
```bash
mvn compile -DskipTests -pl '!benchmarks'
```

**Gradle (full test suite):**
```bash
./gradlew test
```

### Standard Module Structure

Every module in the project MUST follow this structure. New modules are **non-compliant** without all required files:

```
module/
  README.md                 — Module overview, features, quick-start code, dependency badges
  AGENTS.md                 — Module-specific conventions for AI agents (references root AGENTS.md)
  CLAUDE.md                 — Symlink: `CLAUDE.md -> AGENTS.md`
  pom.xml                   — Maven POM (parent = category POM or root)
  build.gradle.kts          — Gradle build (depends on other modules via project(":..."))
  doc/
    ARCHITECTURE.md         — Module purpose, Mermaid diagrams, package structure, design decisions
    COMPLIANCE.md           — Spec compliance matrix (RFC sections → status → test ref)
    REQUIREMENTS.md         — Historical requirements tracking (append-only, commit-based)
  src/main/java/            — Source files under ssg.legoflow.<category>.<module>/
  src/test/java/            — Tests under matching package structure
```

#### README.md (at module root)
- Must include shields (Java version, Maven, License, Tests count, Version)
- Brief description (1-2 sentences)
- Overview section with ASCII or Mermaid diagram
- Quick Start section with runnable code examples
- Dependencies section listing module dependencies

#### AGENTS.md (at module root)
- Must reference root AGENTS.md with relative link
- Must cover: module purpose, key interfaces, design decisions, thread safety model
- Package structure listing (package → purpose)
- Testing notes (framework, test count)
- Module-specific coding conventions

#### CLAUDE.md
- **Must be a symlink** to AGENTS.md (not a copy)
- Created via: `ln -s AGENTS.md CLAUDE.md`

#### doc/ARCHITECTURE.md
- Module purpose, key abstractions, design patterns, data flow
- Mermaid diagrams for architecture (graph TD or graph LR) — no ASCII art
- Package structure listing with descriptions
- Design decisions with rationale

#### doc/COMPLIANCE.md
- Specifications covered (RFCs, APIs, standards)
- Compliance matrix: requirement → status → verification (test ref)
- Thread safety coverage table

#### doc/REQUIREMENTS.md
- Commit-based entries (append-only, historical)
- Each entry: Original Request, Reformulated Requirements, Design Decisions, Implementation, Test Coverage, Cost Estimate


#### Aggregator Modules (POM-only, no source code)

Aggregator modules (e.g., `auth/`, `database/`, `network/`) group child modules under a common
parent POM. They follow a relaxed structure:

- **Required**: `README.md`, `AGENTS.md`, `CLAUDE.md` (symlink → AGENTS.md), `pom.xml`
- **Not required**: `build.gradle.kts`, `doc/COMPLIANCE.md`, `src/` directories
- **Recommended**: `doc/ARCHITECTURE.md` (parent chain diagram), `doc/REQUIREMENTS.md` (if any decisions specific to aggregation)
- `pom.xml` must declare `<packaging>pom</packaging>` and list child `<modules>`

#### Special Modules (benchmarks, demos, interop-tests)

Special modules at the project root have relaxed requirements:

- **Required**: `README.md`, `AGENTS.md`, `CLAUDE.md` (symlink), `pom.xml`
- **Not required**: `doc/COMPLIANCE.md`
- `benchmarks/` — excluded from Maven builds; Gradle-only
- `demos/` — integration demos; may have timing-sensitive tests
- `interop-tests/` — Maven-only; requires Docker reference servers

**When creating a new module, ensure ALL files are present before committing.** Use existing modules (e.g., `web/http`, `network/dns`) as reference templates.

### Structural Rules

1. **Never remove parent references** from child modules — they ensure inheritance from the correct parent POM hierarchy.
2. **Always use ../pom.xml as relativePath** for child modules (pointing to their immediate parent, not root).
3. **Root POM groupId is ssg** — all child module dependency declarations must use this groupId.
4. **Modules without JUnit/SLF4J test dependencies will fail Maven compile** — all leaf modules with test sources must declare these test-scoped dependencies.
5. **Benchmark module is pre-structurally broken** — excluded from Maven by design. Do not add new dependencies to benchmarks/pom.xml without verifying the target module exports the expected packages.
6. **BufferPool utility** is in service/util/BufferPool.java — any module using BufferPool must depend on lego-flow-service.

### Verification Checklist (Before Any PR)

- [ ] mvn compile -DskipTests -pl '!benchmarks' succeeds
- [ ] ./gradlew test succeeds
- [ ] No module references ssg.legoflow groupId (should be ssg)
- [ ] No module references non-existent legoflow-parent (should be lego-flow or parent module name)
- [ ] All leaf modules with test sources declare junit-jupiter and slf4j-simple test dependencies

### Final Verification Protocol (MUST run before reporting "verified")

**Before any PR, run BOTH build systems with FULL clean builds:**

1. **Maven clean + test (ALL modules including benchmarks):**
   mvn clean test

2. **Gradle clean + test (ALL modules, bypassing cache):**
   ./gradlew clean test --rerun-tasks

**Targeted verification of changed modules and their dependents:**
- messaging-stomp (STOMP protocol — buffer pooling, added http dependency)
- messaging-mqtt (MQTT protocol — buffer pooling)
- database-redis (Redis protocol — buffer pooling)
- network-dns (DNS protocol — buffer pooling)
- messaging-kafka (depends on service)
- messaging-amqp (depends on service)
- web-http (depends on service)
- web-http2 (HTTP/2 with buffer pooling)
- web-http3 (HTTP/3 with buffer pooling)
- rpc-grpc (gRPC with buffer pooling)
- network-ssh (SSH with buffer pooling)
- iot-coap (CoAP with buffer pooling)
- media-rtsp (RTSP with buffer pooling)
- email-smtp (SMTP with buffer pooling)
- email-imap (IMAP with buffer pooling)
- service (shared BufferPool utility)
- benchmarks (cross-module performance benchmarks — must compile and test)

**NEVER report "verified" without explicitly running BOTH Maven and Gradle clean test commands.**

---

## Documentation & Graphics Guidelines

### Mermaid Graphics (REQUIRED)
Always use Mermaid diagrams instead of ASCII graphics. ASCII diagrams are deprecated and should be replaced with Mermaid equivalents in all documentation files (README.md, doc/*.md, AGENTS.md).

Use Mermaid graph TD or graph LR for architecture diagrams. Use Mermaid sequence diagrams for protocol flows. Use Mermaid class diagrams for architecture components. Mermaid is supported natively by GitHub, GitLab, VS Code, and all major markdown renderers.

### Conditional --rerun-tasks (Gradle)
Use ./gradlew clean test --rerun-tasks ONLY when:
- Module structure has changed (new/removed modules)
- Dependencies have changed (new/removed dependencies)
- Module names or artifact IDs have changed

For routine verification of unchanged modules, use ./gradlew clean test (uses cache).

### Documentation Update Checklist
Before committing changes that affect code structure:
1. Update README.md: Performance section, module table, architecture diagram
2. Update doc/ARCHITECTURE.md: Update Mermaid diagrams if module structure changed
3. Update doc/COMPARISON.md: Update performance findings if buffer pooling/thread changes affect benchmarks
4. Update doc/PERFORMANCE_IMPROVEMENTS.md: Add new codec coverage, benchmark results
5. Update AGENTS.md: Add any new structural rules or build system changes

### Performance-Related Documentation
When buffer pooling or thread management changes are made:
1. Update the coverage table in README.md (list all modules with buffer pooling)
2. Update doc/PERFORMANCE_IMPROVEMENTS.md with expanded coverage section
3. Update doc/COMPARISON.md with benchmark comparisons (old vs new)
4. Add to README.md Performance section: number of codecs using BufferPool, hit ratio targets


## 9. Callback APIs Must Be Testable ⚠️ NEW

### Problem: IntConsumer Callbacks Block Deterministic Testing
When a background component fires callbacks with just a count or primitive value (e.g.
`IntConsumer` with failure count), tests cannot determine WHICH entity triggered the callback.
This forces tests to use `Thread.sleep` as a workaround, creating platform-specific flakiness.

### Anti-Pattern ❌: Callback Without Identity
```java
// BAD: Test cannot tell which node changed — must sleep
GrpcHealthChecker checker = new GrpcHealthChecker(interval, threshold, probe, i -> latch.countDown());
// After state change: Thread.sleep(200); assertThat(checker.status("n1")).isEqualTo(NOT_SERVING);
```

### Correct Pattern ✅: BiConsumer with Entity Identifier
```java
// GOOD: Test can filter by nodeId and assert the right entity changed
GrpcHealthChecker checker = new GrpcHealthChecker(interval, threshold, probe,
    (nodeId, failures) -> {
        if ("n1".equals(nodeId) && failures >= threshold) latch.countDown();
    }
);
// After state change: assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
```

### Rule
**All callback parameters must carry enough context for tests to identify the triggering entity.**
If a callback conveys only a count or status, extend the signature to include the entity
identifier (nodeId, key, name, etc.) before the class is merged.

## 10. No Self-Referencing Variables in Constructor Lambdas ⚠️ NEW

### Problem: Java Definite Assignment Blocks `var` and Explicit Types
When a lambda passed to a constructor references the variable being initialized, Java's
definite assignment analysis rejects it — even with explicit types (not just `var`).

### Anti-Pattern ❌: Constructor Lambda References the Target
```java
// COMPILE ERROR: variable checker might not have been initialized
GrpcHealthChecker checker = new GrpcHealthChecker(interval, threshold, probe,
    (nodeId, failures) -> {
        HealthStatus s = checker.status(nodeId); // ← references checker being initialized
        ...
    }
);
```

### Correct Pattern ✅: Use Lambda Parameters for All Decisions
```java
// GOOD: All decisions based on lambda parameters only
GrpcHealthChecker checker = new GrpcHealthChecker(interval, threshold, probe,
    (nodeId, failures) -> {
        if (failures >= threshold && "n1".equals(nodeId)) latch.countDown();
        // No checker.status() calls — failures param is sufficient
    }
);
```

### Rule
**Lambda parameters passed to constructors must carry all data needed for decisions.** If the
test needs to query state back from the object, do so AFTER the constructor completes (outside
the lambda).

## Test Quality Enforcement

### Mandatory Pre-Commit Test Review
**Before committing any new test file, the author MUST verify against ALL anti-patterns below:**

| # | Anti-Pattern | Check |
|---|-------------|-------|
| 1 | Thread.sleep as primary sync | Use CountDownLatch with assert |
| 2 | Insufficient timeouts (< 5s for network) | Use generous timeouts |
| 3 | Warmup requests for readiness | Use latch + propagation delay |
| 4 | Assert before latch verify | Latch assert first |
| 5 | Connection count without polling | Poll with deadline |
| 6 | Timing-dependent state transitions | Explicit simulation or defensive thresholds |
| 9 | Non-testable callbacks | BiConsumer with entity ID |
| 11 | Wrong protocol enum codes | Verify RFC codes before using |
| 12 | Byte-to-int AssertJ comparison | Use `| 9 | Non-testable callbacks | BiConsumer with entity ID | 0xFF` for unsigned byte |
| 13 | Empty payload for handlers | Include suboption type byte |
| 14 | Wrong NAWS data format | Exactly 4 bytes big-endian |
| 15 | VT100 SGR color mode | Check foreground(), not fgMode() |
| 16 | VT100 vs DEC SGR codes | SGR 7 = reverse, not 52 |
| 17 | CSI DeleteLine semantics | Shifts up, does not clear |
| 18 | SSH version byte count | Count precisely: 20+2=22 |
| 19 | Cursor relative math | Verify start position |
| 20 | Async counter race conditions | 40×100ms retry minimum |
| 10 | Self-referencing constructor lambdas | Use lambda params only |

**If a test uses Thread.sleep to wait for a scheduled task (health check, watcher, heartbeat),**
**it violates anti-pattern #1 AND #6 simultaneously and MUST be rewritten with latch-based
waiting before the commit is accepted.** This has caused 3+ CI failures across platforms
(Ubuntu, macOS, Windows) in the cluster_protocols branch alone.

## 11. In-Memory Transport Signaling ⚠️ REQUIRED

### Problem: Blocking Polls and Executor Delays on CI
In-memory transports connect two virtual threads in the same JVM. Without proper signaling,
the reader either busy-spins or blocks with timeouts that race against CI scheduling.

### Anti-Pattern ❌: Busy-Spin with onSpinWait
```java
// BAD: poll() returns null → onSpinWait → loop. The sender isn't notified,
// so on CI the reader may spin for seconds waiting for data.
ByteBuffer data = inbound.poll();
if (data == null) { Thread.onSpinWait(); continue; }
```

### Anti-Pattern ❌: Second-Level Timeouts
```java
// BAD: 5-second timeout for in-memory data. On CI, both threads block for seconds.
ByteBuffer data = inbound.poll(5, TimeUnit.SECONDS);
```

### Correct Pattern ✅: Queue Signaling
`LinkedBlockingQueue.poll(timeout)` uses `signal()` — when the sender calls `offer()`,
it wakes the waiting thread immediately via `LockSupport.unpark()`:

```java
@Override
public int receive(ByteBuffer buffer) {
    return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
}

@Override
public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
    ByteBuffer data = inbound.poll(timeout, unit);  // Wakes on offer()
    if (data == null) return -1;  // Timeout or closed
    // ... copy data to buffer ...
}
```

### Implementation Rules
1. **Use `poll(Long.MAX_VALUE, unit)` for receive()** — the queue's `signal()` on `offer()`
   wakes the reader immediately. Virtual threads park/unpark in microseconds.
2. **No busy-loops** — `Thread.onSpinWait()` is wrong here because there's no semaphore
   to wake the spinner.
3. **No seconds-level timeouts** — in-memory transport uses the queue's built-in signaling.
4. **Reference implementation** — see `InMemoryTransport` in `messaging/amqp/`.

### Why This Works
Virtual threads park on `poll(timeout)` and unpark when `offer()` is called.
The latency is the OS thread switch — microseconds, not seconds. This works on every
platform and CI runner because it uses `LockSupport.unpark()`, which is deterministic
regardless of CPU contention.

---

## 12. Interop Test Protocol Accuracy ⚠️ CRITICAL

### Anti-Pattern ❌: Incorrect Enum-Based Protocol Codes
When tests reference enum values by numeric code (e.g. `TelnetOption.fromCode(32)`),
always verify the actual RFC specification value. Telnet option code 34 is LINEMODE
(RFC 1143), not 32. Code 32 is unassigned. Code 42 is TERMINAL_SPEED (RFC 1079).
Similarly, TTYPE (RFC 1091) is 24, NAWS (RFC 1073) is 31, and NEW_ENV (RFC 1408) is 39.

```java
// BAD: Wrong codes — these will fail when enum is updated
assertThat(TelnetOption.fromCode(32)).isEqualTo(TelnetOption.LINEMODE);  // LINEMODE is 34!
assertThat(TelnetOption.fromCode(252)).isEqualTo(TelnetOption.NEW_ENV);   // NEW_ENV is 39!

// GOOD: Verify against RFC specification
assertThat(TelnetOption.fromCode(34)).isEqualTo(TelnetOption.LINEMODE);  // RFC 1143
assertThat(TelnetOption.fromCode(39)).isEqualTo(TelnetOption.NEW_ENV);    // RFC 1408
assertThat(TelnetOption.fromCode(42)).isEqualTo(TelnetOption.TERMINAL_SPEED); // RFC 1079
```

### Anti-Pattern ❌: Byte-to-Integer AssertJ Comparison
When testing raw byte arrays from network protocols, AssertJ's `isEqualTo()`
fails when comparing `byte` to `int` due to strict type matching. Always use
bitwise AND to promote to unsigned int:

```java
// BAD: AssertJ strict type comparison fails (byte 24 ≠ Integer 24)
assertThat(msg[2]).isEqualTo(24);

// GOOD: Promote byte to unsigned int
assertThat(msg[2] & 0xFF).isEqualTo(24);
```

### Anti-Pattern ❌: Empty Payload for Protocol Handlers
Protocol handlers that process subnegotiation data (e.g. TTYPEHandler) require
actual data bytes. An empty `List.of()` returns null — the handler needs the
suboption type (e.g. SEND = 1) as the first element:

```java
// BAD: Empty list → handler returns null, test assertion fails
byte[] response = handler.handle(List.of()); // null!

// GOOD: Include suboption type (SEND = 1)
byte[] response = handler.handle(List.of(1)); // 1 = SEND
assertThat(response).isNotNull();
```

### Anti-Pattern ❌: Wrong NAWS Data Format
NAWS (RFC 1073) carries 4 bytes: `colsHi, colsLo, rowsHi, rowsLo` (big-endian).
A 5-element list causes misalignment: the first two bytes become 0, failing the
`cols >= 1` check inside the handler.

```java
// BAD: 5 elements → cols=0 (first two bytes), handler skips callback
handler.handle(List.of(0, 0, 132, 0, 50));

// GOOD: Exactly 4 bytes (132 cols, 50 rows)
handler.handle(List.of(0, 132, 0, 50));  // cols=132, rows=50
```

### Anti-Pattern ❌: VT100 SGR Color Mode Confusion
VT100 SGR codes 30-37 set the foreground COLOR INDEX (0-7), not the SGR mode.
The `fgMode()` field should be 0 (8-color mode), and `foreground()` should be
the color index. Do not assert `fgMode()` equals the SGR code:

```java
// BAD: fgMode is 0 (8-color), not the SGR code 31
t.feed("\u001b[31m");
assertThat(t.currentAttr().fgMode()).isEqualTo(31);  // FAILS!

// GOOD: Check color index via foreground()
t.feed("\u001b[31m");
assertThat(t.currentAttr().foreground()).isEqualTo(1);  // red = index 1
assertThat(t.currentAttr().fgMode()).isEqualTo(0);  // 0 = 8-color mode
```

### Anti-Pattern ❌: VT100 vs DEC Extension SGR Codes
VT100 only supports SGR 7 for reverse video. SGR 52 (Enable Image) is a much
later DEC extension (circa 1994+) not supported by VT100. Always use the
correct SGR code for the target terminal:

```java
// BAD: SGR 52 is not VT100 reverse
t.feed("\u001b[52m");

// GOOD: VT100 reverse video
t.feed("\u001b[7m");
assertThat(t.currentAttr().reverse()).isTrue();
```

### Anti-Pattern ❌: CSI DeleteLine Misinterpretation
CSI `1M` (DL) deletes the line at cursor position and shifts lines BELOW upward.
It does NOT clear the line to empty — the line receives content from below:

```java
// BAD: Expecting empty line after DL
t.feed("\u001b[1M");
assertThat(lines.get(1)).isEmpty();  // FAILS!

// GOOD: DL shifts "C" from row 3 up to row 2
t.feed("\u001b[1M");
assertThat(lines.get(1)).contains("C");  // Row 2 now has "C"
```

### Anti-Pattern ❌: SSH Version Byte Count
The SSH version string `"SSH-2.0-legoflow_1.0\r\n"` is 22 bytes, not 23 or 24.
Count carefully: the content is 20 chars + `\r\n` (2 bytes) = 22 total.

```java
// BAD: Wrong count
assertThat(wire).hasSize(23);  // Wrong!
assertThat(wire).hasSize(24);  // Also wrong!

// GOOD: 20 content chars + 2 CR LF
assertThat(wire).hasSize(22);
```

### Anti-Pattern ❌: Cursor Motion Math Errors
CUU/CUD are RELATIVE movements. Always verify the starting row after prior
movements. Cursor starts at row 1, moves down 5 → row 6, moves up 3 → row 3.

```java
// BAD: Wrong arithmetic
t.feed("\u001b[5B"); // row 6
t.feed("\u001b[3A"); // row 3 (not row 4!)
assertThat(t.cursor().row()).isEqualTo(4);  // WRONG!

// GOOD: Correct relative math
assertThat(t.cursor().row()).isEqualTo(3);
```

### Anti-Pattern ❌: Async Counter Race Conditions
DNS server and other async handlers update counters via virtual threads.
The test read may occur before the counter is updated. Use adequate retry
logic (40×100ms = 4s total), not 10×50ms = 500ms:

```java
// BAD: Too few retries for CI race conditions (especially Windows)
int retries = 10;
while (counter <= before && retries-- > 0) {
    Thread.sleep(50);
}

// GOOD: Generous retry budget for async race conditions
int retries = 40;
while (counter <= before && retries-- > 0) {
    Thread.sleep(100);
}
```


### Anti-Pattern ❌: NATS Virtual Thread Delivery Race
When a NATS demo or test uses an in-house `NatsServer` with virtual threads for
client connections, the `publish()` call completes as soon as the PUB command is
written to the server — it does **not** wait for subscribers to receive and
process the message. The subscriber's virtual thread must read the data from the
TCP socket and invoke the callback. On Windows CI (slower thread scheduling),
this window is large enough to cause `CountDownLatch` timeouts.

Always add a small synchronization delay after the last publish and before the
latch/counter assertion. Or better yet, use request/reply (which blocks until
the reply arrives) instead of fire-and-forget pub/sub in tests:

```java
// BAD — publish completes before subscriber processes messages
publisher.publish("demo.user.login", "user=alice");
publisher.publish("demo.user.logout", "user=bob");
publisher.publish("demo.system.restart", "node=1");
latch.await(5, TimeUnit.SECONDS);  // may time out on Windows

// GOOD — give subscriber virtual thread time to deliver messages
publisher.publish("demo.user.login", "user=alice");
publisher.publish("demo.user.logout", "user=bob");
publisher.publish("demo.system.restart", "node=1");
Thread.sleep(100);  // synchronization barrier
latch.await(5, TimeUnit.SECONDS);

// BEST — use request/reply which blocks until the reply arrives
NatsMessage reply = client.request(subject, data, Duration.ofSeconds(5));
assertThat(reply).isNotNull();
```


### Anti-Pattern ❌: AMQP ByteBuffer Reuse Without Clear
AMQP protocol handlers reuse `ByteBuffer` instances for multiple reads.
After `flip()` and reading into the buffer, `position` and `limit` must
be reset with `clear()` before re-use. Otherwise `readFully()` sees
`hasRemaining() == false` and reads zero bytes:

```java
// BAD: buffer already flipped, position at limit
ByteBuffer buf = ByteBuffer.allocate(8);
readFully(buf);  // reads 8 bytes
buf.flip();
byte[] header = new byte[8];
buf.get(header);  // position=8, limit=8
readFully(buf);   // does nothing! hasRemaining() == false

// GOOD: clear before re-use
headerBuf.clear();  // position=0, limit=8
readFully(headerBuf);
```


### Anti-Pattern ❌: Windows TCP Connect Timeout to Closed Port

On Windows, connecting to a TCP port that has nothing listening (closed port)
does **not** receive an immediate RST like Linux does. Instead, the TCP stack
may hang for 30+ seconds before the connection attempt fails. This causes
intermittent test failures when tests expect an immediate `IOException`.

On Ubuntu/CI Linux, the kernel sends RST immediately → `IOException` right away.
On Windows, the connection attempt hangs → test times out or assertion fails.

**Always set a connect timeout on sockets used in tests or production code:**

```java
// BAD: Default socket uses OS timeout (30+ seconds on Windows for closed ports)
Socket socket = new Socket(host, port);  // hangs on Windows if port is closed

// GOOD: Explicit connect timeout
Socket socket = new Socket();
socket.connect(new InetSocketAddress(host, port), 5_000);  // fails in 5s

// Alternatively, use a connected socket with timeout:
var socket = new Socket();
socket.setSoTimeout(5_000);
socket.connect(new InetSocketAddress(host, port), 5_000);
```

This is especially critical for **negative tests** that expect a connection
to fail (e.g., "connect to non-existent server"), and for any CI test that
must run deterministically on both Linux and Windows.
### Rule
**All interop test assertions must be verified against the actual RFC or
specification document, not guessed from implementation. When in doubt,
check the reference server's actual protocol bytes.**

