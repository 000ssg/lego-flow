# AMQP 1.0 Server Compatibility Analysis

> **AMQP 1.0 is NOT a single protocol.** It is a specification with underspecified corners,
> ambiguous state machines, and broker-specific extensions. Every major implementation interprets
> the spec differently at the wire level. A client that works against one broker will fail against
> another — not because of bugs, but because the spec leaves implementation-defined behavior.

## Executive Summary

| Broker | Wire Compliance | SASL Flow | Address Model | Reference Quality |
|--------|----------------|-----------|---------------|-------------------|
| **Apache ActiveMQ Artemis** | High | Standard SASL-first | JMS-native | ★★★★★ Best reference |
| **Apache Qpid Dispatch Router** | High | ANONYMOUS default | Prefix routing | ★★★★ Good reference |
| **RabbitMQ** | Medium | SASL-required, rejects proto-0 | Exchange/queue mapping | ★★ Broken as reference |
| **Solace** | High | SASL-echo | Topic/queue prefix | ★★★ Cloud-only |
| **Azure Service Bus** | Medium | CBS extensions | Topic/subscription | ★★ Cloud-only |
| **IBM MQ** | Low | Server-OPEN-first | Queue manager | ★★ Legacy |

**Bottom line:** No single broker is a perfect reference. Artemis + Qpid Dispatch Router form the
best pair for validating compliance. RabbitMQ is NOT a reference — it's a broker that happens to
speak AMQP 1.0 as one of several protocols.

---

## 1. Protocol Header Negotiation (Section 2.2)

### What the spec says
```
Client                         Server
  |--- AMQP%n%m%r%v (proto 0) --->|
  |<-- AMQP%n%m%r%v (proto 0) ---|  echo back
```

Optional SASL layer uses protocol 3:
```
Client                         Server
  |--- AMQP%3%1%0%0 (proto 3) --->|
  |<-- AMQP%3%1%0%0 (proto 3) ---|  echo back
  |   ... SASL negotiation ...    |
  |--- AMQP%0%1%0%0 (proto 0) --->|  post-SASL switch
  |<-- AMQP%0%1%0%0 (proto 0) ---|
```

### How brokers actually behave

| Broker | Proto-0 first? | Proto-3 first? | Behavior |
|--------|---------------|---------------|----------|
| **OASIS spec** | Client sends, server echoes | Same | Client controls flow |
| **RabbitMQ** | ❌ Rejects! | ✅ Required for AMQP 1.0 | Sends proto-0 first → socket closed. Must use proto-3. |
| **Artemis** | ✅ Accepted | ✅ Accepted | Both work, depends on acceptor config |
| **Qpid Dispatch** | ✅ With ANONYMOUS | ✅ With SASL | Defaults to ANONYMOUS SASL |
| **Solace** | ✅ Only if auth disabled | ✅ If auth enabled | Echoes back whatever client sends |
| **IBM MQ** | ✅ Accepted | ✅ Accepted | Standard echo |

### Legoflow client behavior
```java
// AmqpClient.java lines 91-139
// Correctly sends SASL_HEADER (proto 3) first when credentials exist
// Correctly sends AMQP_HEADER (proto 0) first when anonymous
```
**Status:** ✅ Compliant. Will work against Artemis, Qpid Dispatch, Solace, IBM MQ.
**Issue:** Will be rejected by RabbitMQ if attempting anonymous (proto-0) connection.

---

## 2. SASL Negotiation (Section 5.3)

### 2.1 SASL Mechanism Discovery

| Broker | Mechanisms offered | Notes |
|--------|-------------------|-------|
| **Artemis** | PLAIN, ANONYMOUS, GSSAPI | Configurable per-acceptor |
| **RabbitMQ** | PLAIN, ANONYMOUS | AMQP 1.0 only; requires SASL |
| **Qpid Dispatch** | ANONYMOUS | Default config; configurable |
| **Solace** | ANONYMOUS, PLAIN, EXTERNAL, XOAUTH2 | VPN-configurable |
| **IBM MQ** | PLAIN, EXTERNAL, GSSAPI | Queue-manager config |

### 2.2 SASL PLAIN — the authzid problem

RFC 4616 defines SASL PLAIN as: `authzid\0authcid\0password`

| Broker | Empty authzid? | Non-empty authzid? | Bug? |
|--------|---------------|-------------------|------|
| **OASIS spec** | ✅ Must accept | ✅ Must accept | - |
| **RabbitMQ** | ✅ Works | ❌ Rejects (socket close) | rabbitmq-server#2586 |
| **Artemis** | ✅ Works | ✅ Works | - |
| **Qpid Dispatch** | ✅ Works | ✅ Works | - |
| **IBM MQ** | ✅ Works | ✅ Works | - |

