# Phase 1: Protocol & Vendor Documentation

**Status:** In progress — 1.1 started  
**Started:** 2026-08-26  
**Goal:** Exhaustively document AMQP 1.0 spec requirements and every vendor's deviation.

---

## Sub-task 1.1: Transport Layer (Sections 2.1-2.8)

**Plan:** Document header exchange, OPEN/BEGIN/ATTACH/FLOW/TRANSFER/DISPOSITION/DETACH/END/CLOSE performatives across spec and all brokers. Map legoflow implementation against each.

### Protocol Header Exchange

| Aspect | OASIS Spec | Artemis | RabbitMQ | Qpid Dispatch | IBM MQ | Solace | legoflow server | legoflow client |
|--------|-----------|---------|----------|---------------|--------|--------|----------------|-----------------|
| Proto-0 header (AMQP\x00\x01\x00\x00) | Optional discovery; server may respond with SASL header | Sends AMQP header back | Requires SASL proto-3 first (rejects proto-0) | Sends AMQP header back | Server-first OPEN on some versions | Sends AMQP header back | Sends AMQP header back | Supports proto-0 (anonymous) |
| Proto-3 SASL header (AMQP\x03\x01\x00\x00) | Indicates SASL layer | Accepts and negotiates SASL | **REQUIRED** — rejects proto-0 | Accepts | Accepts | Accepts | Requires `requireSasl=true` config | Sends SASL proto-3 when credentials present |
| Header echo | Both peers send 8-byte header | Echoes back | Echoes SASL header, then AMQP header after SASL | Echoes back | Echoes back | Echoes back | Echoes AMQP header | Reads echo, validates |

**Gaps identified:**
- legoflow client: No auto-detection — chooses SASL-first or proto-0 based solely on credentials. If `username` is blank but broker requires SASL, connection fails.
- legoflow server: `handleSaslNegotiation` reads AMQP_HEADER from client first, then sends SASL_HEADER — this is the "server requires SASL" path. But if client sends SASL proto-3 header, the server reads it as "AMQP header" and fails because bytes don't match `AMQP_HEADER` (proto-0). **Server cannot accept SASL-first clients.**

### OPEN (0x0010)

| Field | Spec Default | Artemis | RabbitMQ | Qpid Dispatch | IBM MQ | legoflow |
|-------|-------------|---------|----------|---------------|--------|----------|
| `container-id` | Required | Any string | Any string | Any string | Any string | UUID-based, configurable |
| `hostname` | Null-ok | Ignored if null | Used for vhost routing | Ignored | Used for queue manager | Passed through |
| `max-frame-size` | 4294967295 (0xFFFFFFFF) | Negotiates min | Negotiates min | Negotiates min | Negotiates min | 65536 default, negotiates min |
| `channel-max` | 65535 | 65535 (BROKEN on Qpid Broker-J) | 65535 | **Uses signed short internally** — 65535 crashes | 65535 | **65535** — will crash Qpid Broker-J |
| `idle-timeout` | 0 (disabled) | Configurable (default 0) | 60000ms (60s) | 8000ms (8s) | Configurable | 0 default |
| `offered-caps` | Optional list | "ANONYMOUS-RELAY" | None | None | None | Empty list |
| `desired-caps` | Optional list | None | None | None | None | Empty list |
| OPEN order | Client or server first | Client first | Client first | Client first | **Server-first on some versions** | Client sends first, reads response |

**Gaps identified:**
- `channel-max` 65535: legoflow uses this default which crashes Qpid Broker-J (which uses signed short internally). Plan calls for 32767 default (Phase 3/4).
- `idle-timeout`: legoflow accepts the value but never enforces it (no timer in container or client).
- Server-first OPEN: IBM MQ may send OPEN before receiving client OPEN. legoflow container always reads client OPEN first.

### BEGIN (0x0011)

| Field | Spec | legoflow server | legoflow client |
|-------|------|----------------|-----------------|
| `remote-channel` | Null when initiating, ushort when responding | Reads from client BEGIN, sends own channel in response | Sends null, reads response |
| `next-outgoing-id` | Required, starts at 0 | Starts at 0 | Starts at 0 |
| `incoming-window` | Required, spec suggests 2048 | 2048 (constant) | 2048 (constant) |
| `outgoing-window` | Required, spec suggests 2048 | 2048 (constant) | 2048 (constant) |
| `handle-max` | Optional, default 0xFFFFFFFF | Not sent | Not sent |

