# Test Patterns & Anti-Patterns

Reusable test patterns for CI reliability. Load when writing or fixing tests.

> **Root AGENTS.md**: See [../AGENTS.md](../AGENTS.md) for project-wide conventions.

---

## 1. Synchronization: Latch + Timeout (not Thread.sleep)

### Anti-Pattern ❌: Thread.sleep as primary sync
Never use `Thread.sleep(N)` to wait for async operations on CI. Virtual-thread executors can be delayed 5-15 seconds under parallel test load.

### Correct Pattern ✅: CountDownLatch with generous timeout
```java
var readyLatch = new CountDownLatch(1);
// ... setup callback that calls readyLatch.countDown() ...

// Perform operation with generous timeout
result = client.operation(..., Duration.ofSeconds(5));

// Verify callback was invoked as post-condition check
assertThat(readyLatch.await(2, TimeUnit.SECONDS)).isTrue();
```

## 2. Polling with Deadline

### Anti-Pattern ❌: Fixed short timeout
Never use fixed timeouts < 5 seconds for network I/O on CI.

### Correct Pattern ✅: Poll with deadline
```java
long deadline = System.currentTimeMillis() + 10_000;
while (!conditionMet()) {
    if (System.currentTimeMillis() > deadline) break;
    Thread.sleep(50); // 50ms polling interval
}
assertThat(conditionMet()).isTrue();
```

## 3. Callback Ordering: Latch Before Assertions

### Anti-Pattern ❌: Assert before latch verification
If the assertion throws, you lose visibility into whether the callback executed.

### Correct Pattern ✅: Store results, assert after latch
```java
final String[] responsePayload = {null};
var responseReady = new CountDownLatch(1);

// Setup callback that sets responsePayload[0] and counts down latch
result = client.operation(...);
assertThat(result).isNotNull();
assertThat(responseReady.await(2, TimeUnit.SECONDS)).isTrue();
assertThat(responsePayload[0]).isEqualTo(expected);
```

## 4. Connection Count Assertions

### Anti-Pattern ❌: Immediate assertion
Virtual-thread executors process connections asynchronously.

### Correct Pattern ✅: Poll with generous timeout
```java
int countBefore = server.connectionCount();
client.connect(..., Duration.ofSeconds(10));

long deadline = System.currentTimeMillis() + 15_000;
while (server.connectionCount() <= countBefore) {
    if (System.currentTimeMillis() > deadline) break;
    Thread.sleep(50);
}
assertThat(server.connectionCount()).isGreaterThan(countBefore);
```

## 5. Unstable Baseline in @Ordered Tests

### Anti-Pattern ❌: Delta against previous count
Previous tests' virtual-thread cleanup may not have completed.

### Correct Pattern ✅: Absolute minimum
```java
client.connect(...);
assertThat(server.connectionCount()).isGreaterThanOrEqualTo(1);
// Never assert: server.connectionCount() == countBefore after disconnect
```

## 6. Callback APIs Must Be Testable

### Anti-Pattern ❌: Callback without entity identity
```java
// Test cannot tell which node changed — must sleep
checker = new HealthChecker(interval, threshold, probe, i -> latch.countDown());
```

### Correct Pattern ✅: BiConsumer with entity ID
```java
checker = new HealthChecker(interval, threshold, probe, (nodeId, failures) -> {
    if ("n1".equals(nodeId) && failures >= threshold) latch.countDown();
});
```

## 7. Self-Referencing Constructor Lambdas

### Anti-Pattern ❌: Lambda references the variable being initialized
```java
// COMPILE ERROR: variable might not have been initialized
Checker checker = new Checker(params, (nodeId, failures) -> {
    checker.status(nodeId); // ← references checker
});
```

### Correct Pattern ✅: Use lambda parameters only
```java
Checker checker = new Checker(params, (nodeId, failures) -> {
    if (failures >= threshold && "n1".equals(nodeId)) latch.countDown();
});
```

## 8. In-Memory Transport Signaling

### Problem
In-memory transports connect two threads. Without proper signaling, the reader blocks with timeouts that race against CI scheduling.

