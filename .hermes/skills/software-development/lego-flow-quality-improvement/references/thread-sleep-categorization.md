# Thread.sleep Categorization

## Analysis Framework

When analyzing Thread.sleep calls in tests, categorize by:

1. **Purpose** — What is the sleep waiting for?
2. **Necessity** — Can it be replaced with synchronization?
3. **Risk** — How likely is it to be flaky?

## Categories

### 1. TTL Expiration (Necessary)
**Purpose**: Testing timeout behavior
**Examples**:
- `DnsCacheTest`: `Thread.sleep(1100)` — waiting for 1ms TTL to expire
- `TokenStoreTest`: `Thread.sleep(5)` — waiting for 1ms token expiration
- `AuthorizationCodeStoreTest`: `Thread.sleep(5)` — waiting for 1ms code expiration

**Can eliminate?**: NO — must wait for actual time passage
**Risk**: Low (deterministic, system clock-based)

### 2. Virtual Thread Timing (Often Necessary)
**Purpose**: Waiting for async operations to complete
**Examples**:
- `ConnectTunnelTest`: `Thread.sleep(100)` — waiting for virtual thread relay to start
- `HealthCheckerTest`: `Thread.sleep(300)` — waiting for health check cycle
- `SsoSessionTest`: `Thread.sleep(5)` — waiting for timestamp difference

**Can eliminate?**: Sometimes — use CountDownLatch if event available
**Risk**: Medium (depends on scheduler)

### 3. Selector Thread Initialization (Necessary)
**Purpose**: Allowing selector thread to process channel registration
**Examples**:
- `AmqpServiceIntegrationTest`: `Thread.sleep(50)` — after `server.connect(ctx)`

**Can eliminate?**: NO — selector thread scheduling is non-deterministic
**Risk**: Medium (CI vs local differences)

### 4. Async Broker Setup (Often Necessary)
**Purpose**: Waiting for broker to accept connection on virtual thread
**Examples**:
- `StompBrokerTest`: `Thread.sleep(50)` — after `server.connect(ctx)`
- `ProcessingThreadTest`: `Thread.sleep(500)` — waiting for PROCESSING_POOL

**Can eliminate?**: Sometimes — use CountDownLatch if event available
**Risk**: High (virtual thread scheduling)

### 5. Protocol Handshake (Replaceable)
**Purpose**: Waiting for protocol operation to complete
**Examples**:
- Tests using `Thread.sleep(100)` before SUB before PUB

**Can eliminate?**: YES — use CountDownLatch with protocol events
**Risk**: High (timing-dependent)

## Decision Tree

```
Is the sleep testing timeout behavior?
├── YES → Necessary (TTL expiration)
└── NO → Is there a protocol event available?
    ├── YES → Use CountDownLatch (replaceable)
    └── NO → Is it waiting for async setup?
        ├── YES → Document as necessary (selector/broker initialization)
        └── NO → Can you redesign the test?
```

## Replacement Patterns

### CountDownLatch (when event available)
```java
CountDownLatch latch = new CountDownLatch(1);
server.setListener(AmqpEventListener.latchOnFirst(latch, EventType.CONNECTION_OPENED));
client.connect(ctx);
assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
```

### Queue Drain Wait (when transport has queue)
```java
long deadline = System.currentTimeMillis() + 2_000;
while (System.currentTimeMillis() < deadline) {
    var transport = client.transport();
    if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
    Thread.sleep(50);
}
```

### Platform Thread (for blocking I/O)
```java
// BAD: Both server and client are VTs — deadlock on CI
Thread.ofVirtual().start(() -> server.handleConnection());
client.connect();

// GOOD: Server on platform thread
Thread.ofPlatform().start(() -> server.handleConnection());
client.connect();
```

## Reporting Format

```markdown
### 1. Necessary for TTL Expiration (Cannot Eliminate)
- `DnsCacheTest`: 2x Thread.sleep(1100) — testing 1ms TTL expiration
- `AuthorizationCodeStoreTest`: 2x Thread.sleep(5) — testing 1ms expiration
- `TokenStoreTest`: 1x Thread.sleep(5) — testing 1ms expiration

### 2. Necessary for Virtual Thread Timing (Difficult to Eliminate)
- `ConnectTunnelTest`: 5x Thread.sleep(100) — waiting for virtual thread relay
- `HealthCheckerTest`: 1x Thread.sleep(300) — waiting for health check cycle
- `SsoSessionTest`: 2x Thread.sleep(5) — waiting for timestamp difference

### 3. Necessary for Selector Thread Initialization
- `AmqpServiceIntegrationTest`: 2x Thread.sleep(50) — after server.connect()

### 4. Necessary for Async Broker Setup
- `StompBrokerTest`: 17x Thread.sleep(50) — async broker accept()
- `ProcessingThreadTest`: 11x Thread.sleep(500) — PROCESSING_POOL scheduling
```