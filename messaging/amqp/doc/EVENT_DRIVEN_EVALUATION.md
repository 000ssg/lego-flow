# Event-Driven Protocol Handler Evaluation

**Date**: 2026-11-05
**Context**: AMQP event-driven implementation (Options A+B+C) completed. Evaluate spreading to other protocols.

---

## Current State Summary

| Protocol | Server Pattern | Read Strategy | Handshake | Event-Driven |
|----------|---------------|---------------|-----------|--------------|
| **AMQP** | `handleConnection()` → executor | `readWithTimeout()` + `Optional` | Complex (header exchange + SASL) | ✅ Yes |
| **NATS** | `handleConnection()` → executor | `transport.receiveWithTimeout()` | Simple (INFO frame) | ❌ No |
| **STOMP** | `accept()` → `Thread.startVirtualThread()` | `StompFrameCodec.receive()` | Version negotiation | ❌ No |
| **Kafka** | `handleConnection()` → executor | `receiveWithTimeout()` with retry | SASL (length-prefixed) | ❌ No |
| **XMPP** | `handleConnection()` → executor | `transport.receiveWithTimeout()` | XML stream open | ❌ No |
| **WAMP** | `handleConnection()` → `Thread.startVirtualThread()` | `transport.receive()` | Hello/WELCOME | ❌ No |
| **MQTT** | `handleConnection()` → executor | `MqttCodec.decodeAll()` | CONNECT/CONNACK | ❌ No |
| **SIP** | N/A (client-only) | N/A | N/A | N/A |
| **RTSP** | N/A (client-only) | N/A | N/A | N/A |

---

## Pros of Spreading Event-Driven Pattern

### Simplicity
**Low benefit.** All protocols already use virtual threads. The event-driven pattern adds callback complexity without reducing code. AMQP's current approach (non-blocking + timeout) is simpler than full event-driven state machine.

### Reliability
**Medium benefit.** AMQP had a real race condition during handshake because virtual thread could start before data arrived. Other protocols have simpler or no handshakes, making this race less likely.

### Visibility
**High benefit.** AMQP's `handleConnectionWithCallback()` enables selector-thread processing, which is better for debugging and monitoring. This is a legitimate cross-cutting concern.

### Performance
**Low benefit.** For most protocols, the performance difference between blocking-read-on-virtual-thread and event-driven is negligible. The bottleneck is usually protocol processing, not I/O waiting. Only high-throughput protocols (Kafka, MQTT) might benefit.

### Unification
**Medium benefit.** Having a consistent pattern across protocols makes the framework easier to understand. However, the current pattern (virtual thread + non-blocking read) is already unified. Event-driven is an *additional* layer, not a replacement.

---

## Cons of Spreading Event-Driven Pattern

### Complexity
**High cost.** Each protocol would need: (1) callback support in transport, (2) event-driven handler method, (3) state machine for handshake, (4) tests for both modes. This is 50-100 lines per protocol.

### Diminishing Returns
AMQP was the *only* protocol with a real race condition. Other protocols don't have this problem. Adding event-driven support "just in case" is over-engineering.

### Maintenance
More code paths = more bugs. Every protocol would need to maintain both blocking and event-driven modes.

### Testing
Each protocol would need tests for both modes, doubling test surface.

---

## Per-Protocol Recommendation

| Protocol | Recommend Event-Driven? | Rationale |
|----------|------------------------|-----------|
| **AMQP** | ✅ **Keep as-is** | Already has both modes. Complex handshake justifies it. |
| **Kafka** | ❌ No | Already has `receiveWithTimeout()` with retry. Handshake is simple. No race condition experienced. |
| **STOMP** | ❌ No | `StompFrameCodec` already handles buffering. Simple version negotiation. |
| **NATS** | ❌ No | Simple INFO frame handshake. No race condition. |
| **XMPP** | ❌ No | XML stream open is straightforward. No race condition. |
| **WAMP** | ❌ No | WebSocket-based, not TCP. Different architecture. |
| **MQTT** | ❌ No | CONNECT/CONNACK is simple. `MqttCodec` handles buffering. |

---

## General Recommendation

**Do NOT spread the event-driven pattern to other protocols.**

**Reasons:**
1. **AMQP was the outlier** - It had a complex multi-phase handshake (header exchange + SASL) that created a race condition. Other protocols have simpler handshakes.
2. **Non-blocking + timeout is sufficient** - All protocols now use `receiveWithTimeout()`. This prevents indefinite blocking without adding callback complexity.
3. **Event-driven is over-engineering for most cases** - The virtual thread + non-blocking read pattern works well for all protocols except AMQP's specific race condition.
4. **Cost outweighs benefit** - Adding event-driven support to 6+ protocols would add 300-500 lines of code with minimal reliability improvement.

---

## To-Do List

### 1. Ensure all protocols use `receiveWithTimeout()`
**Status**: Most protocols already use it. Verify WAMP and any others that might still use blocking `receive()`.

### 2. Add documentation about when to use event-driven vs. blocking mode
**Action**: Add a section to `doc/ARCHITECTURE.md` explaining:
- When to use `handleConnection()` (blocking, virtual thread)
- When to use `handleConnectionWithCallback()` (event-driven, selector-thread)
- Trade-offs and recommendations

### 3. Monitor for race conditions in other protocols
**Action**: Add integration tests for multi-client scenarios in NATS, STOMP, Kafka, XMPP, MQTT. If race conditions appear, evaluate event-driven mode for that specific protocol.

### 4. Keep AMQP's event-driven mode as an option
**Action**: Document the API and use cases. Don't make it the default, but keep it available for high-throughput scenarios.

---

## Summary

| Metric | Rating |
|--------|--------|
| **Simplicity** | ⭐⭐ (event-driven adds complexity) |
| **Reliability** | ⭐⭐⭐ (AMQP fixed, others OK) |
| **Visibility** | ⭐⭐⭐⭐ (good for debugging) |
| **Performance** | ⭐⭐ (marginal gain) |
| **Unification** | ⭐⭐ (current pattern is unified enough) |
| **Overall** | **Do not spread** - AMQP's event-driven mode is a specialized solution for a specific problem, not a general pattern worth universalizing. |

The non-blocking read + timeout approach (Option A) is the right balance for all protocols. Event-driven (Option B) is a specialized tool for AMQP's complex handshake race condition.