### Anti-Pattern ❌: Busy-spin with onSpinWait
```java
ByteBuffer data = inbound.poll();
if (data == null) { Thread.onSpinWait(); continue; }
```

### Anti-Pattern ❌: Second-level timeouts
```java
ByteBuffer data = inbound.poll(5, TimeUnit.SECONDS);
```

### Correct Pattern ✅: Queue signaling with platform thread for server
`LinkedBlockingQueue.poll(timeout)` uses `signal()` — when the sender calls `offer()`, it wakes the waiting thread immediately via `LockSupport.unpark()`.

**Critical: Run the server side on a platform thread.** Two virtual threads both blocking on `poll()` compete for carrier threads on CI runners with 40+ modules testing simultaneously. A platform thread can block indefinitely and wake instantly.

```java
// Server runs on platform thread (not virtual thread)
Thread.ofPlatform().start(() -> serverContainer.handleConnection(serverSide));

// Transport receive() uses poll(Long.MAX_VALUE) — wakes on offer()
@Override
public int receive(ByteBuffer buffer) {
    return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
}
```

**Reference**: `InMemoryTransport` in `messaging/amqp/`

## 9. In-Process Two-Thread Tests

### Anti-Pattern ❌: Two virtual threads coordinating over queues
When both client and server use virtual threads, carrier thread starvation causes silent deadlock on CI. No latch or timeout can fix this — it's a resource contention issue.

### Correct Pattern ✅: Server on platform thread + latch synchronization
```java
// Server runs on platform thread — avoids carrier contention
Thread.ofPlatform().start(() -> serverContainer.handleConnection(transportPair.server()));

// Wait for server to start (latch fires before server blocks on poll())
assertThat(serverStarted.await(3, TimeUnit.SECONDS)).isTrue();

// Client connects on the test's virtual thread
try (var client = new AmqpClient(config)) {
    client.connect("test", Duration.ofSeconds(10));
}
```

### When to delete instead of fix
If an in-process test fails on CI for 3+ cycles across platforms despite fixes, **delete it**. The `AmqpServiceIntegrationTest` (TCP-based) already covers the same flow. See `InProcessIntegrationTest` deletion (AMQP) for precedent.

## 10. Async Counter Race Conditions

### Anti-Pattern ❌: Insufficient retries
```java
int retries = 10;
while (counter <= before && retries-- > 0) { Thread.sleep(50); }
```

### Correct Pattern ✅: 40×100ms retry minimum
```java
int retries = 40;
while (counter <= before && retries-- > 0) { Thread.sleep(100); }
```

## 11. Windows TCP Connect Timeout

On Windows, connecting to a closed port hangs for 30+ seconds (no RST).

### Correct Pattern ✅: Explicit connect timeout
```java
var socket = new Socket();
socket.connect(new InetSocketAddress(host, port), 5_000);
```

## 12. NATS Virtual Thread Delivery Race

`publish()` completes when the PUB command is written — not when subscribers process it.

### Correct Pattern ✅: Request/reply or synchronization barrier
```java
// Best: request/reply blocks until reply arrives
NatsMessage reply = client.request(subject, data, Duration.ofSeconds(5));

// Acceptable: small sync delay before assertion
publisher.publish(subject, data);
Thread.sleep(100);
latch.await(5, TimeUnit.SECONDS);
```

---

## Timeout Guidelines

| Operation | Minimum Timeout | Polling Interval |
|-----------|----------------|-----------------|
| Simple request/reply | 5 seconds | N/A |
| Connection establishment | 10 seconds | 50ms |
| Server-side processing | 15 seconds | 50ms |
| Message delivery (latch) | 3 seconds (latch) | N/A |
| Async counter read | 40×100ms (4s total) | 100ms |

## CI Runner Characteristics
1. **Parallel test execution**: 40+ modules test simultaneously → heavy CPU contention
2. **Virtual-thread delays**: 5-15 seconds under load
3. **JaCoCo overhead**: Coverage instrumentation slows every method call
4. **Platform scheduling variance**: macOS/Windows sleeps overshoot by 50ms+