**Critical:** Qpid Proton-C clients send `authzid = authcid` by default (`guest\0guest\0guest`),
which RabbitMQ rejects. Legoflow correctly sends empty authzid: `PlainMechanism.java` line 42.

```java
// Legoflow PlainMechanism.java
response[0] = 0; // authzid (empty) — CORRECT
```

### 2.3 SASL + OPEN pipelining

Some implementations (Qpid Proton-C, rhea.js) pipeline the OPEN frame along with the SASL
init when using ANONYMOUS mechanism. The spec is ambiguous about this.

| Broker | Accepts pipelined OPEN? | Notes |
|--------|------------------------|-------|
| **OASIS spec** | Ambiguous | States SASL completes before OPEN |
| **RabbitMQ** | ✅ Handles | Accepts pipelined frames |
| **Qpid C++ Broker** | ❌ Rejects | QPID-6639 — loses pipelined OPEN |
| **Artemis** | ✅ Handles | - |

### 2.4 SASL frame size limit

The spec states SASL frames are limited to 512 bytes before OPEN negotiation:
> "SASL frames can be at-most the 'min max frame size', which is fixed at 512 bytes"

| Broker | Enforces 512 limit? | Max allowed |
|--------|-------------------|-------------|
| **OASIS spec** | ✅ 512 bytes | 512 |
| **Qpid Broker-J** | ✅ 4096 bytes | 4096 |
| **RabbitMQ** | ❌ No hard limit | - |
| **Artemis** | ❌ No hard limit | - |

**Impact:** XOAuth2 tokens can exceed 512 bytes. Legoflow should NOT enforce this limit.

---

## 3. OPEN Frame Negotiation (Section 2.4.1)

### What the spec says
> "After establishing or accepting a TCP connection and sending the protocol header,
> each peer MUST send an open frame before sending any other frames."

The spec does NOT specify which peer sends OPEN first.

| Broker | Sends OPEN first? | Waits for client OPEN? |
|--------|------------------|----------------------|
| **OASIS spec** | Either | Either |
| **Artemis** | Waits for client | ✅ Client must send first |
| **RabbitMQ** | Waits for client | ✅ Client must send first |
| **Qpid Dispatch** | Waits for client | ✅ |
| **Solace** | Waits for client | ✅ Sends after processing |
| **IBM MQ** | ✅ Sometimes first | Can send before client |

**Critical issue:** Qpid JMS client 0.20.0 hung when IBM MQ sent OPEN first (QPIDJMS-261).
The client's state machine expected to send OPEN before receiving it.

### Legoflow client behavior
```java
// AmqpClient.java lines 142-158
sendPerformative(0, open);           // Send OPEN
AmqpFrame openFrame = readFrame();    // Read OPEN response
```
**Status:** ✅ Sends OPEN first. Compatible with Artemis, RabbitMQ, Qpid Dispatch.
**Issue:** May hang if broker sends OPEN first (IBM MQ behavior).

### OPEN frame field interpretation

| Field | Spec default | Legoflow sends | Notes |
|-------|-------------|---------------|-------|
| `container-id` | Required string | From config | ✅ |
| `hostname` | Null = default | From config | RabbitMQ: `vhost:tenant-1` prefix |
| `channel-max` | 65535 | From config | ⚠️ Qpid Broker-J treats as signed short |
| `idle-time-out` | 0 (disabled) | From config | Solace: must be ≥ 500ms |

### channel-max signed short overflow

The spec defines `channel-max` as `ushort` (unsigned, 0-65535).

| Broker | Treats as unsigned? | Bug? |
|--------|-------------------|------|
| **OASIS spec** | ✅ unsigned short | - |
| **Qpid Broker-J** | ❌ Signed short | QPID-6193, QPID-6153 |
| **Artemis** | ✅ | - |
| **RabbitMQ** | ✅ | - |

If the client sends `channel-max` > 32767, Qpid Broker-J truncates it to a negative value and
crashes on `ArrayIndexOutOfBoundsException`.

**Legoflow default:** `AmqpConstants.DEFAULT_CHANNEL_MAX = 65535` — will crash Qpid Broker-J.

---

## 4. BEGIN / Session Establishment (Section 2.5.1)

### Channel assignment

| Broker | Client channel? | Server channel? | Notes |
|--------|----------------|-----------------|-------|
| **OASIS spec** | Client assigns | Server assigns | Independent |
| **Artemis** | Client | Server | Standard |
| **RabbitMQ** | Client | Server | Standard |
| **Qpid Dispatch** | Client | Server | Standard |
| **Solace** | Client | Lowest available | Reserves channel 0 for error back-channel |

### handle-max