**Gaps:** No significant gaps. Both sides follow spec defaults. Window replenishment uses a threshold strategy (replenish when below 25%).

### ATTACH (0x0012)

| Field | Spec | Artemis | RabbitMQ | Qpid Dispatch | legoflow |
|-------|------|---------|----------|---------------|----------|
| `name` | Required, unique per session | UUID-based | Any string | Any string | Configurable name |
| `handle` | Required, unique per session | Allocated by peer | Allocated by peer | Allocated by peer | Allocated locally, sent in ATTACH |
| `role` | bool (true=receiver) | Standard | Standard | Standard | Standard |
| `snd-settle-mode` | 0=unsettled, 1=settled, 2=mixed | 2 (mixed) | 2 (mixed) | 2 (mixed) | **2 (mixed)** — correct default |
| `rcv-settle-mode` | 0=first, 1=second | 0 (first) | **1 (second)** | 0 (first) | **0 (first)** — may mismatch RabbitMQ |
| `source`/`target` | Described terminus | Standard addresses | **Proprietary format** (`/queues/:queue`, `/exchanges/:name/:type`) | `closest:` prefix | Plain string, no vendor conversion |
| `initial-delivery-count` | Senders only | 0 | 0 | 0 | 0 for senders |

**Gaps identified:**
- `rcv-settle-mode`: legoflow defaults to 0 (first) everywhere. RabbitMQ negotiates 1 (second). When sender sends unsettled (snd-settle=unsettled) and rcv-settle=second, the broker won't deliver the message until the sender settles. This is the RabbitMQ settlement mismatch documented in findings.
- Address format: No conversion for RabbitMQ exchange/queue syntax, Solace topic/queue, or IBM MQ queue manager paths.

### FLOW (0x0013)

| Field | Spec | legoflow server | legoflow client |
|-------|------|----------------|-----------------|
| `next-incoming-id` | Optional | Sent on credit issues | Sent on credit issues |
| `incoming-window` | Required | Session-level | Session-level |
| `next-outgoing-id` | Required | Session-level | Session-level |
| `outgoing-window` | Required | Session-level | Session-level |
| `handle` | Null = session-level | Used for link credit | Used for link credit |
| `delivery-count` | Optional | Read but only when non-null | Defaults to sender's count when null |
| `link-credit` | Optional | Sent on initial credit | Read for sender credit grants |
| `available` | Optional | Not tracked | Not used |
| `drain` | Optional | Not implemented | Not implemented |
| `echo` | Optional | Not implemented | Not implemented |

