---
name: flaky-test-debugging
description: "Debug CI-only test failures. Redesign after 2-3 patches."
version: 1.1.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [testing, flaky-tests, CI-reliability, debugging, virtual-threads]
    related_skills: [systematic-debugging, test-driven-development]
---

# Flaky Test Debugging

## Overview

Flaky tests fail on CI but pass locally. Each "fix" (latch, timeout, sleep) looks plausible but fails on different platforms. The root cause is usually structural, not timing-related.

**Core principle:** After 2-3 failed patches, redesign the test. No more band-aids.

**User directive:** "You cannot fix it for multiple cycles introducing different techniques: re-design test instead of fighting with its flakiness."

## The Rule of Three for Tests

When a test fails on CI:

1. **First attempt:** Try the obvious fix (latch, timeout adjustment, sync handler)
2. **Second attempt:** Try a different approach if #1 didn't work
3. **Third attempt:** STOP. Redesign or delete the test. No more band-aids.

**DO NOT attempt a fourth patch.** The problem is structural — no parameter tuning will fix it.

**Escalation path after three failures:**
1. **Redesign** — can you make the test structurally sound? (e.g., platform thread for blocking server)
2. **Delete** — if another test already covers the same functionality, remove the flaky test. Better to lose one flaky test than have a broken CI pipeline.
3. **Move to interop-tests/** — if the test genuinely needs external infrastructure (Docker brokers, network), it belongs in interop-tests, not unit tests.

## Common Structural Causes

### 1. Virtual Thread Carrier Starvation

**Symptom:** Test passes locally, fails on all CI platforms (Ubuntu, macOS, Windows).

**Root cause:** Two virtual threads both block on `poll()` or similar. On CI with 40+ modules testing simultaneously, carrier thread pool is saturated. Neither VT gets a carrier thread to unpark. Deadlock.

**Fix:** Use `Thread.ofPlatform()` for the blocking side. Platform threads block efficiently via `LockSupport.park()` and wake instantly on `unpark()`. No carrier contention.

**Pattern:**
```java
// BAD: Both server and client are VTs — deadlock on CI
Thread.ofVirtual().start(() -> server.handleConnection()); // blocks on poll()
client.connect(); // also blocks on poll()

// GOOD: Server on platform thread — no carrier contention
Thread.ofPlatform().start(() -> server.handleConnection());
client.connect(); // VT is fine for the client side
```

### 2. In-Memory Transport Two-Phase Coordination

**Symptom:** Client times out waiting for server response, even with `poll(Long.MAX_VALUE)` and queue signaling.

**Root cause:** Even with platform threads, JaCoCo coverage overhead (Coverage Gate CI job) can slow server frame processing beyond client timeouts. The structural issue is that in-memory tests require two threads to coordinate over queues — a fundamental CI incompatibility under heavy load.

**Fix:** If the in-memory test can't be made deterministic (no blocking, no threading), delete it. Verify another test covers the same functionality over real TCP.

**Real-world example:** `InProcessIntegrationTest` failed 3+ cycles across all platforms. Platform thread fixed VT starvation but JaCoCo overhead still caused timeouts. Deleted — `AmqpServiceIntegrationTest` covers the same flow over real TCP and passes reliably.

### 3. Timing-Dependent State Transitions

**Symptom:** Test depends on background tasks (heartbeats, health checks, cleanup) firing within a specific window.

**Root cause:** Under CI load, scheduled tasks run at unpredictable times. `Thread.sleep()` is unreliable.

**Fix:** Make state transitions explicit and synchronous. Call the API directly instead of waiting for a background task.

**Pattern:**
```java
// BAD: Depends on heartbeat detection task firing
Thread.sleep(heartbeatInterval * failureThreshold * 2);
assertThat(manager.status(nodeId)).isEqualTo(SUSPECT);

// GOOD: Explicit state simulation
manager.simulateFailure(nodeId);
assertThat(manager.status(nodeId)).isEqualTo(SUSPECT);
```

### 4. Shared Mutable State

**Symptom:** Test compares against a "before" value, but other tests or cleanup haven't completed.

**Root cause:** Virtual thread cleanup is non-deterministic. Previous tests may leave state pending.

**Fix:** Assert absolute minimums, not deltas. Use absolute values that don't depend on prior state.

**Pattern:**
```java
// BAD: Depends on all previous connections being cleaned up
int countBefore = server.connectionCount();
client.connect();
assertThat(server.connectionCount()).isGreaterThan(countBefore);

// GOOD: Assert absolute minimum during the test
client.connect();
assertThat(server.connectionCount()).isGreaterThanOrEqualTo(1);
```

### 5. SelectionKey Lifecycle During Teardown

**Symptom:** Test passes locally, fails intermittently on CI. Failure is `ClosedChannelException` or similar, thrown from a handler callback during test cleanup.

**Root cause:** A `SelectionKey` is cancelled when the selector is closed or when `close()` is called on the underlying channel. If a handler callback (e.g. `onWrite()`) is dispatched on the selector thread or in a processing pool thread while `manager.close()` is running, the key may already be cancelled. Calling `key.interestOps()` on a cancelled key throws `ClosedChannelException`.

**Fix:** Wrap `SelectionKey` operations in try/catch during teardown. The key may be cancelled by a concurrent close operation — this is a normal race, not an error.

**Pattern:**
```java
@Override
public void onWrite(DataChannel channel) {
    var key = channel.getSelectionKey();
    if (key != null) {
        try {
            key.interestOps(SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            // Key may be cancelled during teardown — ignore
        }
    }
}
```

**Test discipline:** When writing tests that exercise the service manager, the handler's lifecycle callbacks must be defensive against concurrent teardown. The test's `finally` block calls `manager.close()` which cancels keys — any in-flight handler callbacks will see cancelled keys.

**Pitfall — do not add timeouts to fix this:** Adding `Thread.sleep()` or increasing timeouts does not help because the race is between the selector thread and the close operation, not a timing issue. The fix is defensive coding in the handler.

### 6. Service Integration Test TCP Round-Trip Timing

**Symptom:** Test passes locally but fails on CI, or fails with `AssertionFailedError` on message arrival timeout.

**Root cause:** The test uses `Thread.sleep()` to wait for protocol operations (e.g., SUB processed before PUB), which is unreliable. Additionally, 5-second timeouts may be insufficient for TCP round-trip latency under load.

**Fix:** Use queue-drain waits instead of `Thread.sleep()`, and use 10-second timeouts for TCP round-trips.

**Pattern:**
```java
// BAD: Unreliable sleep before protocol operation
Thread.sleep(100);
client.publish("test.topic", "hello");

// GOOD: Wait for outbound queue to drain (transport is enqueue-only)
long deadline = System.currentTimeMillis() + 2_000;
while (System.currentTimeMillis() < deadline) {
    var transport = client.transport();
    if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
    Thread.sleep(50);
}
client.publish("test.topic", "hello");

// GOOD: Use 10-second timeout for TCP round-trip
assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
```

**Debugging tip:** When a service integration test fails, add debug logging to trace whether data is actually reaching the server. Check:
1. Server received the protocol operation (e.g., SUB, PUB)
2. Server processed it (e.g., subscription registered)
3. Server sent response back
4. Client received and delivered the response

**In-memory transport TCP semantics:** `InMemoryByteTransport` now supports configurable TCP semantics via `withTcpSemantics(TcpSemantics)`. Default is chunked delivery (256-byte chunks) simulating TCP segmentation. Use `TcpSemantics.NONE` for raw synchronous queue behavior when testing protocol layer in isolation.

## Platform Thread vs Virtual Thread

**When to use platform threads:**
- Blocking I/O (`poll()`, `accept()`, `read()`)
- Background servers that block indefinitely
- Tests where two threads might block simultaneously

**When to use virtual threads:**
- CPU-bound work
- Code that doesn't block on I/O
- Tests where only one thread blocks at a time

**Rule:** In tests, if you have a server that blocks on `poll()` and a client that also blocks, the server MUST be on a platform thread.

## Event-Driven Synchronization (Replace Thread.sleep)

**Symptom:** Test passes locally but fails intermittently on CI. Failure is timeout or assertion error on protocol state.

**Root cause:** `Thread.sleep()` is unreliable under CI load. The selector thread, virtual thread executor, and coverage overhead make timing unpredictable.

**Fix:** Use protocol event listeners with `CountDownLatch` to wait for definitive protocol states:

```java
// BAD: Unreliable sleep
Thread.sleep(100);
assertThat(client.isConnected()).isTrue();

// GOOD: Event-driven wait
CountDownLatch openLatch = new CountDownLatch(1);
server.setListener(AmqpEventListener.latchOnFirst(openLatch, AmqpEventListener.EventType.CONNECTION_OPENED));
client.connect(ctx);
assertThat(openLatch.await(10, TimeUnit.SECONDS)).isTrue();
```

**Why this works:** The event fires only after the protocol has fully processed the operation. No timing guesswork.

**Pitfall — set listener before connecting:** The listener must be attached before the client connects, otherwise the event may fire before the listener is in place.

**Pitfall — service layer must expose setListener():** Add `setListener()` to service classes if missing, forwarding to the underlying container/broker.

**Available events:** See `lego-flow-protocol-client/references/in-process-test-synchronization.md` for per-protocol event lists.

## Debugging Checklist

When a test fails on CI:

1. [ ] Does it fail on all platforms or just one?
2. [ ] Does it pass locally every time?
3. [ ] How many "fixes" have you tried?
4. [ ] Is the problem structural (carrier starvation, race condition)?
5. [ ] Can you redesign the test to avoid the structural issue?
6. [ ] Does another test already cover this functionality? If so, delete the flaky test.
7. [ ] Does the fix work on all platforms before committing?

For service integration tests specifically:
1. [ ] Is the server actually receiving the protocol operations?
2. [ ] Is the server processing them (check state changes)?
3. [ ] Are you using queue-drain waits instead of `Thread.sleep()`?
4. [ ] Is the timeout long enough for TCP round-trip latency?

## Real-World Example

**Symptom:** Test passes locally, fails intermittently on CI. Failure is `ClosedChannelException` or similar, thrown from a handler callback during test cleanup.

**Root cause:** A `SelectionKey` is cancelled when the selector is closed or when `close()` is called on the underlying channel. If a handler callback (e.g. `onWrite()`) is dispatched on the selector thread or in a processing pool thread while `manager.close()` is running, the key may already be cancelled. Calling `key.interestOps()` on a cancelled key throws `ClosedChannelException`.

**Fix:** Wrap `SelectionKey` operations in try/catch during teardown. The key may be cancelled by a concurrent close operation — this is a normal race, not an error.

**Pattern:**
```java
@Override
public void onWrite(DataChannel channel) {
    var key = channel.getSelectionKey();
    if (key != null) {
        try {
            key.interestOps(SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            // Key may be cancelled during teardown — ignore
        }
    }
}
```

**Test discipline:** When writing tests that exercise the service manager, the handler's lifecycle callbacks must be defensive against concurrent teardown. The test's `finally` block calls `manager.close()` which cancels keys — any in-flight handler callbacks will see cancelled keys.

**Pitfall — do not add timeouts to fix this:** Adding `Thread.sleep()` or increasing timeouts does not help because the race is between the selector thread and the close operation, not a timing issue. The fix is defensive coding in the handler.