| Broker | Default handle-max | Notes |
|--------|-------------------|-------|
| **OASIS spec** | Implementation-defined | Should be negotiated |
| **Solace** | 575 | Hard limit per session |
| **Artemis** | 65535 | Standard |
| **RabbitMQ** | 65535 | Standard |

---

## 5. ATTACH / Link Establishment (Section 2.6)

### Source vs Target address resolution

This is where brokers differ MOST dramatically:

| Broker | Sender target → | Receiver source → |
|--------|----------------|------------------|
| **OASIS spec** | Implementation-defined | Implementation-defined |
| **RabbitMQ** | Exchange/queue mapping | Queue (auto-creates) |
| **Artemis** | Address (creates queue) | Address (creates queue) |
| **Qpid Dispatch** | Prefix routing (closest/multicast) | Address subscription |
| **Solace** | `topic://` or `queue://` prefix | Same |
| **Azure SB** | Topic/subscription | Subscription |
| **IBM MQ** | Queue manager | Queue |

### RabbitMQ address format (v2)
```
/exchanges/:exchange/:routing-key   → publish to exchange
/queues/:queue                      → publish directly to queue
```

### Artemis address format
```
:queue-name          → creates a queue
:topic-name          → creates a topic (durable subscription)
```

### ATTACH settle mode negotiation

The spec defines: `unsettled(0)`, `settled(1)`, `mixed(2)`

| Broker | Default snd-settle | Default rcv-settle | Supports mixed? |
|--------|-------------------|-------------------|----------------|
| **OASIS spec** | unsettled(0) | unsettled(0) | Yes |
| **RabbitMQ** | mixed(2) | unsettled(0) | ✅ Yes |
| **Artemis** | unsettled(0) | unsettled(0) | ✅ Yes |
| **Qpid Dispatch** | unsettled(0) | unsettled(0) | ✅ |

### Legoflow client behavior
```java
// PerformativeCodec.java lines 161-162 (decodeAttach)
int sndSettleMode = (int) optUbyteField(list, 3, 2);  // default: mixed(2)
int rcvSettleMode = (int) optUbyteField(list, 4, 0);  // default: unsettled(0)
```
**Status:** ⚠️ Defaults to `mixed(2)` for sender settle mode. This is NON-STANDARD.
Most brokers expect `unsettled(0)`. RabbitMQ explicitly supports mixed, so it works there.

---

## 6. Credit Flow (Section 2.6.9)

### How brokers grant credit

| Broker | Grants credit when? | Auto-creates queue? |
|--------|--------------------|--------------------|
| **OASIS spec** | Receiver sends FLOW with link-credit | N/A |
| **RabbitMQ** | After receiver ATTACH + queue exists | ✅ On receiver ATTACH |
| **Artemis** | After receiver ATTACH | ✅ Address-based creation |
| **Qpid Dispatch** | Auto-routes on ATTACH | No — it's a router, not a broker |

### Common failure: sender before receiver

```
Client creates sender → broker: no queue exists → link-credit = 0
Client creates receiver → broker: creates queue → grants credit
But sender already attached with 0 credit → messages stuck
```

| Broker | Handles sender-first? | Workaround |
|--------|---------------------|------------|
| **RabbitMQ** | ❌ No | Create receiver first, or use v2 queue address |
| **Artemis** | ✅ Yes | Address auto-creates |
| **Qpid Dispatch** | ✅ Yes | Routes to any matching receiver |

---

## 7. FLOW Frame Semantics (Section 2.6.9)

### Which fields are required?

| Field | Spec | Legoflow server sends | Legoflow client expects |
|-------|------|----------------------|----------------------|
| `next-incoming-id` | Required | ✅ | ✅ |
| `incoming-window` | Required | ✅ | ✅ |
| `next-outgoing-id` | Required | ✅ | ✅ |
| `outgoing-window` | Required | ✅ | ✅ |
| `handle` | Conditional | ✅ On link FLOW | ✅ |
| `delivery-count` | Conditional | ✅ | ⚠️ NULL when broker omits |
| `link-credit` | Conditional | ✅ | ✅ |

### Legoflow credit handling

```java
// AmqpClient.java lines 358-364
long brokerDeliveryCount = flow.deliveryCount() != null
    ? flow.deliveryCount() : sender.deliveryCount();  // fallback to local
sender.grantCredit(brokerDeliveryCount, flow.linkCredit());
```
**Status:** ✅ Falls back to local delivery count when broker omits it.

---

## 8. Message Transfer (Section 2.6.12)

### Settlement modes

| Broker | Pre-settled (at-most-once)? | Mixed sender settle? |
|--------|---------------------------|--------------------|
| **OASIS spec** | ✅ | ✅ |
| **RabbitMQ** | ✅ | ✅ (mixed mode) |
| **Artemis** | ✅ | ✅ |
| **Solace** | ✅ | ❌ No exactly-once |
| **IBM MQ** | ✅ | ❌ Limited |