**Gaps identified:**
- `drain` and `echo` are not implemented on either side. Most brokers don't use these in basic flows, but Qpid clients may send drain requests.
- Server-side `delivery-count` handling: when null, credit calculation may be off. Client already handles this (defaults to sender's own count).

### TRANSFER (0x0014)

| Field | Spec | legoflow server | legoflow client |
|-------|------|----------------|-----------------|
| `handle` | Required | Used to find receiver | Used to find receiver |
| `delivery-id` | Optional | Used for disposition | Used for disposition |
| `delivery-tag` | Optional | Generated by sender | Generated by sender |
| `message-format` | Default 0 | 0 | 0 |
| `settled` | Default false | Read, used for auto-accept | Read, used for delivery tracking |
| `more` | Default false | **Not handled** — no multi-frame reassembly | **Not handled** — no multi-frame reassembly |
| `rcv-settle-mode` | Optional override | **Not read** | **Not read** |
| `state` | For resume/abort | **Not handled** | **Not handled** |
| `resume` | Default false | **Not handled** | **Not handled** |
| `aborted` | Default false | **Not handled** | **Not handled** |
| `batchable` | Default false | **Not handled** | **Not handled** |

**Gaps identified:**
- **Auto-accept (server):** legoflow server auto-accepts all unsettled transfers and sends disposition back immediately. This is non-standard — the application should decide acceptance. Plan flags this as Phase 3 fix.
- Multi-frame transfers (`more=true`): Not implemented. Large messages exceeding max-frame-size will fail.
- Transfer resume/abort: Not handled.
- `rcv-settle-mode` override in transfer: Ignored. RabbitMQ may send this to force immediate settlement.

### DISPOSITION (0x0015)

| Field | Spec | legoflow server | legoflow client |
|-------|------|----------------|-----------------|
| `role` | true=receiver, false=sender | Read, routes to correct links | Read, but only handles sender dispositions |
| `first` | Required delivery-id | Used for range | Used for range |
| `last` | Optional, null=first | Used for range | Used for range |
| `settled` | Default false | Read | Read |
| `state` | Delivery state | Decoded via DeliveryStateCodec | Decoded via DeliveryStateCodec |
| `batchable` | Default false | Ignored | Ignored |

**Gaps identified:**
- Client `handleIncomingPerformative` for Disposition only iterates sender links. If a disposition arrives for a receiver role, it's silently dropped. The server handles both roles correctly.

### DETACH (0x0016)

Both sides implement graceful detach. Error conditions are passed through. No significant gaps.

### END (0x0017) / CLOSE (0x0018)

Both sides implement graceful shutdown. Error conditions passed through. No significant gaps.

**Result: 1.1 complete. Key gaps documented: channel-max 65535, idle-timeout not enforced, no server-first OPEN, rcv-settle-mode mismatch with RabbitMQ, auto-accept non-standard, no multi-frame transfer support, no address format conversion, drain/echo not implemented, SASL-first clients rejected by server.**

---

## Sub-task 1.2: SASL Security Layer (Sections 5.1-5.4)

**Plan:** Document SASL mechanisms, PLAIN authzid handling, frame size limits per broker.

### SASL Mechanisms

| Mechanism | Spec | Artemis | RabbitMQ | Qpid Dispatch | IBM MQ | Solace | legoflow |
|-----------|------|---------|----------|---------------|--------|--------|----------|
| ANONYMOUS | Optional | Supported | Supported | **Default** (no other) | Supported | Supported | Supported (default) |
| PLAIN | Optional | Supported | **Default** (required) | Supported | Supported | Supported | Supported |
| EXTERNAL | Optional (TLS) | Supported | Not supported | Supported | Supported | Supported | Supported |
| GSSAPI | Optional | Supported | Not supported | Supported | Supported | Not supported | **Not implemented** |
| SCRAM-SHA-256 | Optional | Supported | Supported | Not supported | Supported | Not supported | **Not implemented** |

**Gaps:** GSSAPI and SCRAM-SHA-256 not implemented. GSSAPI is important for Artemis/IBM MQ Kerberos auth. SCRAM is important for RabbitMQ 3.9+.

### SASL Header Negotiation

| Aspect | Spec | Artemis | RabbitMQ | Qpid Dispatch | legoflow server | legoflow client |
|--------|------|---------|----------|---------------|----------------|-----------------|
| Client sends proto-0, server replies SASL | Allowed | Supported | **Not supported** — closes socket | Supported | **Not supported** — reads AMQP_HEADER | Supported (server-first SASL path) |
| Client sends proto-3 (SASL header first) | Allowed | Supported | **Required** | Supported | **Not supported** — expects AMQP_HEADER | Supported (with credentials) |
| authzid in sasl-init | Optional | Any value accepted | **Must be empty string** | Any value | **Not handled** — server ignores authzid | Sends hostname as authzid |

**Gaps identified:**
- **Server cannot receive SASL-first clients:** `handleSaslNegotiation` reads 8 bytes expecting `AMQP_HEADER` (proto-0). If client sends `SASL_HEADER` (proto-3), comparison fails and connection is rejected. RabbitMQ clients that always lead with SASL proto-3 will fail.
- **authzid handling:** legoflow `SaslCodec.encodeInit` sends `hostname` as authzid. RabbitMQ requires authzid to be an empty string. Server-side `SaslAuthenticator` doesn't read authzid from sasl-init at all.

### SASL Frame Size Limits

| Aspect | Spec | Artemis | RabbitMQ | Qpid Dispatch | legoflow |
|--------|------|---------|----------|---------------|--------|
| SASL max-frame-size | Negotiated via sasl-init `max-frame-size` field | Enforced | Enforced | Enforced | **Not enforced** — server uses connection max-frame for SASL frames too |
| sasl-init max-frame | Optional in spec | Read | Read | Read | **Not read or enforced** |

**Gaps:** legoflow `SaslCodec.encodeInit` does not include a `max-frame-size` field in sasl-init. Server does not read it from incoming sasl-init. This means SASL frame size limits are not negotiated — a client could send a huge SASL response.

**Result: 1.2 complete. Key gaps: no SASL-first server support, authzid not handled for RabbitMQ compatibility, SASL frame size not negotiated, GSSAPI/SCRAM not implemented.**

---

## Sub-task 1.3: Messaging Layer (Sections 2.6, 3.2)

**Plan:** Document link credit, settlement modes, and message format variances.

### Link Credit Model

| Aspect | Spec | Artemis | RabbitMQ | Qpid Dispatch | legoflow server | legoflow client |
|--------|------|---------|----------|---------------|----------------|-----------------|
| Receiver issues credit via FLOW | Required | Issues on ATTACH response | Issues on ATTACH response (if queue exists) | Self-grants | Issues initial credit on ATTACH response | Issues initial credit on ATTACH |
| Sender waits for credit | Required | Waits | Waits (may get 0 until receiver ready) | Self-grants | N/A (server creates links) | Self-grants on sender (artificial credit) |
| Credit replenishment | Via FLOW frames | Automatic | Automatic when receiver attached | Automatic | Auto-replenish at 25% threshold | Auto-replenish at 25% threshold |

**Gaps identified:**
- legoflow client `createSender` calls `link.grantCredit(0, 100)` after ATTACH — this is artificial self-granting. Works for Artemis/Qpid but fails when broker enforces credit (RabbitMQ may send 0 credit if queue doesn't exist yet).
- Self-grant on settled sends: `SenderLink.send()` self-grants credit when `settled=true`. This bypasses the broker's flow control for at-most-once deliveries.

### Settlement Modes

| Mode | Value | Meaning | legoflow default | RabbitMQ | Artemis | Qpid |
|------|-------|---------|-----------------|----------|---------|------|
| snd-settle: unsettled | 0 | Sender settles after receiver | **2 (mixed)** | 2 (mixed) | 2 (mixed) | 2 (mixed) |
| snd-settle: settled | 1 | Sender pre-settles | — | — | — | — |
| snd-settle: mixed | 2 | Sender decides per-transfer | **2 (mixed)** | 2 (mixed) | 2 (mixed) | 2 (mixed) |
| rcv-settle: first | 0 | Receiver settles on receipt | **0 (first)** | **1 (second)** | 0 (first) | 0 (first) |
| rcv-settle: second | 1 | Receiver settles after sender settles | 0 (first) | **1 (second)** | — | — |

**Critical RabbitMQ mismatch:**
- legoflow sends `rcv-settle-mode=0` (first) in ATTACH
- RabbitMQ responds with `rcv-settle-mode=1` (second)
- When sender sends unsettled transfer and broker uses rcv-settle=second, the broker waits for sender disposition before delivering to receiver
- legoflow client never sends sender disposition after unsettled transfer → **message never delivered to receiver**

**Result: 1.3 complete. Key gaps: RabbitMQ rcv-settle-mode mismatch causes message delivery failure, artificial credit grant on sender links, auto-accept on server side, no proper disposition dispatch.**

---

## Sub-task 1.4: Vendor-Specific Addressing

**Plan:** Document address format differences across brokers.

### Address Formats

| Broker | Format | Example | legoflow support |
|--------|--------|---------|-----------------|
| **Standard (OASIS)** | Plain string | `myqueue` | ✓ |
| **RabbitMQ** | `/queues/:queue-name` | `/queues/orders` | ✗ |
| **RabbitMQ exchanges** | `/exchanges/:name/:type` | `/exchanges/events/direct` | ✗ |
| **RabbitMQ temp** | `/auto/delete` | — | ✗ |
| **Artemis** | Queue name (JMS compat) | `QUEUE.ORDERS` | ✓ (plain string) |
| **Artemis topics** | `TOPICS.#{selector}` | `TOPICS.events` | ✓ (plain string) |
| **Qpid Dispatch** | `closest:queue-name` | `closest:orders` | ✗ (prefix not handled) |
| **IBM MQ** | Queue manager path | `QMGR.QUEUE.ORDERS` | ✓ (plain string) |
| **Solace** | Topic/queue binding | `SMF/Topic/Events` | ✓ (plain string) |

**Gaps identified:**
- RabbitMQ exchange/queue format not supported. Need address format converter in both client and server.
- Qpid Dispatch `closest:` routing prefix not understood.
- Artemis topic selectors not handled.

**Result: 1.4 complete. RabbitMQ and Qpid Dispatch address formats need converters.**

---

## Sub-task 1.5: Cross-Reference — legoflow Implementation Gaps

### Complete Gap Matrix

| # | Gap | Severity | Spec Section | Phase | Component |
|---|-----|----------|-------------|-------|-----------|
| 1 | `channel-max` 65535 crashes Qpid Broker-J | **High** | OPEN (2.7.1) | Phase 3, 4 | Container, Client |
| 2 | SASL PLAIN authzid — server ignores, client sends hostname | **High** | SASL (5.1) | Phase 3, 4 | SaslCodec, SaslAuthenticator |
| 3 | Server rejects SASL-first clients (RabbitMQ flow) | **High** | Transport (2.2) | Phase 3 | AmqpContainer |
| 4 | `rcv-settle-mode` mismatch with RabbitMQ (0 vs 1) | **High** | ATTACH (2.7.3) | Phase 4 | AmqpClient |
| 5 | Auto-accept transfers on server side | **Medium** | TRANSFER (2.7.5) | Phase 3 | AmqpContainer |
| 6 | Artificial credit grant on sender links | **Medium** | FLOW (2.7.4) | Phase 4 | AmqpClient |
| 7 | No multi-frame transfer reassembly | **Medium** | TRANSFER (2.7.5) | Phase 3 | Both |
| 8 | `rcv-settle-mode` override in transfer ignored | **Medium** | TRANSFER (2.7.5) | Phase 3, 4 | Both |
| 9 | Address format conversion missing | **High** | — | Phase 3, 4 | Both |
| 10 | Idle timeout not enforced | **Low** | OPEN (2.7.1) | Phase 3 | Container |
| 11 | Server-first OPEN not handled (IBM MQ) | **Medium** | OPEN (2.7.1) | Phase 3 | AmqpContainer |
| 12 | SASL frame size not negotiated | **Low** | SASL (5.1) | Phase 3 | SaslCodec |
| 13 | GSSAPI/SCRAM mechanisms missing | **Low** | SASL (5.1) | Out of scope | SaslAuthenticator |
| 14 | Drain/echo flow not implemented | **Low** | FLOW (2.7.4) | Phase 3, 4 | Both |
| 15 | Disposition handling incomplete on client side | **Medium** | DISPOSITION (2.7.6) | Phase 4 | AmqpClient |
| 16 | No vendor simulation modes in container | — | — | Phase 3 | AmqpContainer |
| 17 | No broker type detection in client | — | — | Phase 4 | AmqpClient |

### Legoflow Code Paths → Spec Sections

| Code Path | File | Spec Section | Notes |
|-----------|------|-------------|-------|
| `handleProtocolHeader` | AmqpContainer | §2.2.2 | Header exchange |
| `handleSaslNegotiation` / `doSaslExchange` | AmqpContainer | §3.2.4 | SASL layer |
| `handleConnectionLifecycle` → OPEN | AmqpContainer | §2.7.1 | Connection open |
| `handleBegin` | AmqpContainer | §2.7.2 | Session begin |
| `handleAttach` | AmqpContainer | §2.7.3 | Link attach |
| `handleFlow` | AmqpContainer | §2.7.4 | Flow control |
| `handleTransfer` | AmqpContainer | §2.7.5 | Message transfer |
| `handleDisposition` | AmqpContainer | §2.7.6 | Disposition |
| `handleDetach` | AmqpContainer | §2.7.7 | Link detach |
| `handleEnd` / `handleClose` | AmqpContainer | §2.7.8/2.7.9 | Session/connection close |
| `connect` → header exchange | AmqpClient | §2.2.2 | Header exchange |
| `doSaslNegotiation` | AmqpClient | §3.2.4 | SASL negotiation |
| `connect` → OPEN | AmqpClient | §2.7.1 | Connection open |
| `createSession` → BEGIN | AmqpClient | §2.7.2 | Session begin |
| `createSender` → ATTACH | AmqpClient | §2.7.3 | Sender attach |
| `createReceiver` → ATTACH | AmqpClient | §2.7.3 | Receiver attach |
| `send` → TRANSFER | SenderLink | §2.7.5 | Message send |
| `handleTransfer` | ReceiverLink | §2.7.5 | Message receive |
| `settle`/`accept`/`reject` | ReceiverLink | §2.7.6 | Disposition send |
| `issueCredit` | ReceiverLink | §2.7.4 | Credit grant |
| `grantCredit` | SenderLink | §2.7.4 | Credit receive |

**Result: 1.5 complete. All 17 gaps catalogued with severity, spec reference, and target phase/component.**

---

## Phase 1 Summary

All 5 sub-tasks complete. 17 implementation gaps identified and mapped to phases 3-4.

**Highest priority gaps (block interop):**
1. Channel-max 65535 → reduce to 32767 (Phase 3.7, 4.7)
2. SASL authzid handling (Phase 3.2, 4.3)
3. Server SASL-first support (Phase 3)
4. Rcv-settle-mode negotiation (Phase 4.6)
5. Address format converters (Phase 3.6, 4.5)