### Legoflow server behavior
```java
// AmqpContainer.java lines 381-398
if (transfer.settled()) {
    receiver.handleTransfer(..., true);  // auto-accept pre-settled
} else {
    receiver.handleTransfer(..., false);
    // Auto-accepts and sends disposition back
    sendPerformative(ctx, localChannel, disposition);
}
```
**Status:** ⚠️ Auto-accepts ALL messages. This is non-standard — the receiver
should reject messages it cannot process. Most brokers don't auto-accept.

---

## 9. Known Interop Issues Summary

| Issue | Affected Brokers | Legoflow Impact | Severity |
|-------|-----------------|----------------|----------|
| **RabbitMQ rejects proto-0 without SASL** | RabbitMQ | Cannot connect anonymously | High |
| **RabbitMQ rejects non-empty SASL authzid** | RabbitMQ + Proton clients | Legoflow sends empty authzid ✅ | N/A (fixed) |
| **Qpid Broker-J crashes on channel-max > 32767** | Qpid Broker-J | Default 65535 will crash | High |
| **IBM MQ sends OPEN before client** | IBM MQ | Legoflow hangs waiting to send | Medium |
| **RabbitMQ requires receiver before sender** | RabbitMQ | Sender-first pattern fails | Medium |
| **Qpid C++ broker drops pipelined OPEN** | Qpid C++ | rhea.js ANONYMOUS connections fail | Medium |
| **XOAuth2 tokens exceed 512-byte SASL limit** | All strict brokers | Not currently used | Low |
| **RabbitMQ address format (exchange/queue)** | RabbitMQ | Simple queue names don't work | High |

---

## 10. Recommended Reference Stack

### Primary reference: Apache ActiveMQ Artemis
- **Docker image:** `apache/artemis:latest-alpine` (arm64 supported)
- **Why:** Follows OASIS spec strictly, used by Qpid Interop Test suite
- **Config:** Standard acceptor on port 5672, AMQP protocol, PLAIN/ANONYMOUS SASL
- **Credentials:** `ARTEMIS_USER`/`ARTEMIS_PASSWORD` env vars

### Secondary reference: Qpid Dispatch Router
- **Docker image:** `scholzj/qpid-dispatch:latest` (amd64 only — use on non-Apple Silicon)
- **Why:** Reference Proton-C implementation, Apache-verified
- **Config:** Standalone mode, ANONYMOUS SASL by default
- **Note:** Runs under QEMU emulation on Apple Silicon (slow/unreliable)

### NOT suitable as reference
- **RabbitMQ:** Broker-specific address mapping, SASL quirks, authzid bug
- **Solace:** Cloud-only, proprietary extensions
- **Azure Service Bus:** Cloud-only, CBS extensions required

### Qpid Interop Test
The Apache Qpid Interop Test suite (`qpid-interop-test`) validates AMQP 1.0 clients against
both Artemis and Qpid Dispatch Router. It is the authoritative compliance check.

```bash
docker run -it apache/artemis:latest-alpine
# Inside container:
qpid-interop-test amqp_types_test
qpid-interop-test amqp_large_content_test
```

---

## 11. Legoflow Compliance Checklist

| Area | Status | Notes |
|------|--------|-------|
| Protocol header exchange | ✅ | SASL-first when credentials exist |
| SASL PLAIN encoding | ✅ | Empty authzid per RFC 4616 |
| SASL ANONYMOUS | ✅ | Works with brokers that support it |
| OPEN frame | ✅ | Standard field encoding |
| BEGIN frame | ✅ | Standard channel assignment |
| ATTACH settle modes | ⚠️ | Defaults to mixed(2) — non-standard |
| FLOW credit handling | ✅ | Handles null delivery-count |
| channel-max | ⚠️ | Default 65535 crashes Qpid Broker-J |
| Address resolution | ⚠️ | No broker-specific addressing support |
| Auto-accept transfers | ⚠️ | Non-standard — should allow rejection |
| Error handling | ⚠️ | Limited error condition support |
| Transaction support | ❌ | Not implemented |
| Exactly-once delivery | ❌ | Not implemented |

---

## 12. Action Items

1. **Reduce channel-max default** from 65535 to 32767 for Qpid Broker-J compatibility
2. **Add broker-specific address formatting** for RabbitMQ (`/queues/:queue` v2 format)
3. **Fix settle mode defaults** — use unsettled(0) as standard, mixed(2) only when broker offers it
4. **Handle server-sent OPEN first** — support IBM MQ / brokers that initiate OPEN
5. **Remove auto-accept** — implement proper disposition handling
6. **Run Qpid Interop Test** against legoflow's AmqpContainer for authoritative compliance check
